package com.nemonicworld.relay.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

/**
 * 릴레이 WebSocket ping 메시지 처리 정책을 검증합니다.
 */
class RelayRoomWebSocketControllerTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String SESSION_ID = "session-1";

    private final RelayRoomEventPublisher relayRoomEventPublisher = mock(RelayRoomEventPublisher.class);
    private final RelayRoomWebSocketController controller = new RelayRoomWebSocketController(relayRoomEventPublisher);

    /**
     * 연결된 세션이 ping을 보내면 같은 세션의 개인 큐로 PONG 이벤트를 발행합니다.
     */
    @Test
    void pingPublishesPongToCurrentSessionUserQueue() {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        Principal principal = () -> SESSION_ID;
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionAttributes.CONNECTION_KEY, ROOM_CODE);
        headerAccessor.setUser(principal);
        headerAccessor.setSessionAttributes(sessionAttributes);

        controller.ping(ROOM_CODE, headerAccessor);

        verify(relayRoomEventPublisher).publishPong(SESSION_ID, ROOM_CODE);
    }
}
