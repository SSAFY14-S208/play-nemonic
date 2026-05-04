package com.nemonicworld.relay.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.security.Principal;
import java.util.Map;
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

    public RelayRoomWebSocketController(RelayRoomEventPublisher relayRoomEventPublisher) {
        this.relayRoomEventPublisher = relayRoomEventPublisher;
    }

    /**
     * 클라이언트 heartbeat/ping 요청에 개인 큐 PONG 이벤트로 응답합니다.
     */
    @MessageMapping("/relay/rooms/{roomCode}/ping")
    public void ping(@DestinationVariable("roomCode") String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (principal == null || sessionAttributes == null
            || !roomCode.equals(sessionAttributes.get(WebSocketSessionAttributes.CONNECTION_KEY))) {
            return;
        }

        relayRoomEventPublisher.publishPong(principal.getName(), roomCode);
    }
}
