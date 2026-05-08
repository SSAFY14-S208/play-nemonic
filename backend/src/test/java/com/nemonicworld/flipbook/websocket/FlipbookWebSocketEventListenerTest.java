package com.nemonicworld.flipbook.websocket;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomParticipantResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 플립북 STOMP disconnect 이벤트가 최신 세션에 대해서만 Redis 연결 해제를 수행하는지 검증합니다.
 */
class FlipbookWebSocketEventListenerTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SESSION_ID = "session-1";

    private final FlipbookRoomService flipbookRoomService = mock(FlipbookRoomService.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher = mock(FlipbookRoomEventPublisher.class);
    private final FlipbookWebSocketEventListener listener = new FlipbookWebSocketEventListener(flipbookRoomService,
        webSocketSessionRegistry, flipbookRoomEventPublisher);

    /**
     * 최신 활성 세션 disconnect는 Redis connected=false 갱신 후 방 전체 이벤트를 발행합니다.
     */
    @Test
    void handleSessionDisconnectUpdatesRedisWhenSessionIsCurrent() {
        FlipbookRoomStateResponse roomStateResponse = roomStateResponse();
        ActiveWebSocketSession session = new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
            ROOM_CODE, USER_UUID, SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(session));
        given(webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, ROOM_CODE,
            USER_UUID, SESSION_ID)).willReturn(true);
        given(flipbookRoomService.disconnectRoom(USER_UUID, ROOM_CODE)).willReturn(roomStateResponse);

        listener.handleSessionDisconnect(disconnectEvent());

        verify(flipbookRoomService).disconnectRoom(USER_UUID, ROOM_CODE);
        verify(flipbookRoomEventPublisher).publishParticipantDisconnected(roomStateResponse);
        verify(webSocketSessionRegistry).removeIfCurrent(SESSION_ID);
    }

    /**
     * 중복 접속으로 교체된 예전 세션의 disconnect는 Redis 상태를 false로 덮어쓰지 않습니다.
     */
    @Test
    void handleSessionDisconnectSkipsRedisUpdateWhenSessionIsStale() {
        ActiveWebSocketSession session = new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
            ROOM_CODE, USER_UUID, SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(session));
        given(webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, ROOM_CODE,
            USER_UUID, SESSION_ID)).willReturn(false);

        listener.handleSessionDisconnect(disconnectEvent());

        verify(flipbookRoomService, never()).disconnectRoom(USER_UUID, ROOM_CODE);
        verify(flipbookRoomEventPublisher, never()).publishParticipantDisconnected(org.mockito.ArgumentMatchers.any());
        verify(webSocketSessionRegistry).removeStaleSession(SESSION_ID);
    }

    private SessionDisconnectEvent disconnectEvent() {
        return new SessionDisconnectEvent(this, MessageBuilder.withPayload(new byte[0]).build(), SESSION_ID,
            CloseStatus.NORMAL);
    }

    private FlipbookRoomStateResponse roomStateResponse() {
        FlipbookRoomParticipantResponse participant = new FlipbookRoomParticipantResponse(USER_UUID, "망고", true, 0,
            false);
        LocalDateTime now = LocalDateTime.now();

        return new FlipbookRoomStateResponse(ROOM_CODE, FlipbookRoomStatus.WAITING, USER_UUID, 60, 2, 6, 1,
            List.of(participant), null, now, now);
    }
}
