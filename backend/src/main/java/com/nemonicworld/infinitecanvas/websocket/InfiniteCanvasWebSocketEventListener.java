package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.service.InfiniteCanvasService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class InfiniteCanvasWebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(InfiniteCanvasWebSocketEventListener.class);

    private final InfiniteCanvasService infiniteCanvasService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final InfiniteCanvasEventPublisher infiniteCanvasEventPublisher;

    public InfiniteCanvasWebSocketEventListener(InfiniteCanvasService infiniteCanvasService,
        WebSocketSessionRegistry webSocketSessionRegistry, InfiniteCanvasEventPublisher infiniteCanvasEventPublisher) {
        this.infiniteCanvasService = infiniteCanvasService;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.infiniteCanvasEventPublisher = infiniteCanvasEventPublisher;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Optional<ActiveWebSocketSession> activeSession = webSocketSessionRegistry.findBySessionId(sessionId);

        if (activeSession.isEmpty()) {
            return;
        }

        ActiveWebSocketSession session = activeSession.get();
        if (!WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS.equals(session.connectionType())) {
            return;
        }

        String canvasId = session.connectionKey();
        if (!webSocketSessionRegistry.isCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS,
            canvasId, session.userUuid(), sessionId)) {
            webSocketSessionRegistry.removeStaleSession(sessionId);
            return;
        }

        try {
            InfiniteCanvasStateResponse response = infiniteCanvasService.disconnectCanvas(session.userUuid(), canvasId);
            infiniteCanvasEventPublisher.publishParticipantDisconnected(response);
        } catch (RuntimeException e) {
            log.warn("Failed to update infinite canvas websocket disconnect state. canvasId={}, sessionId={}", canvasId,
                sessionId, e);
        } finally {
            webSocketSessionRegistry.removeIfCurrent(sessionId);
        }
    }
}
