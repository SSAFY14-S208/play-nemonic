package com.nemonicworld.infinitecanvas.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 무한 캔버스 STOMP CONNECT frame 검증과 중복 세션 교체 흐름을 검증합니다.
 */
class InfiniteCanvasStompChannelInterceptorTest {

    private static final String ROOM_CODE = "AC3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String NEW_SESSION_ID = "session-2";
    private static final String OLD_SESSION_ID = "session-1";

    private final InfiniteCanvasService infiniteCanvasService = mock(InfiniteCanvasService.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher = spy(
        new InfiniteCanvasEventPublisher(messagingTemplate, webSocketSessionRegistry));
    @SuppressWarnings("unchecked")
    private final ObjectProvider<InfiniteCanvasEventPublisher> infiniteCanvasEventPublisherProvider = mock(
        ObjectProvider.class);
    private InfiniteCanvasStompChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        given(infiniteCanvasEventPublisherProvider.getObject()).willReturn(infiniteCanvasEventPublisher);
        interceptor = new InfiniteCanvasStompChannelInterceptor(infiniteCanvasService, webSocketSessionRegistry,
            infiniteCanvasEventPublisherProvider);
    }

    /**
     * CONNECT header가 유효하면 Redis 연결 상태를 갱신하고 세션 메타데이터를 등록합니다.
     */
    @Test
    void preSendRegistersInfiniteCanvasSessionAndClosesDuplicateSessionOnConnect() {
        InfiniteCanvasStateResponse stateResponse = stateResponse(true);
        Message<byte[]> message = connectMessage();
        given(infiniteCanvasService.connectCanvas(USER_UUID, ROOM_CODE)).willReturn(stateResponse);
        given(webSocketSessionRegistry.register(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, ROOM_CODE,
            USER_UUID, NEW_SESSION_ID)).willReturn(
                Optional.of(new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
                    ROOM_CODE, USER_UUID, OLD_SESSION_ID)));

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(NEW_SESSION_ID);
        assertThat(resultAccessor.getSessionAttributes())
            .containsEntry(WebSocketSessionAttributes.CONNECTION_KEY, ROOM_CODE)
            .containsEntry(WebSocketSessionAttributes.USER_UUID, USER_UUID);
        verify(infiniteCanvasEventPublisher).publishDuplicateSessionClosed(OLD_SESSION_ID, ROOM_CODE);
        verify(webSocketSessionRegistry).closeWebSocketSession(OLD_SESSION_ID);
        verify(webSocketSessionRegistry).removeStaleSession(OLD_SESSION_ID);
        verify(infiniteCanvasEventPublisher).publishParticipantConnected(stateResponse);
    }

    /**
     * 연결 검증 실패는 내부 상세를 그대로 노출하지 않는 STOMP 연결 거부 예외로 변환합니다.
     */
    @Test
    void preSendRejectsConnectWhenCanvasServiceRejectsConnection() {
        Message<byte[]> message = connectMessage();
        given(infiniteCanvasService.connectCanvas(USER_UUID, ROOM_CODE))
            .willThrow(new ConflictException("이미 종료된 캔버스입니다."));

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
            .isInstanceOf(MessageDeliveryException.class).hasMessageContaining("무한 캔버스 웹소켓 연결을 허용할 수 없습니다.");

        verify(webSocketSessionRegistry).removeStaleSession(NEW_SESSION_ID);
    }

    private Message<byte[]> connectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(NEW_SESSION_ID);
        HashMap<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionAttributes.CONNECTION_TYPE,
            WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS);
        accessor.setSessionAttributes(sessionAttributes);
        accessor.addNativeHeader("roomCode", ROOM_CODE);
        accessor.addNativeHeader(AnonymousUserHeaders.ANONYMOUS_USER_UUID, USER_UUID);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private InfiniteCanvasStateResponse stateResponse(boolean connected) {
        InfiniteCanvasParticipantResponse participant = new InfiniteCanvasParticipantResponse(USER_UUID, "망고",
            "#72DDF7", null, true, connected, LocalDateTime.now(), connected ? LocalDateTime.now() : null);
        LocalDateTime now = LocalDateTime.now();

        return new InfiniteCanvasStateResponse(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, USER_UUID, participant,
            List.of(participant), List.of(), List.of(), Map.of(), null, 6, 0L, now, now);
    }
}
