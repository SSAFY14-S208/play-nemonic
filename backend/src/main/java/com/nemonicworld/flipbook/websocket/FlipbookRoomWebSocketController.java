package com.nemonicworld.flipbook.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * 플립북 WebSocket 클라이언트 send 메시지를 처리합니다.
 */
@Controller
@RequiredArgsConstructor
public class FlipbookRoomWebSocketController {

    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    /**
     * 클라이언트 heartbeat/ping 요청에 개인 큐 PONG 이벤트로 응답합니다.
     */
    @MessageMapping("/flipbook/rooms/{roomCode}/ping")
    public void ping(@DestinationVariable("roomCode") String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Optional<ActiveWebSocketSession> activeSession = webSocketSessionRegistry.findBySessionId(sessionId);

        if (activeSession.isEmpty() || !isCurrentFlipbookRoomSession(roomCode, activeSession.get())) {
            return;
        }

        flipbookRoomEventPublisher.publishPong(sessionId, roomCode);
    }

    private boolean isCurrentFlipbookRoomSession(String roomCode, ActiveWebSocketSession activeSession) {
        return WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK.equals(activeSession.connectionType())
            && roomCode.equals(activeSession.connectionKey());
    }
}
