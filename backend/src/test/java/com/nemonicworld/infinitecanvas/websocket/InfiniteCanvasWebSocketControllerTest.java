package com.nemonicworld.infinitecanvas.websocket;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasLockRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

class InfiniteCanvasWebSocketControllerTest {

    private static final String ROOM_CODE = "AC3K9Q";
    private static final String USER_UUID = "user-uuid";
    private static final String SESSION_ID = "session-1";

    private final InfiniteCanvasService infiniteCanvasService = mock(InfiniteCanvasService.class);
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher = mock(InfiniteCanvasEventPublisher.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final InfiniteCanvasWebSocketController controller = new InfiniteCanvasWebSocketController(
        infiniteCanvasService, infiniteCanvasEventPublisher, webSocketSessionRegistry);

    @Test
    void pingPublishesPongToCurrentCanvasSession() {
        SimpMessageHeaderAccessor headerAccessor = currentCanvasHeaderAccessor();

        controller.ping(ROOM_CODE, headerAccessor);

        verify(infiniteCanvasEventPublisher).publishPong(SESSION_ID, ROOM_CODE);
    }

    @Test
    void updateCursorPublishesCursorUpdatedEvent() {
        SimpMessageHeaderAccessor headerAccessor = currentCanvasHeaderAccessor();
        InfiniteCanvasCursorRequest request = new InfiniteCanvasCursorRequest(10.0, 20.0, 1.2, null);
        InfiniteCanvasCursorResponse response = new InfiniteCanvasCursorResponse(ROOM_CODE,
            new InfiniteCanvasCursor(USER_UUID, 10.0, 20.0, 1.2, null, LocalDateTime.now()));
        given(infiniteCanvasService.updateCursor(USER_UUID, ROOM_CODE, request)).willReturn(response);

        controller.updateCursor(ROOM_CODE, request, headerAccessor);

        verify(infiniteCanvasService).updateCursor(USER_UUID, ROOM_CODE, request);
        verify(infiniteCanvasEventPublisher).publishCursorUpdated(response);
    }

    @Test
    void acquireLockPublishesLockAcquiredEvent() {
        SimpMessageHeaderAccessor headerAccessor = currentCanvasHeaderAccessor();
        InfiniteCanvasLockRequest request = new InfiniteCanvasLockRequest("shape-1");
        InfiniteCanvasLock lock = new InfiniteCanvasLock("shape-1", USER_UUID, LocalDateTime.now(),
            LocalDateTime.now().plusSeconds(30));
        InfiniteCanvasLockResponse response = new InfiniteCanvasLockResponse(ROOM_CODE, "shape-1", lock);
        given(infiniteCanvasService.acquireLock(USER_UUID, ROOM_CODE, request)).willReturn(response);

        controller.acquireLock(ROOM_CODE, request, headerAccessor);

        verify(infiniteCanvasService).acquireLock(USER_UUID, ROOM_CODE, request);
        verify(infiniteCanvasEventPublisher).publishLockAcquired(response);
    }

    @Test
    void releaseLockPublishesLockReleasedEvent() {
        SimpMessageHeaderAccessor headerAccessor = currentCanvasHeaderAccessor();
        InfiniteCanvasLockRequest request = new InfiniteCanvasLockRequest("shape-1");
        InfiniteCanvasLockResponse response = new InfiniteCanvasLockResponse(ROOM_CODE, "shape-1", null);
        given(infiniteCanvasService.releaseLock(USER_UUID, ROOM_CODE, request)).willReturn(response);

        controller.releaseLock(ROOM_CODE, request, headerAccessor);

        verify(infiniteCanvasService).releaseLock(USER_UUID, ROOM_CODE, request);
        verify(infiniteCanvasEventPublisher).publishLockReleased(response);
    }

    private SimpMessageHeaderAccessor currentCanvasHeaderAccessor() {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId(SESSION_ID);
        given(webSocketSessionRegistry.findBySessionId(SESSION_ID)).willReturn(Optional.of(new ActiveWebSocketSession(
            WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, ROOM_CODE, USER_UUID, SESSION_ID)));
        given(webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
            ROOM_CODE, USER_UUID, SESSION_ID)).willReturn(true);

        return headerAccessor;
    }
}
