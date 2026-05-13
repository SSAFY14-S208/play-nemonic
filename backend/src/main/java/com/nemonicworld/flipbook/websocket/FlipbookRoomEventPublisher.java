package com.nemonicworld.flipbook.websocket;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookFrameSubmitResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookAllRoundsCompletedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookFrameAutoSubmittedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomClosedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventType;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomHostChangedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomParticipantKickedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomParticipantDroppedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomParticipantLeftEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomResultCreatedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomSimpleMessageResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoundStartedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoundTimeUpEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoundTimeUpEventResponse.PendingSubmission;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.disconnect.FlipbookDroppedParticipantResult;
import com.nemonicworld.flipbook.service.disconnect.FlipbookHostChangeResult;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationResult;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * 플립북 방 WebSocket topic/user queue로 이벤트를 발행합니다.
 */
@Component
@RequiredArgsConstructor
public class FlipbookRoomEventPublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/flipbook/rooms/";
    private static final String ROOM_USER_QUEUE_PREFIX = "/queue/flipbook/rooms/";
    private static final String DUPLICATE_SESSION_CLOSED_MESSAGE = "다른 곳에서 접속되어 연결이 종료되었습니다.";
    private static final String KICKED_FROM_ROOM_MESSAGE = "방장에 의해 강퇴되었습니다.";
    private static final CloseStatus KICKED_FROM_ROOM_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION
        .withReason("KICKED_FROM_ROOM");
    private static final CloseStatus LEFT_ROOM_CLOSE_STATUS = CloseStatus.NORMAL.withReason("LEFT_ROOM");
    private static final CloseStatus ROOM_CLOSED_CLOSE_STATUS = CloseStatus.NORMAL.withReason("ROOM_CLOSED");
    private static final String PONG_MESSAGE = "pong";

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    /**
     * 참여자 연결 상태가 바뀐 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantConnected(FlipbookRoomStateResponse roomStateResponse, String connectedUserUuid) {
        publishRoomEvent(FlipbookRoomEventType.PARTICIPANT_CONNECTED, roomStateResponse, connectedUserUuid);
    }

    /**
     * 참여자 연결 해제 상태가 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishParticipantDisconnected(FlipbookRoomStateResponse roomStateResponse,
        String disconnectedUserUuid) {
        publishRoomEvent(FlipbookRoomEventType.PARTICIPANT_DISCONNECTED, roomStateResponse, disconnectedUserUuid);
    }

    /**
     * 게임 중 재접속 유예가 끝나 참여자가 이탈 확정되었음을 방 전체에 알립니다.
     */
    public void publishParticipantDropped(FlipbookDroppedParticipantResult droppedParticipantResult) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.PARTICIPANT_DROPPED,
            droppedParticipantResult.roomCode(),
            FlipbookRoomParticipantDroppedEventResponse.from(droppedParticipantResult));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + droppedParticipantResult.roomCode(), event);
    }

    /**
     * 방 설정 변경이 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishSettingsChanged(FlipbookRoomStateResponse roomStateResponse) {
        publishRoomEvent(FlipbookRoomEventType.SETTINGS_CHANGED, roomStateResponse);
    }

    /**
     * 게임 시작이 반영된 최신 방 상태를 방 전체에 알립니다.
     */
    public void publishGameStarted(FlipbookRoomStateResponse roomStateResponse) {
        publishRoomEvent(FlipbookRoomEventType.GAME_STARTED, roomStateResponse);
    }

    /**
     * 참여자의 프레임 제출 결과와 라운드 진행 상태를 방 전체에 알립니다.
     */
    public void publishFrameSubmitted(FlipbookFrameSubmitResponse submitResponse) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.FRAME_SUBMITTED,
            submitResponse.roomCode(), submitResponse);

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + submitResponse.roomCode(), event);
    }

    /**
     * 현재 라운드 제한 시간이 끝나 클라이언트가 현재 캔버스를 제출해야 함을 방 전체에 알립니다.
     */
    public void publishRoundTimeUp(String roomCode, int round, LocalDateTime roundDeadlineAt,
        LocalDateTime submitGraceDeadlineAt, long autoSubmitGraceMillis, List<PendingSubmission> pendingSubmissions) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.ROUND_TIME_UP, roomCode,
            new FlipbookRoundTimeUpEventResponse(roomCode, round, roundDeadlineAt, submitGraceDeadlineAt,
                autoSubmitGraceMillis, pendingSubmissions == null ? 0 : pendingSubmissions.size(), pendingSubmissions));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    /**
     * 마감 시간으로 프레임이 빈 제출 처리되었음을 방 전체에 알립니다.
     */
    public void publishFrameAutoSubmitted(String roomCode, String nickname, FlipbookFrameAssignment assignment) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.FRAME_AUTO_SUBMITTED,
            roomCode, FlipbookFrameAutoSubmittedEventResponse.from(roomCode, nickname, assignment));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    /**
     * 현재 라운드가 완료되어 다음 라운드가 시작되었음을 방 전체에 알립니다.
     */
    public void publishRoundStarted(String roomCode, Integer previousRound, Integer round, LocalDateTime roundStartedAt,
        LocalDateTime roundDeadlineAt) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.ROUND_STARTED, roomCode,
            FlipbookRoundStartedEventResponse.of(roomCode, previousRound, round, roundStartedAt, roundDeadlineAt));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    /**
     * 마지막 라운드까지 완료되어 결과 생성 대기 상태가 되었음을 방 전체에 알립니다.
     */
    public void publishAllRoundsCompleted(String roomCode, FlipbookRoomStatus roomStatus, LocalDateTime completedAt) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.ALL_ROUNDS_COMPLETED,
            roomCode, new FlipbookAllRoundsCompletedEventResponse(roomCode, roomStatus, completedAt));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
    }

    /**
     * 최종 GIF 결과 생성과 갤러리 저장이 완료되었음을 방 전체에 알립니다.
     */
    public void publishResultCreated(FlipbookRoomFinalizationResult finalizationResult) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.RESULT_CREATED,
            finalizationResult.roomCode(), FlipbookRoomResultCreatedEventResponse.from(finalizationResult));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + finalizationResult.roomCode(), event);
    }

    /**
     * 대기실 참여자가 강퇴되었음을 방 전체에 알립니다.
     */
    public void publishParticipantKicked(FlipbookRoomKickResponse kickResponse) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.PARTICIPANT_KICKED,
            kickResponse.roomCode(), FlipbookRoomParticipantKickedEventResponse.from(kickResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + kickResponse.roomCode(), event);
    }

    /**
     * 강퇴 대상자의 현재 개인 큐에 안내를 보낸 뒤 같은 서버의 활성 WebSocket 세션을 종료합니다.
     */
    public void publishKickedFromRoom(String roomCode, String kickedUserUuid) {
        webSocketSessionRegistry
            .findCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, roomCode, kickedUserUuid)
            .ifPresent(session -> publishKickedFromRoom(roomCode, session));
    }

    /**
     * 참여자가 대기실에서 스스로 퇴장했음을 방 전체에 알립니다.
     */
    public void publishParticipantLeft(FlipbookRoomLeaveResponse leaveResponse) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.PARTICIPANT_LEFT,
            leaveResponse.roomCode(), FlipbookRoomParticipantLeftEventResponse.from(leaveResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + leaveResponse.roomCode(), event);
    }

    /**
     * 방장 퇴장으로 새 방장이 승계되었음을 방 전체에 알립니다.
     */
    public void publishHostChanged(FlipbookRoomLeaveResponse leaveResponse) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.HOST_CHANGED,
            leaveResponse.roomCode(), FlipbookRoomHostChangedEventResponse.from(leaveResponse));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + leaveResponse.roomCode(), event);
    }

    /**
     * 게임 중 방장 이탈 확정으로 새 방장이 승계되었음을 방 전체에 알립니다.
     */
    public void publishHostChanged(FlipbookHostChangeResult hostChangeResult) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.HOST_CHANGED,
            hostChangeResult.roomCode(), FlipbookRoomHostChangedEventResponse.from(hostChangeResult));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + hostChangeResult.roomCode(), event);
    }

    /**
     * 마지막 참여자 퇴장으로 방이 종료되었음을 방 전체에 알립니다.
     */
    public void publishRoomClosed(String roomCode, LocalDateTime closedAt) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.ROOM_CLOSED, roomCode,
            new FlipbookRoomClosedEventResponse(roomCode, FlipbookRoomStatus.CLOSED, closedAt));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomCode, event);
        closeRoomSessions(roomCode);
    }

    private void closeRoomSessions(String roomCode) {
        webSocketSessionRegistry.findCurrentSessions(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, roomCode)
            .forEach(session -> webSocketSessionRegistry.closeWebSocketSession(session.sessionId(),
                ROOM_CLOSED_CLOSE_STATUS));
    }

    /**
     * 스스로 퇴장한 사용자의 같은 서버 활성 WebSocket 세션이 있으면 정상 종료합니다.
     */
    public void closeLeftRoomSession(String roomCode, String leftUserUuid) {
        webSocketSessionRegistry
            .findCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK, roomCode, leftUserUuid)
            .ifPresent(this::closeLeftRoomSession);
    }

    /**
     * 같은 roomCode + UUID로 교체된 기존 세션 개인 큐에 중복 접속 종료 안내를 보냅니다.
     */
    public void publishDuplicateSessionClosed(String sessionId, String roomCode) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.DUPLICATE_SESSION_CLOSED,
            roomCode, new FlipbookRoomSimpleMessageResponse(DUPLICATE_SESSION_CLOSED_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * ping을 보낸 현재 세션 개인 큐에 pong 이벤트를 보냅니다.
     */
    public void publishPong(String sessionId, String roomCode) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.PONG, roomCode,
            new FlipbookRoomSimpleMessageResponse(PONG_MESSAGE));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    /**
     * WebSocket 처리 중 클라이언트에 알려도 되는 안전한 오류 메시지를 개인 큐로 전달합니다.
     */
    public void publishError(String sessionId, String roomCode, String message) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.ERROR, roomCode,
            new FlipbookRoomSimpleMessageResponse(message));

        messagingTemplate.convertAndSendToUser(sessionId, ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(sessionId));
    }

    private void publishRoomEvent(FlipbookRoomEventType type, FlipbookRoomStateResponse roomStateResponse) {
        publishRoomEvent(type, roomStateResponse, null);
    }

    private void publishRoomEvent(FlipbookRoomEventType type, FlipbookRoomStateResponse roomStateResponse,
        String changedUserUuid) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(type, roomStateResponse.roomCode(),
            FlipbookRoomEventStateResponse.from(roomStateResponse, changedUserUuid));

        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomStateResponse.roomCode(), event);
    }

    private void publishKickedFromRoom(String roomCode, ActiveWebSocketSession session) {
        FlipbookRoomEventResponse event = FlipbookRoomEventResponse.of(FlipbookRoomEventType.KICKED_FROM_ROOM, roomCode,
            new FlipbookRoomSimpleMessageResponse(KICKED_FROM_ROOM_MESSAGE));

        messagingTemplate.convertAndSendToUser(session.sessionId(), ROOM_USER_QUEUE_PREFIX + roomCode, event,
            createSessionHeaders(session.sessionId()));
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId(), KICKED_FROM_ROOM_CLOSE_STATUS);
    }

    private void closeLeftRoomSession(ActiveWebSocketSession session) {
        webSocketSessionRegistry.removeStaleSession(session.sessionId());
        webSocketSessionRegistry.closeWebSocketSession(session.sessionId(), LEFT_ROOM_CLOSE_STATUS);
    }

    private MessageHeaders createSessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        return headerAccessor.getMessageHeaders();
    }
}
