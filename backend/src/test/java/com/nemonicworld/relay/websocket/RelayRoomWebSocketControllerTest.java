package com.nemonicworld.relay.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

/**
 * 릴레이 WebSocket ping 메시지 처리 정책을 검증합니다.
 */
class RelayRoomWebSocketControllerTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String SESSION_ID = "session-1";

    private final RelayRoomEventPublisher relayRoomEventPublisher = mock(RelayRoomEventPublisher.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final RelayRoomWebSocketController controller = new RelayRoomWebSocketController(relayRoomEventPublisher,
        webSocketSessionRegistry);

    /**
     * 연결된 세션이 ping을 보내면 같은 세션의 개인 큐로 PONG 이벤트를 발행합니다.
     */
    @Test
    void pingPublishesPongToCurrentSessionUserQueue() {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId(SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID))
            .willReturn(Optional.of(new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY,
                ROOM_CODE, "user-uuid", SESSION_ID)));

        controller.ping(ROOM_CODE, headerAccessor);

        verify(relayRoomEventPublisher).publishPong(SESSION_ID, ROOM_CODE);
    }
}
