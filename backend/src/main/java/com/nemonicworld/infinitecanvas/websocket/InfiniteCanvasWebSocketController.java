package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasCursorRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasLockRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.exception.InfiniteCanvasRevisionConflictException;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import java.util.Optional;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class InfiniteCanvasWebSocketController {

    private final InfiniteCanvasService infiniteCanvasService;
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public InfiniteCanvasWebSocketController(InfiniteCanvasService infiniteCanvasService,
        InfiniteCanvasEventPublisher infiniteCanvasEventPublisher, WebSocketSessionRegistry webSocketSessionRegistry) {
        this.infiniteCanvasService = infiniteCanvasService;
        this.infiniteCanvasEventPublisher = infiniteCanvasEventPublisher;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @MessageMapping("/infinite-canvas/canvases/{roomCode}/ping")
    public void ping(@DestinationVariable("roomCode") String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(roomCode, headerAccessor).ifPresent(
            session -> infiniteCanvasEventPublisher.publishPong(session.sessionId(), session.connectionKey()));
    }

    @MessageMapping("/infinite-canvas/canvases/{roomCode}/snapshot")
    public void replaceSnapshot(@DestinationVariable("roomCode") String roomCode,
        @Payload InfiniteCanvasSnapshotRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(roomCode, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasStateResponse response = infiniteCanvasService.replaceSnapshot(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishSnapshotUpdated(response);
            } catch (InfiniteCanvasRevisionConflictException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage(),
                    e.response());
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    @MessageMapping("/infinite-canvas/canvases/{roomCode}/ops")
    public void applyOperations(@DestinationVariable("roomCode") String roomCode,
        @Payload InfiniteCanvasOpsRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(roomCode, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasOpsAppliedResponse response = infiniteCanvasService.applyOperations(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishOperationsApplied(response);
            } catch (InfiniteCanvasRevisionConflictException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage(),
                    e.response());
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    @MessageMapping("/infinite-canvas/canvases/{roomCode}/cursor")
    public void updateCursor(@DestinationVariable("roomCode") String roomCode,
        @Payload InfiniteCanvasCursorRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(roomCode, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasCursorResponse response = infiniteCanvasService.updateCursor(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishCursorUpdated(response);
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    @MessageMapping("/infinite-canvas/canvases/{roomCode}/locks/acquire")
    public void acquireLock(@DestinationVariable("roomCode") String roomCode,
        @Payload InfiniteCanvasLockRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(roomCode, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasLockResponse response = infiniteCanvasService.acquireLock(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishLockAcquired(response);
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    @MessageMapping("/infinite-canvas/canvases/{roomCode}/locks/release")
    public void releaseLock(@DestinationVariable("roomCode") String roomCode,
        @Payload InfiniteCanvasLockRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(roomCode, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasLockResponse response = infiniteCanvasService.releaseLock(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishLockReleased(response);
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    private Optional<ActiveWebSocketSession> currentCanvasSession(String roomCode,
        SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Optional<ActiveWebSocketSession> activeSession = webSocketSessionRegistry.findBySessionId(sessionId);
        if (activeSession.isEmpty() || !isCurrentCanvasSession(roomCode, activeSession.get(), sessionId)) {
            return Optional.empty();
        }

        return activeSession;
    }

    private boolean isCurrentCanvasSession(String roomCode, ActiveWebSocketSession activeSession, String sessionId) {
        return WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS.equals(activeSession.connectionType())
            && roomCode.equals(activeSession.connectionKey())
            && webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
                activeSession.connectionKey(), activeSession.userUuid(), sessionId);
    }
}
