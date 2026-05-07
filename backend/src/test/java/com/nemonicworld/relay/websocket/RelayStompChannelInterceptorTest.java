package com.nemonicworld.relay.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.relay.dto.response.RelayRoomParticipantResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.RelayRoomService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * STOMP CONNECT frame 검증과 중복 세션 교체 흐름을 검증합니다.
 */
class RelayStompChannelInterceptorTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String NEW_SESSION_ID = "session-2";
    private static final String OLD_SESSION_ID = "session-1";

    private final RelayRoomService relayRoomService = mock(RelayRoomService.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final RelayRoomEventPublisher relayRoomEventPublisher = mock(RelayRoomEventPublisher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RelayRoomEventPublisher> relayRoomEventPublisherProvider = mock(ObjectProvider.class);
    private RelayStompChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        given(relayRoomEventPublisherProvider.getObject()).willReturn(relayRoomEventPublisher);
        interceptor = new RelayStompChannelInterceptor(relayRoomService, webSocketSessionRegistry,
            relayRoomEventPublisherProvider);
    }

    /**
     * CONNECT header가 유효하면 Redis 연결 상태를 갱신하고 세션 메타데이터를 등록합니다.
     */
    @Test
    void preSendRegistersRelaySessionAndClosesDuplicateSessionOnConnect() {
        RelayRoomStateResponse roomStateResponse = roomStateResponse();
        Message<byte[]> message = connectMessage();
        given(relayRoomService.connectRoom(USER_UUID, ROOM_CODE)).willReturn(roomStateResponse);
        given(webSocketSessionRegistry.register(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, ROOM_CODE, USER_UUID,
            NEW_SESSION_ID))
            .willReturn(Optional.of(new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY,
                ROOM_CODE, USER_UUID, OLD_SESSION_ID)));

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(NEW_SESSION_ID);
        assertThat(resultAccessor.getSessionAttributes())
            .containsEntry(WebSocketSessionAttributes.CONNECTION_KEY, ROOM_CODE)
            .containsEntry(WebSocketSessionAttributes.USER_UUID, USER_UUID);
        verify(relayRoomEventPublisher).publishDuplicateSessionClosed(OLD_SESSION_ID, ROOM_CODE);
        verify(webSocketSessionRegistry).closeWebSocketSession(OLD_SESSION_ID);
        verify(webSocketSessionRegistry).removeStaleSession(OLD_SESSION_ID);
        verify(relayRoomEventPublisher).publishParticipantConnected(roomStateResponse, USER_UUID);
    }

    /**
     * 연결 검증 실패는 내부 상세를 그대로 노출하지 않는 STOMP 연결 거부 예외로 변환합니다.
     */
    @Test
    void preSendRejectsConnectWhenRoomServiceRejectsConnection() {
        Message<byte[]> message = connectMessage();
        given(relayRoomService.connectRoom(USER_UUID, ROOM_CODE)).willThrow(new ConflictException("이미 종료된 방입니다."));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
            .isInstanceOf(MessageDeliveryException.class).hasMessageContaining("릴레이 웹소켓 연결을 허용할 수 없습니다.");

        verify(webSocketSessionRegistry).removeStaleSession(NEW_SESSION_ID);
    }

    private Message<byte[]> connectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(NEW_SESSION_ID);
        HashMap<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionAttributes.CONNECTION_TYPE,
            WebSocketSessionAttributes.CONNECTION_TYPE_RELAY);
        accessor.setSessionAttributes(sessionAttributes);
        accessor.addNativeHeader("roomCode", ROOM_CODE);
        accessor.addNativeHeader(AnonymousUserHeaders.ANONYMOUS_USER_UUID, USER_UUID);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private RelayRoomStateResponse roomStateResponse() {
        RelayRoomParticipantResponse participant = new RelayRoomParticipantResponse(USER_UUID, "망고", true, 0, true);
        LocalDateTime now = LocalDateTime.now();

        return new RelayRoomStateResponse(ROOM_CODE, RelayRoomStatus.WAITING, USER_UUID, 60, 2, 6, 1, null,
            List.of(participant), null, now, now);
    }
}
