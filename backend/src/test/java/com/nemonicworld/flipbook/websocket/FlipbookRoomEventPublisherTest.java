package com.nemonicworld.flipbook.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nemonicworld.flipbook.dto.response.FlipbookRoomKickResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomParticipantResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookAllRoundsCompletedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookFrameAutoSubmittedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventStateResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomEventType;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomParticipantKickedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoomResultCreatedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoundStartedEventResponse;
import com.nemonicworld.flipbook.dto.websocket.FlipbookRoundTimeUpEventResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationResult;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry.ActiveWebSocketSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;

/**
 * 플립북 WebSocket 이벤트 발행 시 방 전체 topic과 개인 session queue 라우팅 헤더를 검증합니다.
 */
class FlipbookRoomEventPublisherTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String SESSION_ID = "session-1";
    private static final String USER_UUID = "550e8400-e29b-41d4-a716-446655440000";

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final WebSocketSessionRegistry webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
    private final FlipbookRoomEventPublisher publisher = new FlipbookRoomEventPublisher(messagingTemplate,
        webSocketSessionRegistry);

    /**
     * 설정 변경 이벤트는 방 전체 topic에 SETTINGS_CHANGED 타입과 최신 방 상태를 보냅니다.
     */
    @Test
    void publishSettingsChangedSendsSettingsChangedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        FlipbookRoomStateResponse roomStateResponse = roomStateResponse(60);

        publisher.publishSettingsChanged(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.SETTINGS_CHANGED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(FlipbookRoomEventStateResponse.class);

        FlipbookRoomEventStateResponse data = (FlipbookRoomEventStateResponse) event.data();
        assertThat(data.timeLimitSeconds()).isEqualTo(60);
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.changedParticipant()).isNull();
    }

    /**
     * 게임 시작 이벤트는 방 전체 topic에 GAME_STARTED 타입과 현재 라운드 타이밍을 함께 보냅니다.
     */
    @Test
    void publishGameStartedSendsGameStartedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime now = LocalDateTime.now();
        FlipbookRoomStateResponse roomStateResponse = playingRoomStateResponse(now);

        publisher.publishGameStarted(roomStateResponse);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.GAME_STARTED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(FlipbookRoomEventStateResponse.class);

        FlipbookRoomEventStateResponse data = (FlipbookRoomEventStateResponse) event.data();
        assertThat(data.status()).isEqualTo(FlipbookRoomStatus.PLAYING);
        assertThat(data.currentRound()).isEqualTo(1);
        assertThat(data.totalRounds()).isEqualTo(4);
        assertThat(data.roundStartedAt()).isEqualTo(now);
        assertThat(data.roundDeadlineAt()).isEqualTo(now.plusSeconds(60));
        assertThat(data.gameStartedAt()).isEqualTo(now);
    }

    /**
     * 참여자 연결 이벤트는 최신 방 상태와 함께 연결된 사용자 UUID/닉네임을 보냅니다.
     */
    @Test
    void publishParticipantConnectedSendsChangedUserInfoToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        FlipbookRoomStateResponse roomStateResponse = roomStateResponse(60);

        publisher.publishParticipantConnected(roomStateResponse, USER_UUID);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.PARTICIPANT_CONNECTED);

        FlipbookRoomEventStateResponse data = (FlipbookRoomEventStateResponse) event.data();
        assertThat(data.changedParticipant()).isNotNull();
        assertThat(data.changedParticipant().userUuid()).isEqualTo(USER_UUID);
        assertThat(data.changedParticipant().nickname()).isEqualTo("망고");
        assertThat(data.changedParticipant().host()).isTrue();
        assertThat(data.changedParticipant().joinOrder()).isZero();
        assertThat(data.changedParticipant().connected()).isTrue();
    }

    /**
     * 자동 제출 이벤트는 방 전체 topic에 자동 제출 사용자와 프레임 정보를 보냅니다.
     */
    @Test
    void publishFrameAutoSubmittedSendsAutoSubmittedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime submittedAt = LocalDateTime.now().minusSeconds(1);
        FlipbookFrameAssignment assignment = new FlipbookFrameAssignment(1, 2, 3, USER_UUID,
            FlipbookFrameAssignmentStatus.AUTO_SUBMITTED, null, null, true, true, submittedAt);

        publisher.publishFrameAutoSubmitted(ROOM_CODE, "망고", assignment);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.FRAME_AUTO_SUBMITTED);

        FlipbookFrameAutoSubmittedEventResponse data = (FlipbookFrameAutoSubmittedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.userUuid()).isEqualTo(USER_UUID);
        assertThat(data.nickname()).isEqualTo("망고");
        assertThat(data.flipbookIndex()).isEqualTo(1);
        assertThat(data.frameIndex()).isEqualTo(2);
        assertThat(data.round()).isEqualTo(3);
        assertThat(data.assignmentStatus()).isEqualTo(FlipbookFrameAssignmentStatus.AUTO_SUBMITTED);
        assertThat(data.empty()).isTrue();
        assertThat(data.submittedAt()).isEqualTo(submittedAt);
    }

    /**
     * 제한 시간 종료 이벤트는 라운드와 유예 제출 마감 시각을 방 전체 topic에 보냅니다.
     */
    @Test
    void publishRoundTimeUpSendsRoundTimeUpEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime roundDeadlineAt = LocalDateTime.now().minusSeconds(1);
        LocalDateTime submitGraceDeadlineAt = roundDeadlineAt.plusSeconds(2);

        publisher.publishRoundTimeUp(ROOM_CODE, 2, roundDeadlineAt, submitGraceDeadlineAt, 2000L);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.ROUND_TIME_UP);

        FlipbookRoundTimeUpEventResponse data = (FlipbookRoundTimeUpEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.round()).isEqualTo(2);
        assertThat(data.roundDeadlineAt()).isEqualTo(roundDeadlineAt);
        assertThat(data.submitGraceDeadlineAt()).isEqualTo(submitGraceDeadlineAt);
        assertThat(data.autoSubmitGraceMillis()).isEqualTo(2000L);
    }

    /**
     * 라운드 시작 이벤트는 다음 라운드 번호와 제한 시간을 방 전체 topic에 보냅니다.
     */
    @Test
    void publishRoundStartedSendsRoundStartedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(1);
        LocalDateTime deadlineAt = startedAt.plusSeconds(45);

        publisher.publishRoundStarted(ROOM_CODE, 1, 2, startedAt, deadlineAt);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.ROUND_STARTED);

        FlipbookRoundStartedEventResponse data = (FlipbookRoundStartedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.previousRound()).isEqualTo(1);
        assertThat(data.round()).isEqualTo(2);
        assertThat(data.roundStartedAt()).isEqualTo(startedAt);
        assertThat(data.roundDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(data.timeLimitSeconds()).isEqualTo(45);
    }

    /**
     * 전체 라운드 완료 이벤트는 완료 후 방 상태와 완료 시각을 방 전체 topic에 보냅니다.
     */
    @Test
    void publishAllRoundsCompletedSendsAllRoundsCompletedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime completedAt = LocalDateTime.now().minusSeconds(1);

        publisher.publishAllRoundsCompleted(ROOM_CODE, FlipbookRoomStatus.FINALIZING, completedAt);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.ALL_ROUNDS_COMPLETED);

        FlipbookAllRoundsCompletedEventResponse data = (FlipbookAllRoundsCompletedEventResponse) event.data();
        assertThat(data.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(data.roomStatus()).isEqualTo(FlipbookRoomStatus.FINALIZING);
        assertThat(data.completedAt()).isEqualTo(completedAt);
    }

    /**
     * 최종 GIF 생성 완료 이벤트는 artifact 목록과 방 상태를 방 전체 topic에 보냅니다.
     */
    @Test
    void publishResultCreatedSendsResultCreatedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        UUID artifactId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusSeconds(1);
        FlipbookRoomFinalizationResult result = FlipbookRoomFinalizationResult
            .finished(ROOM_CODE,
                List.of(new FlipbookResultArtifactResult(artifactId, 0,
                    "flipbook/results/%s/result.gif".formatted(artifactId), "uploads/flipbook/frame-0.png",
                    "flipbook/results/%s/thumbnail.png".formatted(artifactId), "{}")),
                createdAt);

        publisher.publishResultCreated(result);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.RESULT_CREATED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);

        FlipbookRoomResultCreatedEventResponse data = (FlipbookRoomResultCreatedEventResponse) event.data();
        assertThat(data.roomStatus()).isEqualTo(FlipbookRoomStatus.FINISHED);
        assertThat(data.artifactIds()).containsExactly(artifactId);
        assertThat(data.resultCount()).isEqualTo(1);
        assertThat(data.results().get(0).gifUrl()).endsWith("/result.gif");
    }

    /**
     * 개인 큐 이벤트는 user destination resolver가 특정 sessionId로 해석할 수 있도록 simpSessionId
     * 헤더를 함께 보냅니다.
     */
    @Test
    void publishPongSendsSessionIdHeaderForUserQueueRouting() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        publisher.publishPong(SESSION_ID, ROOM_CODE);

        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/flipbook/rooms/" + ROOM_CODE),
            any(FlipbookRoomEventResponse.class), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
    }

    /**
     * 강퇴 이벤트는 방 전체 topic에 PARTICIPANT_KICKED 타입과 강퇴 대상 정보를 보냅니다.
     */
    @Test
    void publishParticipantKickedSendsParticipantKickedEventToRoomTopic() {
        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        LocalDateTime kickedAt = LocalDateTime.now().minusSeconds(1);
        FlipbookRoomKickResponse response = new FlipbookRoomKickResponse(ROOM_CODE,
            "11111111-1111-1111-1111-111111111111", "포도", 2, kickedAt);

        publisher.publishParticipantKicked(response);

        verify(messagingTemplate).convertAndSend(eq("/topic/flipbook/rooms/" + ROOM_CODE), eventCaptor.capture());
        FlipbookRoomEventResponse event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(FlipbookRoomEventType.PARTICIPANT_KICKED);
        assertThat(event.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(event.data()).isInstanceOf(FlipbookRoomParticipantKickedEventResponse.class);

        FlipbookRoomParticipantKickedEventResponse data = (FlipbookRoomParticipantKickedEventResponse) event.data();
        assertThat(data.kickedUserUuid()).isEqualTo(response.kickedUserUuid());
        assertThat(data.kickedNickname()).isEqualTo("포도");
        assertThat(data.participantCount()).isEqualTo(2);
        assertThat(data.kickedAt()).isEqualTo(kickedAt);
    }

    /**
     * 강퇴 대상자의 현재 개인 큐에 안내를 보낸 뒤 활성 WebSocket 세션을 정책 위반 status로 종료합니다.
     */
    @Test
    void publishKickedFromRoomSendsUserQueueMessageAndClosesSession() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        String kickedUserUuid = "11111111-1111-1111-1111-111111111111";
        org.mockito.BDDMockito
            .given(webSocketSessionRegistry.findCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
                ROOM_CODE, kickedUserUuid))
            .willReturn(Optional.of(new ActiveWebSocketSession(WebSocketSessionAttributes.CONNECTION_TYPE_FLIPBOOK,
                ROOM_CODE, kickedUserUuid, SESSION_ID)));

        publisher.publishKickedFromRoom(ROOM_CODE, kickedUserUuid);

        ArgumentCaptor<FlipbookRoomEventResponse> eventCaptor = ArgumentCaptor
            .forClass(FlipbookRoomEventResponse.class);
        verify(messagingTemplate).convertAndSendToUser(eq(SESSION_ID), eq("/queue/flipbook/rooms/" + ROOM_CODE),
            eventCaptor.capture(), headersCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(FlipbookRoomEventType.KICKED_FROM_ROOM);
        assertThat(headersCaptor.getValue()).containsEntry(SimpMessageHeaderAccessor.SESSION_ID_HEADER, SESSION_ID);
        org.mockito.Mockito.verify(webSocketSessionRegistry).removeStaleSession(SESSION_ID);
        org.mockito.Mockito.verify(webSocketSessionRegistry).closeWebSocketSession(SESSION_ID,
            CloseStatus.POLICY_VIOLATION.withReason("KICKED_FROM_ROOM"));
    }

    private FlipbookRoomStateResponse roomStateResponse(int timeLimitSeconds) {
        LocalDateTime now = LocalDateTime.now();

        return new FlipbookRoomStateResponse(ROOM_CODE, FlipbookRoomStatus.WAITING, USER_UUID, timeLimitSeconds, 2, 6,
            1, List.of(new FlipbookRoomParticipantResponse(USER_UUID, "망고", true, 0, true)), null, now, now);
    }

    private FlipbookRoomStateResponse playingRoomStateResponse(LocalDateTime now) {
        return new FlipbookRoomStateResponse(ROOM_CODE, FlipbookRoomStatus.PLAYING, USER_UUID, 60, 2, 6, 2, 1, 4, now,
            now.plusSeconds(60), now,
            List.of(new FlipbookRoomParticipantResponse(USER_UUID, "망고", true, 0, true),
                new FlipbookRoomParticipantResponse("11111111-1111-1111-1111-111111111111", "다현", false, 1, true)),
            null, now.minusMinutes(1), now);
    }
}
