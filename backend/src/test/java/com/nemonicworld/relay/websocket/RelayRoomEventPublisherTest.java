package com.nemonicworld.relay.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import com.nemonicworld.relay.dto.response.RelayRoomParticipantResponse;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomAllPartsCompletedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomClosedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventStateResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomEventType;
import com.nemonicworld.relay.dto.websocket.RelayRoomHostChangedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomPartAutoSubmittedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomPartStartedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomParticipantDroppedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomParticipantKickedEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomParticipantLeftEventResponse;
import com.nemonicworld.relay.dto.websocket.RelayRoomResultCreatedEventResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.disconnect.RelayDroppedParticipantResult;
import com.nemonicworld.relay.service.disconnect.RelayHostChangeResult;
import com.nemonicworld.relay.service.finalization.RelayFinalizationArtifactResult;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 릴레이 WebSocket 이벤트 발행 시 방 전체 topic과 개인 session queue 라우팅 헤더를 검증합니다.
 */
class RelayRoomEventPublisherTest {

    private static final String ROOM_CODE = "QUDNKQ";
    private static final String SESSION_ID = "session-1";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final RelayRoomEventPublisher publisher = new RelayRoomEventPublisher(messagingTemplate,
        webSocketSessionRegistry);

    /**
     * 설정 변경 이벤트는 방 전체 topic에 SETTINGS_CHANGED 타입과 최신 방 상태를 보냅니다.
     */
    @Test
    void publishSettingsChangedSendsSettingsChangedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = roomStateResponse(45);

