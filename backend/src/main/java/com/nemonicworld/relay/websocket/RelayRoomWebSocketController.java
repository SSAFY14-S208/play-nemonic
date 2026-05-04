package com.nemonicworld.relay.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.util.Optional;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * 릴레이 WebSocket 클라이언트 send 메시지를 처리합니다.
 */
@Controller
public class RelayRoomWebSocketController {

    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public RelayRoomWebSocketController(RelayRoomEventPublisher relayRoomEventPublisher,
        WebSocketSessionRegistry webSocketSessionRegistry) {
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    /**
     * 클라이언트 heartbeat/ping 요청에 개인 큐 PONG 이벤트로 응답합니다.
     */
    @MessageMapping("/relay/rooms/{roomCode}/ping")
    public void ping(@DestinationVariable("roomCode") String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Optional<ActiveWebSocketSession> activeSession = webSocketSessionRegistry.findBySessionId(sessionId);

        if (activeSession.isEmpty() || !roomCode.equals(activeSession.get().connectionKey())) {
            return;
        }

        relayRoomEventPublisher.publishPong(sessionId, roomCode);
    }
}
