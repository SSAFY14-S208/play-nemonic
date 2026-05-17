package com.nemonicworld.infinitecanvas.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasCursorResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLeaveResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasLockResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasOpsAppliedResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasParticipantResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasStateResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventStateResponse;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasEventType;
import com.nemonicworld.infinitecanvas.dto.websocket.InfiniteCanvasSimpleMessageResponse;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

@Component
public class InfiniteCanvasEventPublisher {

    private static final String CANVAS_TOPIC_PREFIX = "/topic/infinite-canvas/canvases/";
    private static final String CANVAS_USER_QUEUE_PREFIX = "/queue/infinite-canvas/canvases/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String CANVAS_CLOSED_MESSAGE = "무한 캔버스가 종료되었습니다.";
    private static final CloseStatus LEFT_CANVAS_CLOSE_STATUS = CloseStatus.NORMAL.withReason("LEFT_CANVAS");
    private static final CloseStatus CANVAS_CLOSED_CLOSE_STATUS = CloseStatus.NORMAL.withReason("CANVAS_CLOSED");
    private static final String PONG_MESSAGE = "pong";
    private static final String DEFAULT_ERROR_MESSAGE = "무한 캔버스 요청을 처리할 수 없습니다.";

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public InfiniteCanvasEventPublisher(SimpMessagingTemplate messagingTemplate,
        WebSocketSessionRegistry webSocketSessionRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    public void publishParticipantConnected(InfiniteCanvasStateResponse response) {
        publishCanvasStateEvent(InfiniteCanvasEventType.PARTICIPANT_CONNECTED, response, currentUserUuid(response));
    }

    public void publishOperationsApplied(InfiniteCanvasOpsAppliedResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.OPS_APPLIED, response.roomCode(), response);
    }

    public void publishSnapshotUpdated(InfiniteCanvasStateResponse response) {
        publishCanvasStateEvent(InfiniteCanvasEventType.SNAPSHOT_UPDATED, response, currentUserUuid(response));
    }

    public void publishCursorUpdated(InfiniteCanvasCursorResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.CURSOR_UPDATED, response.roomCode(), response);
    }

    public void publishLockAcquired(InfiniteCanvasLockResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.LOCK_ACQUIRED, response.roomCode(), response);
    }

    public void publishLockReleased(InfiniteCanvasLockResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.LOCK_RELEASED, response.roomCode(), response);
    }

    public void publishParticipantDisconnected(InfiniteCanvasStateResponse response) {
        publishCanvasStateEvent(InfiniteCanvasEventType.PARTICIPANT_DISCONNECTED, response, currentUserUuid(response));
    }

    public void publishParticipantLeft(InfiniteCanvasLeaveResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.PARTICIPANT_LEFT, response.roomCode(), response);
    }

    public void publishHostChanged(InfiniteCanvasLeaveResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.HOST_CHANGED, response.roomCode(), response);
    }

    public void publishParticipantUpdated(String roomCode, InfiniteCanvasParticipantResponse response) {
        publishCanvasEvent(InfiniteCanvasEventType.PARTICIPANT_UPDATED, roomCode, response);
    }

    public void publishCanvasClosed(String roomCode, Object data) {
        Object payload = data == null ? new InfiniteCanvasSimpleMessageResponse(CANVAS_CLOSED_MESSAGE) : data;
        publishCanvasEvent(InfiniteCanvasEventType.CANVAS_CLOSED, roomCode, payload);
        closeCanvasSessions(roomCode);
    }

    public void publishDuplicateSessionClosed(String sessionId, String roomCode) {
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(
            InfiniteCanvasEventType.DUPLICATE_SESSION_CLOSED, roomCode,
            new InfiniteCanvasSimpleMessageResponse(DUPLICATE_SESSION_CLOSED_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, CANVAS_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    public void publishPong(String sessionId, String roomCode) {
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(InfiniteCanvasEventType.PONG, roomCode,
            new InfiniteCanvasSimpleMessageResponse(PONG_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, CANVAS_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    public void publishError(String sessionId, String roomCode, String message) {
        String safeMessage = org.springframework.util.StringUtils.hasText(message) ? message : DEFAULT_ERROR_MESSAGE;
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(InfiniteCanvasEventType.ERROR, roomCode,
            new InfiniteCanvasSimpleMessageResponse(safeMessage));

        messagingTemplate.convertAndSendToUser(sessionId, CANVAS_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    public void closeStaleSession(ActiveWebSocketSession session) {
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId());
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
    }

    public void closeLeftCanvasSession(String roomCode, String leftUserUuid) {
        webSocketSessionRegistry
            .findCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, roomCode, leftUserUuid)
            .ifPresent(this::closeLeftCanvasSession);
    }

    private void publishCanvasEvent(InfiniteCanvasEventType type, String roomCode, Object data) {
        InfiniteCanvasEventResponse event = InfiniteCanvasEventResponse.of(type, roomCode, data);

        messagingTemplate.convertAndSend(CANVAS_TOPIC_PREFIX + roomCode, event);
    }

    private void publishCanvasStateEvent(InfiniteCanvasEventType type, InfiniteCanvasStateResponse response,
        String changedUserUuid) {
        publishCanvasEvent(type, response.roomCode(), InfiniteCanvasEventStateResponse.from(response, changedUserUuid));
    }

    private String currentUserUuid(InfiniteCanvasStateResponse response) {
        return response.me() == null ? null : response.me().userUuid();
    }

    private void closeCanvasSessions(String roomCode) {
        webSocketSessionRegistry
            .findCurrentSessions(WebSocketSessionAttributes.CONNECTION_TYPE_INFINITE_CANVAS, roomCode)
            .forEach(session -> webSocketSessionRegistry.closeWebSocketSession(session.sessionId(),
                CANVAS_CLOSED_CLOSE_STATUS));
    }

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }

    private void closeLeftCanvasSession(ActiveWebSocketSession session) {
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId(), LEFT_CANVAS_CLOSE_STATUS);
    }
}
