package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomMutationLockRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomTimeUpNotificationRepository;
import com.nemonicworld.flipbook.repository.FlipbookSubmissionLockRepository;
import com.nemonicworld.flipbook.service.game.FlipbookRoomRoundAdvanceService;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationTriggerService;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.timeout.FlipbookRoomTimeoutResult;
import com.nemonicworld.flipbook.service.timeout.FlipbookRoomTimeoutService;
import com.nemonicworld.flipbook.service.timeout.FlipbookTimeoutProcessResult;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomTimeoutServiceTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 8, 14, 1, 16);
    private static final long AUTO_SUBMIT_GRACE_MS = 5_000L;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookRoomTimeUpNotificationRepository flipbookRoomTimeUpNotificationRepository;

    @Mock
    private FlipbookSubmissionLockRepository flipbookSubmissionLockRepository;

    @Mock
    private FlipbookRoomMutationLockRepository flipbookRoomMutationLockRepository;

    @Mock
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    @Mock
    private FlipbookRoomFinalizationTriggerService flipbookRoomFinalizationTriggerService;

    private FlipbookRoomTimeoutService flipbookRoomTimeoutService;

    @BeforeEach
    void setUp() {
        flipbookRoomTimeoutService = new FlipbookRoomTimeoutService(flipbookRoomRepository,
            flipbookRoomTimeUpNotificationRepository, flipbookSubmissionLockRepository,
            flipbookRoomMutationLockRepository, new FlipbookRoomRoundAdvanceService(), flipbookRoomEventPublisher,
            flipbookInviteMetadataSyncService, flipbookRoomFinalizationTriggerService, 100, AUTO_SUBMIT_GRACE_MS,
            5000L);
        lenient().when(flipbookRoomMutationLockRepository.acquireRoomMutationLock(any(), any(), any(Duration.class)))
            .thenReturn(true);
    }

    @Test
    void processExpiredRoomDoesNothingBeforeDeadline() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(10), NOW.plusSeconds(1),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void processExpiredRoomDoesNothingDuringAutoSubmitGracePeriod() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(45), NOW.minusSeconds(1),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = FlipbookRoomStatus.class, names = {"WAITING", "FINALIZING", "FINISHED", "CLOSED"})
    void processExpiredRoomIgnoresNonPlayingRooms(FlipbookRoomStatus roomStatus) {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = room(roomStatus, null, null, null, List.of(),
            participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(flipbookRoomEventPublisher);
    }

    @Test
    void processExpiredRoomAutoSubmitsPendingFrameAndAdvancesToNextRound() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, 0, 1, hostUuid), submittedAssignment(1, 0, 1, participantUuid),
                pendingAssignment(0, 1, 2, participantUuid), pendingAssignment(1, 1, 2, hostUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        assertThat(result.autoSubmissions()).hasSize(1);
        assertThat(result.advanceResult().advanced()).isTrue();
        assertThat(result.advanceResult().nextRound()).isEqualTo(2);

        FlipbookRoomState updatedRoomState = captureUpdatedRoomState();
        FlipbookFrameAssignment autoSubmittedAssignment = updatedRoomState.assignments().get(0);
        FlipbookFrameAssignment existingSubmission = updatedRoomState.assignments().get(1);
        assertAutoSubmitted(autoSubmittedAssignment, hostUuid);
        assertThat(existingSubmission.status()).isEqualTo(FlipbookFrameAssignmentStatus.SUBMITTED);
        assertThat(existingSubmission.objectKey()).isEqualTo("uploads/flipbook/already.png");
        assertThat(updatedRoomState.status()).isEqualTo(FlipbookRoomStatus.PLAYING);
        assertThat(updatedRoomState.currentRound()).isEqualTo(2);
        assertThat(Duration.between(updatedRoomState.roundStartedAt(), updatedRoomState.roundDeadlineAt()))
            .isEqualTo(Duration.ofSeconds(updatedRoomState.timeLimitSeconds()));
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomState);
        verify(flipbookRoomEventPublisher).publishFrameAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(FlipbookFrameAssignment.class));
        verify(flipbookRoomEventPublisher).publishRoundStarted(eq(ROOM_CODE), eq(1), eq(2), eq(NOW),
            eq(NOW.plusSeconds(45)));
        verify(flipbookRoomMutationLockRepository).releaseRoomMutationLock(eq(ROOM_CODE), any());
    }

    @Test
    void processExpiredRoomDoesNothingWhenRoomMutationLockIsBusy() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomMutationLockRepository.acquireRoomMutationLock(any(), any(), any(Duration.class)))
            .willReturn(false);

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(flipbookRoomMutationLockRepository, never()).releaseRoomMutationLock(any(), any());
    }

    @Test
    void processExpiredRoomSkipsAssignmentWhenSubmissionLockExists() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookSubmissionLockRepository.isSubmissionLocked(ROOM_CODE, 0, 0, 1, hostUuid.toString()))
            .willReturn(true);

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(flipbookRoomEventPublisher, never()).publishFrameAutoSubmitted(any(), any(), any());
    }

    @Test
    void processExpiredRoomAutoSubmitsAllPendingAssignments() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, 0, 1, hostUuid), pendingAssignment(1, 0, 1, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.autoSubmissions()).hasSize(2);
        assertThat(captureUpdatedRoomState().assignments()).allSatisfy(assignment -> {
            assertThat(assignment.status()).isEqualTo(FlipbookFrameAssignmentStatus.AUTO_SUBMITTED);
            assertThat(assignment.fileId()).isNull();
            assertThat(assignment.objectKey()).isNull();
            assertThat(assignment.empty()).isTrue();
            assertThat(assignment.autoSubmitted()).isTrue();
        });
        verify(flipbookRoomEventPublisher, times(2)).publishFrameAutoSubmitted(eq(ROOM_CODE), any(),
            any(FlipbookFrameAssignment.class));
    }

    @Test
    void processExpiredRoomAutoSubmitsLastRoundAndFinishesRoom() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(4, 4, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, 3, 4, hostUuid), submittedAssignment(1, 3, 4, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.advanceResult().allRoundsCompleted()).isTrue();
        assertThat(captureUpdatedRoomState().status()).isEqualTo(FlipbookRoomStatus.FINALIZING);
        verify(flipbookRoomEventPublisher).publishAllRoundsCompleted(eq(ROOM_CODE), eq(FlipbookRoomStatus.FINALIZING),
            eq(NOW));
        verify(flipbookRoomFinalizationTriggerService).triggerFinalizationAsync(ROOM_CODE);
        verify(flipbookRoomEventPublisher, never()).publishRoundStarted(eq(ROOM_CODE), any(), any(), any(), any());
    }

    @Test
    void processExpiredRoomRetriesRedisSaveConflict() {
        UUID hostUuid = UUID.randomUUID();
        FlipbookRoomState roomState = playingRoom(1, 4, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(false, true);

        FlipbookRoomTimeoutResult result = flipbookRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        verify(flipbookRoomRepository, times(2)).saveIfUnchanged(any(FlipbookRoomState.class),
            any(FlipbookRoomState.class));
        verify(flipbookRoomEventPublisher, times(1)).publishFrameAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(FlipbookFrameAssignment.class));
    }

    @Test
    void processExpiredRoomsSummarizesScannedRooms() {
        UUID hostUuid = UUID.randomUUID();
        LocalDateTime currentNow = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = playingRoom(1, 4, currentNow.minusSeconds(45), currentNow.minusSeconds(5),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        FlipbookRoomTimeoutService limitedService = new FlipbookRoomTimeoutService(flipbookRoomRepository,
            flipbookRoomTimeUpNotificationRepository, flipbookSubmissionLockRepository,
            flipbookRoomMutationLockRepository, new FlipbookRoomRoundAdvanceService(), flipbookRoomEventPublisher,
            flipbookInviteMetadataSyncService, flipbookRoomFinalizationTriggerService, 5, AUTO_SUBMIT_GRACE_MS, 5000L);
        given(flipbookRoomRepository.findExpiredPlayingRooms(any(LocalDateTime.class), eq(5)))
            .willReturn(List.of(roomState));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookTimeoutProcessResult result = limitedService.processExpiredRooms();

        assertThat(result.scannedRoomCount()).isEqualTo(1);
        assertThat(result.processedRoomCount()).isEqualTo(1);
        assertThat(result.autoSubmittedCount()).isEqualTo(1);
    }

    @Test
    void processExpiredRoomsPublishesRoundTimeUpDuringAutoSubmitGracePeriod() {
        UUID hostUuid = UUID.randomUUID();
        LocalDateTime testNow = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime roundDeadlineAt = testNow.minusSeconds(1);
        FlipbookRoomState roomState = playingRoom(1, 4, testNow.minusSeconds(45), roundDeadlineAt,
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findExpiredPlayingRooms(any(LocalDateTime.class), eq(100)))
            .willReturn(List.of(roomState), List.of());
        given(flipbookRoomTimeUpNotificationRepository.markRoundTimeUpNotified(any(), any(Integer.class),
            any(LocalDateTime.class), any(Duration.class))).willReturn(true);

        FlipbookTimeoutProcessResult result = flipbookRoomTimeoutService.processExpiredRooms();

        assertThat(result.scannedRoomCount()).isZero();
        assertThat(result.processedRoomCount()).isZero();
        assertThat(result.autoSubmittedCount()).isZero();
        verify(flipbookRoomTimeUpNotificationRepository).markRoundTimeUpNotified(eq(ROOM_CODE), eq(1),
            eq(roundDeadlineAt), eq(FlipbookRoomRepository.ROOM_STATE_TTL));
        verify(flipbookRoomEventPublisher).publishRoundTimeUp(eq(ROOM_CODE), eq(1), eq(roundDeadlineAt),
            eq(roundDeadlineAt.plus(AUTO_SUBMIT_GRACE_MS, ChronoUnit.MILLIS)), eq(AUTO_SUBMIT_GRACE_MS),
            org.mockito.ArgumentMatchers.argThat(pendingSubmissions -> pendingSubmissions.size() == 1
                && pendingSubmissions.get(0).flipbookIndex() == 0 && pendingSubmissions.get(0).frameIndex() == 0
                && pendingSubmissions.get(0).userUuid().equals(hostUuid.toString())
                && pendingSubmissions.get(0).nickname().equals("Mango") && pendingSubmissions.get(0).connected()));
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    @Test
    void processExpiredRoomsDoesNotDuplicateRoundTimeUpWhenNotificationWasAlreadyMarked() {
        UUID hostUuid = UUID.randomUUID();
        LocalDateTime testNow = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        FlipbookRoomState roomState = playingRoom(1, 4, testNow.minusSeconds(45), testNow.minusSeconds(1),
            List.of(pendingAssignment(0, 0, 1, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(flipbookRoomRepository.findExpiredPlayingRooms(any(LocalDateTime.class), eq(100)))
            .willReturn(List.of(roomState), List.of());
        given(flipbookRoomTimeUpNotificationRepository.markRoundTimeUpNotified(any(), any(Integer.class),
            any(LocalDateTime.class), any(Duration.class))).willReturn(false);

        FlipbookTimeoutProcessResult result = flipbookRoomTimeoutService.processExpiredRooms();

        assertThat(result.scannedRoomCount()).isZero();
        verify(flipbookRoomEventPublisher, never()).publishRoundTimeUp(any(), any(Integer.class), any(), any(),
            anyLong(), any());
        verify(flipbookRoomRepository, never()).saveIfUnchanged(any(), any());
    }

    private LocalDateTime expiredDeadline() {
        return NOW.minus(AUTO_SUBMIT_GRACE_MS + 1_000L, ChronoUnit.MILLIS);
    }

    private FlipbookRoomState captureUpdatedRoomState() {
        ArgumentCaptor<FlipbookRoomState> updatedStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(any(FlipbookRoomState.class), updatedStateCaptor.capture());

        return updatedStateCaptor.getValue();
    }

    private void assertAutoSubmitted(FlipbookFrameAssignment assignment, UUID assignedUserUuid) {
        assertThat(assignment.assignedUserUuid()).isEqualTo(assignedUserUuid.toString());
        assertThat(assignment.status()).isEqualTo(FlipbookFrameAssignmentStatus.AUTO_SUBMITTED);
        assertThat(assignment.fileId()).isNull();
        assertThat(assignment.objectKey()).isNull();
        assertThat(assignment.empty()).isTrue();
        assertThat(assignment.autoSubmitted()).isTrue();
        assertThat(assignment.submittedAt()).isEqualTo(NOW);
    }

    private FlipbookRoomState playingRoom(int currentRound, int totalRounds, LocalDateTime roundStartedAt,
        LocalDateTime roundDeadlineAt, List<FlipbookFrameAssignment> assignments,
        FlipbookRoomParticipant... participants) {
        return room(FlipbookRoomStatus.PLAYING, currentRound, roundStartedAt, roundDeadlineAt, assignments,
            participants);
    }

    private FlipbookRoomState room(FlipbookRoomStatus status, Integer currentRound, LocalDateTime roundStartedAt,
        LocalDateTime roundDeadlineAt, List<FlipbookFrameAssignment> assignments,
        FlipbookRoomParticipant... participants) {
        LocalDateTime createdAt = NOW.minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);

        return new FlipbookRoomState(ROOM_CODE, status, participants[0].userUuid(), 45, 2, 6, currentRound, 4,
            roundStartedAt, roundDeadlineAt, createdAt.plusSeconds(1), assignments, List.of(participants), createdAt,
            createdAt.plusSeconds(1), List.of());
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            NOW.minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private FlipbookFrameAssignment pendingAssignment(int flipbookIndex, int frameIndex, int round,
        UUID assignedUserUuid) {
        return new FlipbookFrameAssignment(flipbookIndex, frameIndex, round, assignedUserUuid.toString(),
            FlipbookFrameAssignmentStatus.PENDING, null, null, false, false, null);
    }

    private FlipbookFrameAssignment submittedAssignment(int flipbookIndex, int frameIndex, int round,
        UUID assignedUserUuid) {
        return new FlipbookFrameAssignment(flipbookIndex, frameIndex, round, assignedUserUuid.toString(),
            FlipbookFrameAssignmentStatus.SUBMITTED, UUID.randomUUID().toString(), "uploads/flipbook/already.png",
            false, false, NOW.minusSeconds(5));
    }
}
