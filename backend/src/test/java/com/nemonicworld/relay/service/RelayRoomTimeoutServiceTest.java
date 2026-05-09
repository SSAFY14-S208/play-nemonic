package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.timeout.RelayRoomTimeoutResult;
import com.nemonicworld.relay.service.timeout.RelayRoomTimeoutService;
import com.nemonicworld.relay.service.timeout.RelayTimeoutProcessResult;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelayRoomTimeoutServiceTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 5, 14, 1, 16);
    private static final long AUTO_SUBMIT_GRACE_MS = 2_000L;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelaySubmissionLockRepository relaySubmissionLockRepository;

    @Mock
    private RelayRoomMutationLockRepository relayRoomMutationLockRepository;

    @Mock
    private RelayRoomEventPublisher relayRoomEventPublisher;

    @Mock
    private RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    private RelayRoomTimeoutService relayRoomTimeoutService;

    @BeforeEach
    void setUp() {
        given(relaySubmissionLockRepository.isSubmissionLocked(anyString(), anyInt(), any(RelayDrawingPart.class),
            anyString())).willReturn(false);
        given(relayRoomMutationLockRepository.acquireRoomMutationLock(anyString(), anyString(), any(Duration.class)))
            .willReturn(true);
        relayRoomTimeoutService = new RelayRoomTimeoutService(relayRoomRepository, relaySubmissionLockRepository,
            relayRoomMutationLockRepository, new RelayRoomPartAdvanceService(), relayRoomEventPublisher,
            relayInviteMetadataSyncService, 100, AUTO_SUBMIT_GRACE_MS, 5000L);
    }

    @Test
    void processExpiredRoomDoesNothingBeforeDeadline() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(10), NOW.plusSeconds(1),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomDoesNothingDuringAutoSubmitGracePeriod() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), NOW.minusSeconds(1),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomDoesNothingWhenRoomMutationLockIsBusy() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomMutationLockRepository.acquireRoomMutationLock(anyString(), anyString(), any(Duration.class)))
            .willReturn(false);

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        assertThat(result.autoSubmissions()).isEmpty();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(relayRoomMutationLockRepository, never()).releaseRoomMutationLock(anyString(), anyString());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomRechecksLatestRoomStateAfterRoomMutationLock() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState candidateRoomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        RelayRoomState latestRoomState = playingRoom(RelayDrawingPart.BODY, NOW, NOW.plusSeconds(45),
            List.of(submittedAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(candidateRoomState),
            Optional.of(latestRoomState));

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verify(relayRoomMutationLockRepository).releaseRoomMutationLock(eq(ROOM_CODE), anyString());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomDoesNotAutoSubmitLockedAssignment() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(
            relaySubmissionLockRepository.isSubmissionLocked(ROOM_CODE, 0, RelayDrawingPart.FACE, hostUuid.toString()))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        assertThat(result.autoSubmissions()).isEmpty();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomAutoSubmitsAfterSubmissionLockDisappears() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(
            relaySubmissionLockRepository.isSubmissionLocked(ROOM_CODE, 0, RelayDrawingPart.FACE, hostUuid.toString()))
            .willReturn(true, false);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomTimeoutResult lockedResult = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);
        RelayRoomTimeoutResult unlockedResult = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(lockedResult.processed()).isFalse();
        assertThat(unlockedResult.processed()).isTrue();
        assertThat(unlockedResult.autoSubmissions()).hasSize(1);
        assertAutoSubmitted(captureUpdatedRoomState().assignments().get(0), hostUuid);
        verify(relayRoomEventPublisher).publishPartAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(RelayRoomAssignment.class));
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"WAITING", "FINISHED", "CLOSED", "FINALIZING"})
    void processExpiredRoomIgnoresNonPlayingRooms(RelayRoomStatus roomStatus) {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = room(roomStatus, RelayDrawingPart.FACE, NOW.minusSeconds(45), NOW.minusSeconds(1),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomAutoSubmitsPendingFaceAndAdvancesToBody() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid),
                submittedAssignment(1, RelayDrawingPart.FACE, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        assertThat(result.autoSubmissions()).hasSize(1);
        assertThat(result.advanceResult().advanced()).isTrue();
        assertThat(result.advanceResult().nextPart()).isEqualTo(RelayDrawingPart.BODY);

        RelayRoomState updatedRoomState = captureUpdatedRoomState();
        RelayRoomAssignment autoSubmittedAssignment = updatedRoomState.assignments().get(0);
        RelayRoomAssignment existingSubmission = updatedRoomState.assignments().get(1);
        assertAutoSubmitted(autoSubmittedAssignment, hostUuid);
        assertThat(existingSubmission.status()).isEqualTo(RelayAssignmentStatus.SUBMITTED);
        assertThat(existingSubmission.objectKey()).isEqualTo("relay/tmp/AB3K9Q/1/face.png");
        assertThat(updatedRoomState.status()).isEqualTo(RelayRoomStatus.PLAYING);
        assertThat(updatedRoomState.currentPart()).isEqualTo(RelayDrawingPart.BODY);
        assertThat(Duration.between(updatedRoomState.partStartedAt(), updatedRoomState.partDeadlineAt()))
            .isEqualTo(Duration.ofSeconds(updatedRoomState.timeLimitSeconds()));
        verify(relayRoomEventPublisher).publishPartAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(RelayRoomAssignment.class));
        verify(relayRoomEventPublisher).publishPartStarted(eq(ROOM_CODE), eq(RelayDrawingPart.FACE),
            eq(RelayDrawingPart.BODY), eq(NOW), eq(NOW.plusSeconds(45)));
    }

    @Test
    void processExpiredRoomAutoSubmitsAllPendingAssignments() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid),
                pendingAssignment(1, RelayDrawingPart.FACE, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.autoSubmissions()).hasSize(2);
        assertThat(captureUpdatedRoomState().assignments()).allSatisfy(assignment -> {
            assertThat(assignment.status()).isEqualTo(RelayAssignmentStatus.AUTO_SUBMITTED);
            assertThat(assignment.empty()).isTrue();
            assertThat(assignment.objectKey()).isNull();
            assertThat(assignment.hintObjectKey()).isNull();
        });
        verify(relayRoomEventPublisher, times(2)).publishPartAutoSubmitted(eq(ROOM_CODE), any(),
            any(RelayRoomAssignment.class));
    }

    @Test
    void processExpiredRoomAutoSubmitsBodyAndAdvancesToLegs() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.BODY, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.BODY, hostUuid),
                submittedAssignment(1, RelayDrawingPart.BODY, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.advanceResult().nextPart()).isEqualTo(RelayDrawingPart.LEGS);
        assertThat(captureUpdatedRoomState().currentPart()).isEqualTo(RelayDrawingPart.LEGS);
        verify(relayRoomEventPublisher).publishPartStarted(eq(ROOM_CODE), eq(RelayDrawingPart.BODY),
            eq(RelayDrawingPart.LEGS), eq(NOW), eq(NOW.plusSeconds(45)));
    }

    @Test
    void processExpiredRoomAutoSubmitsLegsAndMovesToFinalizing() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.LEGS, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.LEGS, hostUuid),
                submittedAssignment(1, RelayDrawingPart.LEGS, participantUuid)),
            participant(hostUuid, "Mango", true, 0), participant(participantUuid, "Peach", false, 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.advanceResult().allPartsCompleted()).isTrue();
        assertThat(captureUpdatedRoomState().status()).isEqualTo(RelayRoomStatus.FINALIZING);
        verify(relayRoomEventPublisher).publishAllPartsCompleted(eq(ROOM_CODE), eq(RelayRoomStatus.FINALIZING),
            eq(NOW));
        verify(relayRoomEventPublisher, never()).publishPartStarted(eq(ROOM_CODE), any(), any(), any(), any());
    }

    @Test
    void processExpiredRoomRetriesRedisSaveConflict() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayRoomTimeoutResult result = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        verify(relayRoomRepository, times(2)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        verify(relayRoomEventPublisher, times(1)).publishPartAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(RelayRoomAssignment.class));
    }

    @Test
    void processExpiredRoomDoesNotDuplicateEventsAfterAlreadyAdvanced() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);
        RelayRoomTimeoutResult firstResult = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);
        RelayRoomState advancedRoomState = firstResult.advanceResult().roomState();
        clearInvocations(relayRoomRepository, relayRoomEventPublisher);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(advancedRoomState));

        RelayRoomTimeoutResult secondResult = relayRoomTimeoutService.processExpiredRoom(ROOM_CODE, NOW);

        assertThat(secondResult.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processExpiredRoomsSummarizesScannedRooms() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE, NOW.minusSeconds(45), expiredDeadline(),
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)), participant(hostUuid, "Mango", true, 0));
        RelayRoomTimeoutService limitedService = new RelayRoomTimeoutService(relayRoomRepository,
            relaySubmissionLockRepository, relayRoomMutationLockRepository, new RelayRoomPartAdvanceService(),
            relayRoomEventPublisher, relayInviteMetadataSyncService, 5, AUTO_SUBMIT_GRACE_MS, 5000L);
        given(relayRoomRepository.findExpiredPlayingRooms(any(LocalDateTime.class), eq(5)))
            .willReturn(List.of(roomState));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayTimeoutProcessResult result = limitedService.processExpiredRooms();

        assertThat(result.scannedRoomCount()).isEqualTo(1);
        assertThat(result.processedRoomCount()).isEqualTo(1);
        assertThat(result.autoSubmittedCount()).isEqualTo(1);
    }

    private LocalDateTime expiredDeadline() {
        return NOW.minus(AUTO_SUBMIT_GRACE_MS + 1_000L, ChronoUnit.MILLIS);
    }

    private RelayRoomState captureUpdatedRoomState() {
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());

        return updatedStateCaptor.getValue();
    }

    private void assertAutoSubmitted(RelayRoomAssignment assignment, UUID assignedUserUuid) {
        assertThat(assignment.assignedUserUuid()).isEqualTo(assignedUserUuid.toString());
        assertThat(assignment.status()).isEqualTo(RelayAssignmentStatus.AUTO_SUBMITTED);
        assertThat(assignment.objectKey()).isNull();
        assertThat(assignment.hintObjectKey()).isNull();
        assertThat(assignment.empty()).isTrue();
        assertThat(assignment.autoSubmitted()).isTrue();
        assertThat(assignment.submittedAt()).isEqualTo(NOW);
    }

    private RelayRoomState playingRoom(RelayDrawingPart currentPart, LocalDateTime partStartedAt,
        LocalDateTime partDeadlineAt, List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        return room(RelayRoomStatus.PLAYING, currentPart, partStartedAt, partDeadlineAt, assignments, participants);
    }

    private RelayRoomState room(RelayRoomStatus status, RelayDrawingPart currentPart, LocalDateTime partStartedAt,
        LocalDateTime partDeadlineAt, List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = NOW.minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);

        return new RelayRoomState(ROOM_CODE, status, participants[0].userUuid(), 45, 2, 6, currentPart,
            List.of(participants), assignments, partStartedAt, partDeadlineAt, createdAt, createdAt,
            createdAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, true, null,
            NOW.minusMinutes(1).truncatedTo(ChronoUnit.SECONDS));
    }

    private RelayRoomAssignment pendingAssignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid) {
        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), RelayAssignmentStatus.PENDING,
            null, null, null, false, false, null);
    }

    private RelayRoomAssignment submittedAssignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid) {
        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), RelayAssignmentStatus.SUBMITTED,
            null, "relay/tmp/AB3K9Q/%d/%s.png".formatted(canvasIndex, part.name().toLowerCase()),
            "relay/tmp/AB3K9Q/%d/%s-hint.png".formatted(canvasIndex, part.name().toLowerCase()), false, false,
            NOW.minusSeconds(5));
    }
}
