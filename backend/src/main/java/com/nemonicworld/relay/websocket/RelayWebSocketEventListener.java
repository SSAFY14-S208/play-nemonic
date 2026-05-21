package com.nemonicworld.relay.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.service.RelayRoomService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 릴레이 STOMP 세션 종료 이벤트를 Redis 연결 상태에 반영합니다.
 */
@Component
public class RelayWebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketEventListener.class);

    private final RelayRoomService relayRoomService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final RelayRoomEventPublisher relayRoomEventPublisher;

    public RelayWebSocketEventListener(RelayRoomService relayRoomService,
        WebSocketSessionRegistry webSocketSessionRegistry, RelayRoomEventPublisher relayRoomEventPublisher) {
        this.relayRoomService = relayRoomService;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
    }

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
        if (!WebSocketSessionAttributes.CONNECTION_TYPE_RELAY.equals(session.connectionType())) {
            return;
        }

        String roomCode = session.connectionKey();

        if (!webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, roomCode,
            session.userUuid(), sessionId)) {
            webSocketSessionRegistry.removeStaleSession(sessionId);
            return;
        }

        try {
            RelayRoomStateResponse roomStateResponse = relayRoomService.disconnectRoom(session.userUuid(), roomCode);
            relayRoomEventPublisher.publishParticipantDisconnected(roomStateResponse, session.userUuid());
            RelayRoomEventLogger.websocketBusiness("relay_ws_disconnected",
                metadata("room_id", roomCode, "uuid", session.userUuid(), "session_id", sessionId, "disconnect_reason",
                    event.getCloseStatus() == null ? null : event.getCloseStatus().toString(), "room_status",
                    roomStateResponse.status(), "current_part", roomStateResponse.currentPart()));
        } catch (RuntimeException e) {
            RelayRoomEventLogger.websocketWarn("relay_ws_disconnect_update_failed",
                "failed to update relay websocket disconnect state",
                metadata("room_id", roomCode, "uuid", session.userUuid(), "session_id", sessionId), e);
            log.warn("Failed to update relay websocket disconnect state. roomCode={}, sessionId={}", roomCode,
                sessionId, e);
        } finally {
            webSocketSessionRegistry.removeIfCurrent(sessionId);
        }
    }
}
