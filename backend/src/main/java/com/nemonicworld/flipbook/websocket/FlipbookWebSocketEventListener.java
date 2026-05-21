package com.nemonicworld.flipbook.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger;
import com.nemonicworld.flipbook.service.FlipbookRoomService;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import static com.nemonicworld.flipbook.logging.FlipbookRoomEventLogger.metadata;

/**
 * 플립북 STOMP 세션 종료 이벤트를 Redis 연결 상태에 반영합니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookWebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlipbookWebSocketEventListener.class);

    private final FlipbookRoomService flipbookRoomService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    /**
     * 최신 활성 세션의 disconnect만 Redis에 connected=false로 반영합니다.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Optional<ActiveWebSocketSession> activeSession = webSocketSessionRegistry.findBySessionId(sessionId);

        if (activeSession.isEmpty()) {
            return;
        }

        ActiveWebSocketSession session = activeSession.get();
        if (!WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK.equals(session.connectionType())) {
            return;
        }

        String roomCode = session.connectionKey();

        if (!webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, roomCode,
            session.userUuid(), sessionId)) {
            webSocketSessionRegistry.removeStaleSession(sessionId);
            return;
        }

        try {
            FlipbookRoomStateResponse roomStateResponse = flipbookRoomService.disconnectRoom(session.userUuid(),
                roomCode);
            flipbookRoomEventPublisher.publishParticipantDisconnected(roomStateResponse, session.userUuid());
            FlipbookRoomEventLogger.websocketBusiness("flipbook_ws_disconnected", metadata("room_id", roomCode, "uuid",
                session.userUuid(), "session_id", sessionId, "room_status", roomStateResponse.status()));
        } catch (RuntimeException e) {
            log.warn("Failed to update flipbook websocket disconnect state. roomCode={}, sessionId={}", roomCode,
                sessionId, e);
            FlipbookRoomEventLogger.websocketWarn("flipbook_ws_disconnect_update_failed",
                "failed to update flipbook websocket disconnect state",
                metadata("room_id", roomCode, "uuid", session.userUuid(), "session_id", sessionId), e);
        } finally {
            webSocketSessionRegistry.removeIfCurrent(sessionId);
        }
    }
}
