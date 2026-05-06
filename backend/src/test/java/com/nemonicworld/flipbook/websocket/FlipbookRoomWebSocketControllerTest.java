package com.nemonicworld.flipbook.websocket;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

/**
 * 플립북 WebSocket ping 메시지 처리 정책을 검증합니다.
 */
class FlipbookRoomWebSocketControllerTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String SESSION_ID = "session-1";

    private final FlipbookRoomEventPublisher flipbookRoomEventPublisher = mock(FlipbookRoomEventPublisher.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final FlipbookRoomWebSocketController controller = new FlipbookRoomWebSocketController(
        flipbookRoomEventPublisher, webSocketSessionRegistry);

    /**
     * 연결된 세션이 ping을 보내면 같은 세션의 개인 큐로 PONG 이벤트를 발행합니다.
     */
    @Test
    void pingPublishesPongToCurrentSessionUserQueue() {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId(SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID))
            .willReturn(Optional.of(new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
                ROOM_CODE, "user-uuid", SESSION_ID)));

        controller.ping(ROOM_CODE, headerAccessor);

        verify(flipbookRoomEventPublisher).publishPong(SESSION_ID, ROOM_CODE);
    }
}
