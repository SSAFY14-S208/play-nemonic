package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasOpsRequest;
import com.nemonicworld.infinitecanvas.dto.request.InfiniteCanvasSnapshotRequest;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
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

    @MessageMapping("/infinite-canvas/canvases/{canvasId}/snapshot")
    public void replaceSnapshot(@DestinationVariable("canvasId") String canvasId,
        @Payload InfiniteCanvasSnapshotRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(canvasId, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasStateResponse response = infiniteCanvasService.replaceSnapshot(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishSnapshotUpdated(response);
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    @MessageMapping("/infinite-canvas/canvases/{canvasId}/ops")
    public void applyOperations(@DestinationVariable("canvasId") String canvasId,
        @Payload InfiniteCanvasOpsRequest request, SimpMessageHeaderAccessor headerAccessor) {
        currentCanvasSession(canvasId, headerAccessor).ifPresent(session -> {
            try {
                InfiniteCanvasOpsAppliedResponse response = infiniteCanvasService.applyOperations(session.userUuid(),
                    session.connectionKey(), request);
                infiniteCanvasEventPublisher.publishOperationsApplied(response);
            } catch (RuntimeException e) {
                infiniteCanvasEventPublisher.publishError(session.sessionId(), session.connectionKey(), e.getMessage());
            }
        });
    }

    private Optional<ActiveWebSocketSession> currentCanvasSession(String canvasId,
        SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        Optional<ActiveWebSocketSession> activeSession = webSocketSessionRegistry.findBySessionId(sessionId);
        if (activeSession.isEmpty() || !isCurrentCanvasSession(canvasId, activeSession.get(), sessionId)) {
            return Optional.empty();
        }

        return activeSession;
    }

    private boolean isCurrentCanvasSession(String canvasId, ActiveWebSocketSession activeSession, String sessionId) {
        return WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS.equals(activeSession.connectionType())
            && canvasId.equals(activeSession.connectionKey())
            && webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
                activeSession.connectionKey(), activeSession.userUuid(), sessionId);
    }
}
