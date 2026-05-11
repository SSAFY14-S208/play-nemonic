package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomMutationLockRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import com.nemonicworld.relay.service.disconnect.RelayDisconnectGraceProcessResult;
import com.nemonicworld.relay.service.disconnect.RelayDisconnectGraceRoomResult;
import com.nemonicworld.relay.service.disconnect.RelayHostChangeResult;
import com.nemonicworld.relay.service.disconnect.RelayRoomDisconnectGraceService;
import com.nemonicworld.relay.service.game.RelayRoomPartAdvanceService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class RelayRoomDisconnectGraceServiceTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String SECOND_ROOM_CODE = "CD4L8M";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 17, 0, 10).truncatedTo(ChronoUnit.SECONDS);
    private static final long RECONNECT_GRACE_SECONDS = 10L;

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

    private RelayRoomDisconnectGraceService relayRoomDisconnectGraceService;

    @BeforeEach
    void setUp() {
        lenient()
            .when(
                relayRoomMutationLockRepository.acquireRoomMutationLock(anyString(), anyString(), any(Duration.class)))
            .thenReturn(true);
        lenient().when(relaySubmissionLockRepository.isSubmissionLocked(anyString(), anyInt(),
            any(RelayDrawingPart.class), anyString())).thenReturn(false);
        relayRoomDisconnectGraceService = new RelayRoomDisconnectGraceService(relayRoomRepository,
            relaySubmissionLockRepository, relayRoomMutationLockRepository, new RelayRoomPartAdvanceService(),
            relayRoomEventPublisher, relayInviteMetadataSyncService, RECONNECT_GRACE_SECONDS, 5000, 100);
    }

    @Test
    void processRoomDoesNothingBeforeReconnectGraceExpires() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(9)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processRoomDropsExpiredHostAutoSubmitsCurrentPartTransfersHostAndAdvancesOnlyOnce() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid),
                submittedAssignment(1, RelayDrawingPart.FACE, participantUuid),
                pendingAssignment(0, RelayDrawingPart.BODY, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(10)),
            participant(participantUuid, "Peach", false, 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        assertThat(result.droppedParticipants()).hasSize(1);
        assertThat(result.autoSubmissions()).hasSize(1);
        assertThat(result.hostChange()).isNotNull();
        assertThat(result.advanceResult().advanced()).isTrue();
        assertThat(result.advanceResult().nextPart()).isEqualTo(RelayDrawingPart.BODY);

        RelayRoomState updatedRoomState = captureUpdatedRoomState();
        RelayRoomParticipant droppedHost = updatedRoomState.participants().get(0);
        RelayRoomParticipant newHost = updatedRoomState.participants().get(1);
        assertThat(droppedHost.dropped()).isTrue();
        assertThat(droppedHost.droppedAt()).isEqualTo(NOW);
        assertThat(droppedHost.disconnectedAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(droppedHost.host()).isFalse();
        assertThat(newHost.host()).isTrue();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(participantUuid.toString());
        assertThat(updatedRoomState.currentPart()).isEqualTo(RelayDrawingPart.BODY);

        RelayRoomAssignment autoSubmittedFace = updatedRoomState.assignments().get(0);
        RelayRoomAssignment futureBody = updatedRoomState.assignments().get(2);
        assertAutoSubmitted(autoSubmittedFace, hostUuid, RelayDrawingPart.FACE);
        assertThat(futureBody.status()).isEqualTo(RelayAssignmentStatus.PENDING);
        assertThat(futureBody.part()).isEqualTo(RelayDrawingPart.BODY);

        verify(relayRoomEventPublisher).publishParticipantDropped(result.droppedParticipants().get(0));
        verify(relayRoomEventPublisher).publishHostChanged(result.hostChange());
        verify(relayRoomEventPublisher).publishPartAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(RelayRoomAssignment.class));
        verify(relayRoomEventPublisher).publishPartStarted(eq(ROOM_CODE), eq(RelayDrawingPart.FACE),
            eq(RelayDrawingPart.BODY), eq(NOW), eq(NOW.plusSeconds(45)));
    }

    @Test
    void processRoomKeepsAlreadySubmittedCurrentAssignment() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(submittedAssignment(0, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        assertThat(result.autoSubmissions()).isEmpty();
        RelayRoomAssignment assignment = captureUpdatedRoomState().assignments().get(0);
        assertThat(assignment.status()).isEqualTo(RelayAssignmentStatus.SUBMITTED);
        assertThat(assignment.objectKey()).isEqualTo("relay/tmp/AB3K9Q/0/face.png");
        verify(relayRoomEventPublisher).publishParticipantDropped(result.droppedParticipants().get(0));
        verify(relayRoomEventPublisher, never()).publishPartAutoSubmitted(any(), any(), any());
    }

    @Test
    void processRoomAutoSubmitsExistingDroppedParticipantWithoutDuplicateDropEvent() {
        UUID hostUuid = UUID.randomUUID();
        UUID droppedUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.BODY,
            List.of(pendingAssignment(0, RelayDrawingPart.BODY, droppedUuid),
                pendingAssignment(0, RelayDrawingPart.LEGS, droppedUuid)),
            participant(hostUuid, "Mango", true, 0),
            droppedParticipant(droppedUuid, "Peach", false, 1, NOW.minusSeconds(20), NOW.minusSeconds(10)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.droppedParticipants()).isEmpty();
        assertThat(result.autoSubmissions()).hasSize(1);
        RelayRoomState updatedRoomState = captureUpdatedRoomState();
        assertAutoSubmitted(updatedRoomState.assignments().get(0), droppedUuid, RelayDrawingPart.BODY);
        assertThat(updatedRoomState.assignments().get(1).status()).isEqualTo(RelayAssignmentStatus.PENDING);
        verify(relayRoomEventPublisher, never()).publishParticipantDropped(any());
        verify(relayRoomEventPublisher).publishPartAutoSubmitted(eq(ROOM_CODE), eq("Peach"),
            any(RelayRoomAssignment.class));
    }

    @Test
    void processRoomSkipsAutoSubmitWhenDroppedAssignmentSubmissionIsLocked() {
        UUID hostUuid = UUID.randomUUID();
        UUID participantUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(submittedAssignment(0, RelayDrawingPart.FACE, hostUuid),
                pendingAssignment(1, RelayDrawingPart.FACE, participantUuid)),
            participant(hostUuid, "Mango", true, 0),
            participant(participantUuid, "Peach", false, 1, false, NOW.minusSeconds(10)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relaySubmissionLockRepository.isSubmissionLocked(ROOM_CODE, 1, RelayDrawingPart.FACE,
            participantUuid.toString())).willReturn(true);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        assertThat(result.droppedParticipants()).hasSize(1);
        assertThat(result.autoSubmissions()).isEmpty();
        RelayRoomState updatedRoomState = captureUpdatedRoomState();
        assertThat(updatedRoomState.participants().get(1).dropped()).isTrue();
        assertThat(updatedRoomState.assignments().get(1).status()).isEqualTo(RelayAssignmentStatus.PENDING);
        verify(relayRoomEventPublisher).publishParticipantDropped(result.droppedParticipants().get(0));
        verify(relayRoomEventPublisher, never()).publishPartAutoSubmitted(any(), any(), any());
        verify(relayRoomEventPublisher, never()).publishPartStarted(any(), any(), any(), any(), any());
    }

    @Test
    void processRoomTransfersExistingDroppedHostWhenConnectedCandidateExists() {
        UUID droppedHostUuid = UUID.randomUUID();
        UUID candidateUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(submittedAssignment(0, RelayDrawingPart.FACE, droppedHostUuid)),
            droppedParticipant(droppedHostUuid, "Mango", true, 0, NOW.minusSeconds(20), NOW.minusSeconds(10)),
            participant(candidateUuid, "Peach", false, 1));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        RelayRoomState updatedRoomState = captureUpdatedRoomState();
        assertThat(result.droppedParticipants()).isEmpty();
        assertThat(result.autoSubmissions()).isEmpty();
        assertThat(result.hostChange()).isNotNull();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(candidateUuid.toString());
        assertThat(updatedRoomState.participants()).extracting(RelayRoomParticipant::host).containsExactly(false, true);
        verify(relayRoomEventPublisher, never()).publishParticipantDropped(any());
        verify(relayRoomEventPublisher).publishHostChanged(result.hostChange());
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"WAITING", "FINALIZING", "FINISHED", "CLOSED"})
    void processRoomIgnoresNonPlayingRooms(RelayRoomStatus status) {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = room(status, RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processRoomKeepsHostWhenNoConnectedCandidateExists() {
        UUID hostUuid = UUID.randomUUID();
        UUID disconnectedUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)),
            participant(disconnectedUuid, "Peach", false, 1, false, NOW.minusSeconds(3)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        RelayRoomState updatedRoomState = captureUpdatedRoomState();
        assertThat(result.hostChange()).isNull();
        assertThat(updatedRoomState.hostUserUuid()).isEqualTo(hostUuid.toString());
        assertThat(updatedRoomState.participants().get(0).host()).isTrue();
        verify(relayRoomEventPublisher, never()).publishHostChanged(any(RelayHostChangeResult.class));
    }

    @Test
    void processRoomRetriesCasConflictAndPublishesEventsOnce() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false, true);

        RelayDisconnectGraceRoomResult result = relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW);

        assertThat(result.processed()).isTrue();
        verify(relayRoomRepository, times(2)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        verify(relayRoomEventPublisher, times(1)).publishParticipantDropped(any());
        verify(relayRoomEventPublisher, times(1)).publishPartAutoSubmitted(eq(ROOM_CODE), eq("Mango"),
            any(RelayRoomAssignment.class));
    }

    @Test
    void processRoomDoesNotPublishEventsWhenCasConflictsKeepHappening() {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomState roomState = playingRoom(RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, hostUuid)),
            participant(hostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> relayRoomDisconnectGraceService.processRoom(ROOM_CODE, NOW))
            .isInstanceOf(ConflictException.class).hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verify(relayRoomRepository, times(3)).saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class));
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processDroppedParticipantsContinuesAfterRoomFailure() {
        UUID firstHostUuid = UUID.randomUUID();
        UUID secondHostUuid = UUID.randomUUID();
        RelayRoomState firstRoom = playingRoom(ROOM_CODE, RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, firstHostUuid)),
            participant(firstHostUuid, "Mango", true, 0, false, NOW.minusSeconds(11)));
        RelayRoomState secondRoom = playingRoom(SECOND_ROOM_CODE, RelayDrawingPart.FACE,
            List.of(pendingAssignment(0, RelayDrawingPart.FACE, secondHostUuid)),
            participant(secondHostUuid, "Peach", true, 0, false, NOW.minusSeconds(11)));
        given(relayRoomRepository.findPlayingRoomsForDisconnectGrace(eq(NOW.minusSeconds(10)), eq(100)))
            .willReturn(List.of(firstRoom, secondRoom));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willThrow(new InternalServerException("boom"));
        given(relayRoomRepository.findByRoomCode(SECOND_ROOM_CODE)).willReturn(Optional.of(secondRoom));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayDisconnectGraceProcessResult result = relayRoomDisconnectGraceService.processDroppedParticipants(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(2);
        assertThat(result.processedRoomCount()).isEqualTo(1);
        assertThat(result.droppedParticipantCount()).isEqualTo(1);
        assertThat(result.autoSubmittedCount()).isEqualTo(1);
    }

    private RelayRoomState captureUpdatedRoomState() {
        ArgumentCaptor<RelayRoomState> updatedStateCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedStateCaptor.capture());

        return updatedStateCaptor.getValue();
    }

    private void assertAutoSubmitted(RelayRoomAssignment assignment, UUID assignedUserUuid, RelayDrawingPart part) {
        assertThat(assignment.assignedUserUuid()).isEqualTo(assignedUserUuid.toString());
        assertThat(assignment.part()).isEqualTo(part);
        assertThat(assignment.status()).isEqualTo(RelayAssignmentStatus.AUTO_SUBMITTED);
        assertThat(assignment.empty()).isTrue();
        assertThat(assignment.autoSubmitted()).isTrue();
        assertThat(assignment.objectKey()).isNull();
        assertThat(assignment.hintObjectKey()).isNull();
        assertThat(assignment.submittedAt()).isEqualTo(NOW);
    }

    private RelayRoomState playingRoom(RelayDrawingPart currentPart, List<RelayRoomAssignment> assignments,
        RelayRoomParticipant... participants) {
        return playingRoom(ROOM_CODE, currentPart, assignments, participants);
    }

    private RelayRoomState playingRoom(String roomCode, RelayDrawingPart currentPart,
        List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        return room(roomCode, RelayRoomStatus.PLAYING, currentPart, assignments, participants);
    }

    private RelayRoomState room(RelayRoomStatus status, RelayDrawingPart currentPart,
        List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        return room(ROOM_CODE, status, currentPart, assignments, participants);
    }

    private RelayRoomState room(String roomCode, RelayRoomStatus status, RelayDrawingPart currentPart,
        List<RelayRoomAssignment> assignments, RelayRoomParticipant... participants) {
        LocalDateTime createdAt = NOW.minusMinutes(10);
        LocalDateTime startedAt = NOW.minusSeconds(30);

        return new RelayRoomState(roomCode, status, participants[0].userUuid(), 45, 2, 6, currentPart,
            List.of(participants), assignments, startedAt, startedAt.plusSeconds(45), startedAt, createdAt,
            startedAt.plusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder) {
        return participant(userUuid, nickname, host, joinOrder, true, null);
    }

    private RelayRoomParticipant participant(UUID userUuid, String nickname, boolean host, int joinOrder,
        boolean connected, LocalDateTime disconnectedAt) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, connected, disconnectedAt,
            NOW.minusMinutes(5));
    }

    private RelayRoomParticipant droppedParticipant(UUID userUuid, String nickname, boolean host, int joinOrder,
        LocalDateTime disconnectedAt, LocalDateTime droppedAt) {
        return new RelayRoomParticipant(userUuid.toString(), nickname, host, joinOrder, false, disconnectedAt,
            NOW.minusMinutes(5), true, droppedAt);
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
