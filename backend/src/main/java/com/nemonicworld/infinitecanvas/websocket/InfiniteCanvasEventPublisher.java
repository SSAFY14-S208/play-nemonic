package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventType;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasSimpleMessageResponse;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;

@Component
public class InfiniteCanvasEventPublisher {

    private static final String CANVAS_TOPIC_PREFIX = "/topic/infinite-canvas/canvases/";
    private static final String CANVAS_USER_QUEUE_PREFIX = "/queue/infinite-canvas/canvases/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String DEFAULT_ERROR_MESSAGE = "무한 캔버스 요청을 처리할 수 없습니다.";

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public InfiniteCanvasEventPublisher(SimpMessagingTemplate messagingTemplate,
        WebSocketSessionRegistry webSocketSessionRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public void publishParticipantConnected(InfiniteCanvasStateResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.PARTICIPANT_CONNECTED, response.canvasId(), response);
    }

    public void publishOperationsApplied(InfiniteCanvasOpsAppliedResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.OPS_APPLIED, response.canvasId(), response);
    }

    public void publishSnapshotUpdated(InfiniteCanvasStateResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.SNAPSHOT_UPDATED, response.canvasId(), response);
    }

    public void publishParticipantDisconnected(InfiniteCanvasStateResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.PARTICIPANT_DISCONNECTED, response.canvasId(), response);
    }

    public void publishDuplicateSessionClosed(String sessionId, String canvasId) {
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(
            InfiniteCanvasEventType.DUPLICATE_SESSION_CLOSED, canvasId,
            new InfiniteCanvasSimpleMessageResponse(DUPLICATE_SESSION_CLOSED_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, CANVAS_USER_QUEUE_PREFIX + canvasId, event,
            createSessionHeaders(sessionId));
    }

    public void publishError(String sessionId, String canvasId, String message) {
        String safeMessage = org.springframework.util.StringUtils.hasText(message) ? message : DEFAULT_ERROR_MESSAGE;
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(InfiniteCanvasEventType.ERROR, canvasId,
            new InfiniteCanvasSimpleMessageResponse(safeMessage));

        messagingTemplate.convertAndSendToUser(sessionId, CANVAS_USER_QUEUE_PREFIX + canvasId, event,
            createSessionHeaders(sessionId));
    }

    public void closeStaleSession(ActiveWebSocketSession session) {
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId());
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
    }

    private void publishCanvasEvent(InfiniteCanvasEventType type, String canvasId, Object data) {
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(type, canvasId, data);

        messagingTemplate.convertAndSend(CANVAS_TOPIC_PREFIX + canvasId, event);
    }

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }
}
