package com.nemonicworld.relay.websocket;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomAllPartsCompletedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomClosedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventStateResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventType;
import com.nemonicworld.relay.dto.websocket.RelayRoomPartAutoSubmittedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomPartStartedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomPartSubmittedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomParticipantKickedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomResultCreatedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomSimpleMessageResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationResult;
import java.time.LocalDateTime;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * 릴레이 방 WebSocket topic/user queue로 이벤트를 발행합니다.
 */
@Component
public class RelayRoomEventPublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/relay/rooms/";
    private static final String ROOM_USER_QUEUE_PREFIX = "/queue/relay/rooms/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String KICKED_FROM_ROOM_MESSAGE = "방장에 의해 강퇴되었습니다.";
    private static final CloseStatus KICKED_FROM_ROOM_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION
        .withReason("KICKED_FROM_ROOM");
    private static final String PONG_MESSAGE = "pong";

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public RelayRoomEventPublisher(SimpMessagingTemplate messagingTemplate,
        WebSocketSessionRegistry webSocketSessionRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    /**
     * 참여자 연결 상태가 바뀐 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantConnected(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.PARTICIPANT_CONNECTED, roomStateResponse);
    }

    /**
     * 참여자 연결 해제 상태가 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantDisconnected(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.PARTICIPANT_DISCONNECTED, roomStateResponse);
    }

    /**
     * 대기실 참여자가 강퇴되었음을 방 전체에 알립니다.
     */
    public void publishParticipantKicked(RelayRoomKickResponse kickResponse) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.PARTICIPANT_KICKED,
            kickResponse.roomCode(), RelayRoomParticipantKickedEventResponse.from(kickResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + kickResponse.roomCode(), event);
    }

    /**
     * 명시적인 방 상태 갱신 이벤트가 필요할 때 현재 방 스냅샷을 방 전체에 보냅니다.
     */
    public void publishRoomUpdated(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.ROOM_UPDATED, roomStateResponse);
    }

    /**
     * 방 설정 변경이 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishSettingsChanged(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.SETTINGS_CHANGED, roomStateResponse);
    }

    /**
     * 게임 시작이 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishGameStarted(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.GAME_STARTED, roomStateResponse);
    }

    /**
     * 현재 파트 시작과 제한 시간 정보를 방 전체에 알립니다.
     */
    public void publishPartStarted(RelayRoomStateResponse roomStateResponse) {
        publishRoomEvent(RelayRoomEventType.PART_STARTED, roomStateResponse);
    }

    public void publishPartSubmitted(RelayRoomSubmissionResponse submissionResponse) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.PART_SUBMITTED,
            submissionResponse.roomCode(), RelayRoomPartSubmittedEventResponse.from(submissionResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + submissionResponse.roomCode(), event);
    }

    public void publishPartAutoSubmitted(String roomCode, String nickname, RelayRoomAssignment assignment) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.PART_AUTO_SUBMITTED, roomCode,
            RelayRoomPartAutoSubmittedEventResponse.from(roomCode, nickname, assignment));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    public void publishPartStarted(RelayRoomSubmissionResponse submissionResponse) {
        publishPartStarted(submissionResponse.roomCode(), submissionResponse.part(), submissionResponse.nextPart(),
            submissionResponse.nextPartStartedAt(), submissionResponse.nextPartDeadlineAt());
    }

    public void publishPartStarted(String roomCode, RelayDrawingPart previousPart, RelayDrawingPart part,
        LocalDateTime partStartedAt, LocalDateTime partDeadlineAt) {
        int timeLimitSeconds = (int) java.time.Duration.between(partStartedAt, partDeadlineAt).toSeconds();
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.PART_STARTED, roomCode,
            new RelayRoomPartStartedEventResponse(roomCode, previousPart, part, partStartedAt, partDeadlineAt,
                timeLimitSeconds));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    public void publishAllPartsCompleted(RelayRoomSubmissionResponse submissionResponse) {
        publishAllPartsCompleted(submissionResponse.roomCode(), submissionResponse.roomStatus(),
            submissionResponse.submittedAt());
    }

    public void publishAllPartsCompleted(String roomCode, RelayRoomStatus roomStatus, LocalDateTime completedAt) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.ALL_PARTS_COMPLETED, roomCode,
            new RelayRoomAllPartsCompletedEventResponse(roomCode, roomStatus, completedAt));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    public void publishResultCreated(RelayRoomFinalizationResult finalizationResult) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.RESULT_CREATED,
            finalizationResult.roomCode(), RelayRoomResultCreatedEventResponse.from(finalizationResult));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + finalizationResult.roomCode(), event);
    }

    public void publishRoomClosed(String roomCode, LocalDateTime closedAt) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.ROOM_CLOSED, roomCode,
            new RelayRoomClosedEventResponse(roomCode, RelayRoomStatus.CLOSED, closedAt));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    /**
     * 같은 roomCode + UUID로 교체된 기존 세션 개인 큐에 중복 접속 종료 안내를 보냅니다.
     */
    public void publishDuplicateSessionClosed(String sessionId, String roomCode) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.DUPLICATE_SESSION_CLOSED, roomCode,
            new RelayRoomSimpleMessageResponse(DUPLICATE_SESSION_CLOSED_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * 강퇴 대상자의 현재 개인 큐에 안내를 보낸 뒤 같은 서버의 활성 WebSocket 세션을 종료합니다.
     */
    public void publishKickedFromRoom(String roomCode, String kickedUserUuid) {
        webSocketSessionRegistry.findCurrentSession(roomCode, kickedUserUuid)
            .ifPresent(session -> publishKickedFromRoom(roomCode, session));
    }

    private void publishKickedFromRoom(String roomCode, ActiveWebSocketSession session) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.KICKED_FROM_ROOM, roomCode,
            new RelayRoomSimpleMessageResponse(KICKED_FROM_ROOM_MESSAGE));

        messagingTemplate.convertAndSendToUser(session.sessionId(), ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(session.sessionId()));
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId(), KICKED_FROM_ROOM_CLOSE_STATUS);
    }

    /**
     * ping을 보낸 현재 세션 개인 큐에 pong 이벤트를 보냅니다.
     */
    public void publishPong(String sessionId, String roomCode) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.PONG, roomCode,
            new RelayRoomSimpleMessageResponse(PONG_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * WebSocket 처리 중 클라이언트에 알려도 되는 안전한 오류 메시지를 개인 큐로 전달합니다.
     */
    public void publishError(String sessionId, String roomCode, String message) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(RelayRoomEventType.ERROR, roomCode,
            new RelayRoomSimpleMessageResponse(message));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    private void publishRoomEvent(RelayRoomEventType type, RelayRoomStateResponse roomStateResponse) {
        RelayRoomEventResponse event = RelayRoomEventResponse.of(type, roomStateResponse.roomCode(),
            RelayRoomEventStateResponse.from(roomStateResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomStateResponse.roomCode(), event);
    }

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }
}