        publisher.publishSettingsChanged(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.SETTINGS_CHANGED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(RelayRoomEventStateResponse.class);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.timeLimitSeconds()).isEqualTo(45);
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.changedParticipant()).isNull();
    }

    /**
     * 참여자 연결 이벤트는 최신 방 상태와 함께 연결된 사용자 UUID/닉네임을 보냅니다.
     */
    @Test
    void publishParticipantConnectedSendsChangedUserInfoToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = roomStateResponse(45);

        publisher.publishParticipantConnected(roomStateResponse, USER_UUID);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PARTICIPANT_CONNECTED);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.changedParticipant()).isNotNull();
        assertThat(data.changedParticipant().userUuid()).isEqualTo(USER_UUID);
        assertThat(data.changedParticipant().nickname()).isEqualTo("망고");
        assertThat(data.changedParticipant().host()).isTrue();
        assertThat(data.changedParticipant().joinOrder()).isZero();
        assertThat(data.changedParticipant().connected()).isTrue();
    }

    /**
     * 게임 시작 이벤트는 방 전체 topic에 GAME_STARTED 타입과 현재 파트 타이밍을 함께 보냅니다.
     */
    @Test
    void publishGameStartedSendsGameStartedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = startedRoomStateResponse();

        publisher.publishGameStarted(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.GAME_STARTED);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(data.currentPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(data.assignmentCount()).isEqualTo(6);
        assertThat(data.partDeadlineAt()).isEqualTo(roomStateResponse.partDeadlineAt());
    }

    /**
     * 파트 시작 이벤트는 동일한 방 상태 payload를 PART_STARTED 타입으로 보냅니다.
     */
    @Test
    void publishPartStartedSendsPartStartedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomStateResponse roomStateResponse = startedRoomStateResponse();

        publisher.publishPartStarted(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PART_STARTED);

        RelayRoomEventStateResponse data = (RelayRoomEventStateResponse) event.data();
        assertThat(data.currentPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(data.partStartedAt()).isEqualTo(roomStateResponse.partStartedAt());
    }

    @Test
    void publishPartStartedAfterSubmissionSendsNextPartPayloadToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomSubmissionResponse response = advancedSubmissionResponse();

        publisher.publishPartStarted(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PART_STARTED);

        RelayRoomPartStartedEventResponse data = (RelayRoomPartStartedEventResponse) event.data();
        assertThat(data.previousPart()).isEqualTo(RelayDrawingPart.FACE);
        assertThat(data.part()).isEqualTo(RelayDrawingPart.BODY);
        assertThat(data.partStartedAt()).isEqualTo(response.nextPartStartedAt());
        assertThat(data.partDeadlineAt()).isEqualTo(response.nextPartDeadlineAt());
        assertThat(data.timeLimitSeconds()).isEqualTo(45);
    }

    @Test
    void publishAllPartsCompletedSendsAllPartsCompletedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        RelayRoomSubmissionResponse response = allPartsCompletedSubmissionResponse();

        publisher.publishAllPartsCompleted(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.ALL_PARTS_COMPLETED);

        RelayRoomAllPartsCompletedEventResponse data = (RelayRoomAllPartsCompletedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.roomStatus()).isEqualTo(RelayRoomStatus.FINALIZING);
        assertThat(data.completedAt()).isEqualTo(response.submittedAt());
    }

    @Test
    void publishPartAutoSubmittedSendsAutoSubmittedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime submittedAt = LocalDateTime.now().minusSeconds(1);
        RelayRoomAssignment assignment = new RelayRoomAssignment(1, RelayDrawingPart.BODY,
            "550e8400-e29b-41d4-a716-446655440000", RelayAssignmentStatus.AUTO_SUBMITTED, null, null, null, true, true,
            submittedAt);

        publisher.publishPartAutoSubmitted(ROOM_CODE, "Mango", assignment);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PART_AUTO_SUBMITTED);

        RelayRoomPartAutoSubmittedEventResponse data = (RelayRoomPartAutoSubmittedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.userUuid()).isEqualTo(assignment.assignedUserUuid());
        assertThat(data.nickname()).isEqualTo("Mango");
        assertThat(data.canvasIndex()).isEqualTo(1);
        assertThat(data.part()).isEqualTo(RelayDrawingPart.BODY);
        assertThat(data.assignmentStatus()).isEqualTo(RelayAssignmentStatus.AUTO_SUBMITTED);
        assertThat(data.empty()).isTrue();
        assertThat(data.submittedAt()).isEqualTo(submittedAt);
    }

    @Test
    void publishResultCreatedSendsResultCreatedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        UUID artifactId = UUID.randomUUID();
        RelayRoomFinalizationResult result = RelayRoomFinalizationResult.finished(ROOM_CODE,
            List.of(new RelayFinalizationArtifactResult(artifactId, 0,
                "relay/results/%s/original.png".formatted(artifactId),
                "relay/results/%s/thumbnail.png".formatted(artifactId), "{}")),
            LocalDateTime.now().minusSeconds(1));

        publisher.publishResultCreated(result);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.RESULT_CREATED);

        RelayRoomResultCreatedEventResponse data = (RelayRoomResultCreatedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.roomStatus()).isEqualTo(RelayRoomStatus.FINISHED);
        assertThat(data.resultCount()).isEqualTo(1);
        assertThat(data.artifactIds()).containsExactly(artifactId);
        assertThat(data.results()).hasSize(1);
        assertThat(data.results().get(0).contentUrl()).isEqualTo("relay/results/%s/original.png".formatted(artifactId));
    }

    @Test
    void publishRoomClosedSendsRoomClosedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime closedAt = LocalDateTime.now().minusSeconds(1);

        publisher.publishRoomClosed(ROOM_CODE, closedAt);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.ROOM_CLOSED);

        RelayRoomClosedEventResponse data = (RelayRoomClosedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.roomStatus()).isEqualTo(RelayRoomStatus.CLOSED);
        assertThat(data.closedAt()).isEqualTo(closedAt);
    }

    @Test
    void publishParticipantKickedSendsParticipantKickedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime kickedAt = LocalDateTime.now().minusSeconds(1);
        RelayRoomKickResponse response = new RelayRoomKickResponse(ROOM_CODE, "11111111-1111-1111-1111-111111111111",
            "포도", 2, kickedAt);

        publisher.publishParticipantKicked(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PARTICIPANT_KICKED);

        RelayRoomParticipantKickedEventResponse data = (RelayRoomParticipantKickedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.kickedUserUuid()).isEqualTo(response.kickedUserUuid());
        assertThat(data.kickedNickname()).isEqualTo("포도");
        assertThat(data.participantCount()).isEqualTo(2);
        assertThat(data.kickedAt()).isEqualTo(kickedAt);
    }

    @Test
    void publishParticipantLeftSendsParticipantLeftEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime leftAt = LocalDateTime.now().minusSeconds(1);
        RelayRoomLeaveResponse response = new RelayRoomLeaveResponse(ROOM_CODE, "11111111-1111-1111-1111-111111111111",
            "포도", 2, false, null, null, false, RelayRoomStatus.WAITING, leftAt);

        publisher.publishParticipantLeft(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PARTICIPANT_LEFT);

        RelayRoomParticipantLeftEventResponse data = (RelayRoomParticipantLeftEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.leftUserUuid()).isEqualTo(response.leftUserUuid());
        assertThat(data.leftNickname()).isEqualTo("포도");
        assertThat(data.participantCount()).isEqualTo(2);
        assertThat(data.leftAt()).isEqualTo(leftAt);
    }

    @Test
    void publishHostChangedSendsHostChangedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime leftAt = LocalDateTime.now().minusSeconds(1);
        RelayRoomLeaveResponse response = new RelayRoomLeaveResponse(ROOM_CODE, "11111111-1111-1111-1111-111111111111",
            "망고", 2, true, "22222222-2222-2222-2222-222222222222", "포도", false, RelayRoomStatus.WAITING, leftAt);

        publisher.publishHostChanged(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.HOST_CHANGED);

        RelayRoomHostChangedEventResponse data = (RelayRoomHostChangedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.previousHostUserUuid()).isEqualTo(response.leftUserUuid());
        assertThat(data.newHostUserUuid()).isEqualTo(response.newHostUserUuid());
        assertThat(data.newHostNickname()).isEqualTo("포도");
        assertThat(data.changedAt()).isEqualTo(leftAt);
    }

    @Test
    void publishHostChangedAfterDropSendsHostChangedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime changedAt = LocalDateTime.now().minusSeconds(1);
        RelayHostChangeResult result = new RelayHostChangeResult(ROOM_CODE, "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222", "포도", changedAt);

        publisher.publishHostChanged(result);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.HOST_CHANGED);

        RelayRoomHostChangedEventResponse data = (RelayRoomHostChangedEventResponse) event.data();
        assertThat(data.previousHostUserUuid()).isEqualTo(result.previousHostUserUuid());
        assertThat(data.newHostUserUuid()).isEqualTo(result.newHostUserUuid());
        assertThat(data.newHostNickname()).isEqualTo("포도");
        assertThat(data.changedAt()).isEqualTo(changedAt);
    }

    @Test
    void publishParticipantDroppedSendsParticipantDroppedEventToRoomTopic() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        LocalDateTime disconnectedAt = LocalDateTime.now().minusSeconds(11);
        LocalDateTime droppedAt = LocalDateTime.now().minusSeconds(1);
        RelayDroppedParticipantResult result = new RelayDroppedParticipantResult(ROOM_CODE,
            "11111111-1111-1111-1111-111111111111", "망고", disconnectedAt, droppedAt);

        publisher.publishParticipantDropped(result);

        verify(messagingTemplate).convertAndSend(eq("/topic/relay/rooms/" + ROOM_CODE), eventCaptor.capture());
        RelayRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(RelayRoomEventType.PARTICIPANT_DROPPED);

        RelayRoomParticipantDroppedEventResponse data = (RelayRoomParticipantDroppedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.userUuid()).isEqualTo(result.userUuid());
        assertThat(data.nickname()).isEqualTo("망고");
        assertThat(data.disconnectedAt()).isEqualTo(disconnectedAt);
        assertThat(data.droppedAt()).isEqualTo(droppedAt);
    }

    /**
     * 개인 큐 이벤트는 user destination resolver가 특정 sessionId로 해석할 수 있도록 simpSessionId
     * 헤더를 함께 보냅니다.
     */
    @Test
    void publishPongSendsSessionIdHeaderForUserQueueRouting() {
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        publisher.publishPong(SESSION_ID, ROOM_CODE);

        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/relay/rooms/" + ROOM_CODE),
            any(RelayRoomEventResponse.class), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
    }

    @Test
    void publishKickedFromRoomSendsPersonalQueueEventAndClosesActiveSession() {
        ArgumentCaptor<RelayRoomEventResponse> eventCaptor = ArgumentCaptor.forClass(RelayRoomEventResponse.class);
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        String kickedUserUuid = "11111111-1111-1111-1111-111111111111";
        given(webSocketSessionRegistry.findCurrentSession(ROOM_CODE, kickedUserUuid))
            .willReturn(Optional.of(new ActiveWebSocketSession(ROOM_CODE, kickedUserUuid, SESSION_ID)));

        publisher.publishKickedFromRoom(ROOM_CODE, kickedUserUuid);

        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/relay/rooms/" + ROOM_CODE),
            eventCaptor.capture(), headersCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(RelayRoomEventType.KICKED_FROM_ROOM);
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
        verify(webSocketSessionRegistry).removeStaleSession(SESSION_ID);
        verify(webSocketSessionRegistry).closeWebSocketSession(eq(SESSION_ID), any());
    }

    @Test
    void closeLeftRoomSessionClosesActiveSessionWithoutPersonalEvent() {
        String leftUserUuid = "11111111-1111-1111-1111-111111111111";
        given(webSocketSessionRegistry.findCurrentSession(ROOM_CODE, leftUserUuid))
            .willReturn(Optional.of(new ActiveWebSocketSession(ROOM_CODE, leftUserUuid, SESSION_ID)));

        publisher.closeLeftRoomSession(ROOM_CODE, leftUserUuid);

        verify(webSocketSessionRegistry).removeStaleSession(SESSION_ID);
        verify(webSocketSessionRegistry).closeWebSocketSession(eq(SESSION_ID), any());
    }

    private RelayRoomStateResponse roomStateResponse(int timeLimitSeconds) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);
        RelayRoomParticipantResponse participant = new RelayRoomParticipantResponse(USER_UUID, "망고", true, 0, true);

        return new RelayRoomStateResponse(ROOM_CODE, RelayRoomStatus.WAITING, USER_UUID, timeLimitSeconds, 2, 6, 1,
            null, List.of(participant), null, createdAt, createdAt.plusSeconds(1));
    }

    private RelayRoomStateResponse startedRoomStateResponse() {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime startedAt = createdAt.plusSeconds(10);

        return new RelayRoomStateResponse(ROOM_CODE, RelayRoomStatus.PLAYING, "550e8400-e29b-41d4-a716-446655440000",
            45, 2, 6, 2, RelayDrawingPart.FACE, 6, startedAt, startedAt.plusSeconds(45), startedAt, List.of(), null,
            createdAt, startedAt);
    }

    private RelayRoomSubmissionResponse advancedSubmissionResponse() {
        LocalDateTime submittedAt = LocalDateTime.now().minusSeconds(1);
        LocalDateTime nextPartStartedAt = submittedAt;

        return new RelayRoomSubmissionResponse(ROOM_CODE, 0, RelayDrawingPart.FACE, RelayAssignmentStatus.SUBMITTED,
            "relay/tmp/QUDNKQ/0/face.png", "relay/tmp/QUDNKQ/0/face-hint.png", submittedAt, false, true, 2, 2, true,
            RelayDrawingPart.BODY, nextPartStartedAt, nextPartStartedAt.plusSeconds(45), false, RelayRoomStatus.PLAYING,
            "550e8400-e29b-41d4-a716-446655440000", "Mango");
    }

    private RelayRoomSubmissionResponse allPartsCompletedSubmissionResponse() {
        LocalDateTime submittedAt = LocalDateTime.now().minusSeconds(1);

        return new RelayRoomSubmissionResponse(ROOM_CODE, 0, RelayDrawingPart.LEGS, RelayAssignmentStatus.SUBMITTED,
            "relay/tmp/QUDNKQ/0/legs.png", null, submittedAt, false, true, 2, 2, true, null, null, null, true,
            RelayRoomStatus.FINALIZING, "550e8400-e29b-41d4-a716-446655440000", "Mango");
    }
}
