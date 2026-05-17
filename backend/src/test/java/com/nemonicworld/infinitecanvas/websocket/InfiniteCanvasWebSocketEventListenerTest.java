package com.nemonicworld.infinitecanvas.websocket;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasStatus;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 무한 캔버스 STOMP disconnect 이벤트가 최신 세션에 대해서만 Redis 연결 해제를 수행하는지 검증합니다.
 */
class InfiniteCanvasWebSocketEventListenerTest {

    private static final String ROOM_CODE = "AC3K9Q";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SESSION_ID = "session-1";

    private final InfiniteCanvasService infiniteCanvasService = mock(InfiniteCanvasService.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher = mock(InfiniteCanvasEventPublisher.class);
    private final InfiniteCanvasWebSocketEventListener listener = new InfiniteCanvasWebSocketEventListener(
        infiniteCanvasService, webSocketSessionRegistry, infiniteCanvasEventPublisher);

    /**
     * 최신 활성 세션 disconnect는 Redis connected=false 갱신 후 캔버스 전체 이벤트를 발행합니다.
     */
    @Test
    void handleSessionDisconnectUpdatesRedisWhenSessionIsCurrent() {
        InfiniteCanvasStateResponse stateResponse = stateResponse(false);
        ActiveWebSocketSession session = new ActiveWebSocketSession(
            WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, ROOM_CODE, USER_UUID, SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(session));
        given(webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
            ROOM_CODE, USER_UUID, SESSION_ID)).willReturn(true);
        given(infiniteCanvasService.disconnectCanvas(USER_UUID, ROOM_CODE)).willReturn(stateResponse);

        listener.handleSessionDisconnect(disconnectEvent());

        verify(infiniteCanvasService).disconnectCanvas(USER_UUID, ROOM_CODE);
        verify(infiniteCanvasEventPublisher).publishParticipantDisconnected(stateResponse);
        verify(webSocketSessionRegistry).removeIfCurrent(SESSION_ID);
    }

    /**
     * 중복 접속으로 교체된 예전 세션의 disconnect는 Redis 상태를 false로 덮어쓰지 않습니다.
     */
    @Test
    void handleSessionDisconnectSkipsRedisUpdateWhenSessionIsStale() {
        ActiveWebSocketSession session = new ActiveWebSocketSession(
            WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, ROOM_CODE, USER_UUID, SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(session));
        given(webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
            ROOM_CODE, USER_UUID, SESSION_ID)).willReturn(false);

        listener.handleSessionDisconnect(disconnectEvent());

        verify(infiniteCanvasService, never()).disconnectCanvas(USER_UUID, ROOM_CODE);
        verify(infiniteCanvasEventPublisher, never())
            .publishParticipantDisconnected(org.mockito.ArgumentMatchers.any());
        verify(webSocketSessionRegistry).removeStaleSession(SESSION_ID);
    }

    private SessionDisconnectEvent disconnectEvent() {
        return new SessionDisconnectEvent(this, MessageBuilder.withPayload(new byte[0]).build(), SESSION_ID,
            CloseStatus.NORMAL);
    }

    private InfiniteCanvasStateResponse stateResponse(boolean connected) {
        InfiniteCanvasParticipantResponse participant = new InfiniteCanvasParticipantResponse(USER_UUID, "망고",
            "#72DDF7", null, true, connected, LocalDateTime.now(), connected ? LocalDateTime.now() : null);
        LocalDateTime now = LocalDateTime.now();

        return new InfiniteCanvasStateResponse(ROOM_CODE, InfiniteCanvasStatus.ACTIVE, USER_UUID, participant,
            List.of(participant), List.of(), List.of(), Map.of(), null, 6, 0L, now, now);
    }
}
