package com.nemonicworld.relay.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.service.RelayRoomService;
import com.nemonicworld.relay.websocket.RelayWebSocketSessionRegistry.RelayWebSocketSession;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 릴레이 STOMP 세션 종료 이벤트를 Redis 연결 상태에 반영합니다.
 */
@Component
public class RelayWebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketEventListener.class);

    private final RelayRoomService relayRoomService;
    private final RelayWebSocketSessionRegistry relayWebSocketSessionRegistry;
    private final RelayRoomEventPublisher relayRoomEventPublisher;

    public RelayWebSocketEventListener(RelayRoomService relayRoomService,
        RelayWebSocketSessionRegistry relayWebSocketSessionRegistry, RelayRoomEventPublisher relayRoomEventPublisher) {
        this.relayRoomService = relayRoomService;
        this.relayWebSocketSessionRegistry = relayWebSocketSessionRegistry;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
    }

    /**
     * 최신 활성 세션의 disconnect만 Redis에 connected=false로 반영합니다.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Optional<RelayWebSocketSession> relaySession = relayWebSocketSessionRegistry.findBySessionId(sessionId);

        if (relaySession.isEmpty()) {
            return;
        }

        RelayWebSocketSession session = relaySession.get();

        if (!relayWebSocketSessionRegistry.isCurrentSession(session.roomCode(), session.userUuid(), sessionId)) {
            relayWebSocketSessionRegistry.removeStaleSession(sessionId);
            return;
        }

        try {
            RelayRoomStateResponse roomStateResponse = relayRoomService.disconnectRoom(session.userUuid(),
                session.roomCode());
            relayRoomEventPublisher.publishParticipantDisconnected(roomStateResponse);
        } catch (RuntimeException e) {
            log.warn("Failed to update relay websocket disconnect state. roomCode={}, sessionId={}", session.roomCode(),
                sessionId, e);
        } finally {
            relayWebSocketSessionRegistry.removeIfCurrent(sessionId);
        }
    }
}
