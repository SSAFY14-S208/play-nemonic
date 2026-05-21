package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.cleanup.RelayOldTempCleanupResult;
import com.nemonicworld.relay.service.cleanup.RelayRoomTempCleanupResult;
import com.nemonicworld.relay.service.cleanup.RelayRoomTempCleanupService;
import com.nemonicworld.relay.service.cleanup.RelayTempCleanupProcessResult;
import com.nemonicworld.relay.service.cleanup.RelayTempFileStorage;
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
class RelayRoomTempCleanupServiceTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final String SECOND_ROOM_CODE = "CD4L8M";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 16, 0).truncatedTo(ChronoUnit.SECONDS);
    private static final int SCAN_LIMIT = 10;
    private static final long MARKER_TTL_HOURS = 24L;
    private static final long LOCK_TTL_SECONDS = 60L;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelayTempFileStorage relayTempFileStorage;

    private RelayRoomTempCleanupService relayRoomTempCleanupService;

    @BeforeEach
    void setUp() {
        relayRoomTempCleanupService = new RelayRoomTempCleanupService(relayRoomRepository, relayTempFileStorage,
            SCAN_LIMIT, MARKER_TTL_HOURS, LOCK_TTL_SECONDS, 24, 1000);
    }

    @Test
    void cleanupClosedRoomDeletesAssignmentObjectAndHintKeysAndMarksCleanup() {
        RelayRoomState roomState = closedRoom(ROOM_CODE,
            assignment("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png"));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTempCleanupResult result = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        assertThat(result.cleaned()).isTrue();
        assertThat(result.deletedObjectCount()).isEqualTo(2);
        verify(relayTempFileStorage)
            .deleteObjects(List.of("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png"));
        verify(relayRoomRepository).markTempCleanup(ROOM_CODE, NOW, Duration.ofHours(MARKER_TTL_HOURS));
        verify(relayRoomRepository).releaseTempCleanupLock(ROOM_CODE);
    }

    @Test
    void cleanupClosedRoomIgnoresNullBlankDuplicateAndUnsafeKeys() {
        RelayRoomState roomState = closedRoom(ROOM_CODE, assignment(" relay/tmp/AB3K9Q/0/face.png ", null),
            assignment("relay/tmp/AB3K9Q/0/face.png", " "),
            assignment("relay/tmp/OTHER1/0/face.png", "relay/results/artifact-id/original.png"));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTempCleanupResult result = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        assertThat(result.cleaned()).isTrue();
        assertThat(result.deletedObjectCount()).isEqualTo(1);
        verify(relayTempFileStorage).deleteObjects(List.of("relay/tmp/AB3K9Q/0/face.png"));
        verify(relayRoomRepository).markTempCleanup(ROOM_CODE, NOW, Duration.ofHours(MARKER_TTL_HOURS));
    }

    @Test
    void cleanupClosedRoomMarksCleanupWhenThereAreNoTargets() {
        RelayRoomState roomState = closedRoom(ROOM_CODE, assignment(null, null));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTempCleanupResult result = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        assertThat(result.cleaned()).isTrue();
        assertThat(result.deletedObjectCount()).isZero();
        verifyNoInteractions(relayTempFileStorage);
        verify(relayRoomRepository).markTempCleanup(ROOM_CODE, NOW, Duration.ofHours(MARKER_TTL_HOURS));
    }

    @Test
    void cleanupClosedRoomDoesNothingWhenCleanupIsAlreadyMarked() {
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(true);

        RelayRoomTempCleanupResult result = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        assertThat(result.cleaned()).isFalse();
        verify(relayRoomRepository, never()).acquireTempCleanupLock(any(), any());
        verifyNoInteractions(relayTempFileStorage);
    }

    @Test
    void cleanupClosedRoomDoesNothingWhenCleanupLockIsNotAcquired() {
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(false);

        RelayRoomTempCleanupResult result = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        assertThat(result.cleaned()).isFalse();
        verify(relayRoomRepository, never()).findByRoomCode(any());
        verifyNoInteractions(relayTempFileStorage);
    }

    @ParameterizedTest
    @EnumSource(value = RelayRoomStatus.class, names = {"WAITING", "PLAYING", "FINALIZING", "FINISHED"})
    void cleanupClosedRoomDoesNotDeleteNonClosedRoom(RelayRoomStatus roomStatus) {
        RelayRoomState roomState = room(ROOM_CODE, roomStatus,
            assignment("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png"));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTempCleanupResult result = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        assertThat(result.cleaned()).isFalse();
        verifyNoInteractions(relayTempFileStorage);
        verify(relayRoomRepository, never()).markTempCleanup(any(), any(), any());
        verify(relayRoomRepository).releaseTempCleanupLock(ROOM_CODE);
    }

    @Test
    void cleanupClosedRoomDoesNotMarkCleanupWhenDeleteFails() {
        RelayRoomState roomState = closedRoom(ROOM_CODE, assignment("relay/tmp/AB3K9Q/0/face.png", null));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));
        doThrow(new FileStorageException("storage error", new RuntimeException("boom"))).when(relayTempFileStorage)
            .deleteObjects(anyList());

        assertThatThrownBy(() -> relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW))
            .isInstanceOf(FileStorageException.class);

        verify(relayRoomRepository, never()).markTempCleanup(any(), any(), any());
        verify(relayRoomRepository).releaseTempCleanupLock(ROOM_CODE);
    }

    @Test
    void cleanupClosedRoomsContinuesAfterOneRoomFails() {
        RelayRoomState firstRoom = closedRoom(ROOM_CODE, assignment("relay/tmp/AB3K9Q/0/face.png", null));
        RelayRoomState secondRoom = closedRoom(SECOND_ROOM_CODE, assignment("relay/tmp/CD4L8M/0/face.png", null));
        given(relayRoomRepository.findClosedRooms(SCAN_LIMIT)).willReturn(List.of(firstRoom, secondRoom));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.isTempCleanupMarked(SECOND_ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.acquireTempCleanupLock(SECOND_ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(firstRoom));
        given(relayRoomRepository.findByRoomCode(SECOND_ROOM_CODE)).willReturn(Optional.of(secondRoom));
        doThrow(new FileStorageException("storage error", new RuntimeException("boom"))).doNothing()
            .when(relayTempFileStorage).deleteObjects(anyList());

        RelayTempCleanupProcessResult result = relayRoomTempCleanupService.cleanupClosedRooms(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(2);
        assertThat(result.cleanedRoomCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(1);
        verify(relayRoomRepository, never()).markTempCleanup(eq(ROOM_CODE), any(), any());
        verify(relayRoomRepository).markTempCleanup(SECOND_ROOM_CODE, NOW, Duration.ofHours(MARKER_TTL_HOURS));
    }

    @Test
    void cleanupClosedRoomsScansClosedRoomsAndCountsDeletedObjects() {
        RelayRoomState roomState = closedRoom(ROOM_CODE,
            assignment("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png"));
        given(relayRoomRepository.findClosedRooms(SCAN_LIMIT)).willReturn(List.of(roomState));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayTempCleanupProcessResult result = relayRoomTempCleanupService.cleanupClosedRooms(NOW);

        assertThat(result.scannedRoomCount()).isEqualTo(1);
        assertThat(result.cleanedRoomCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(2);
        verify(relayRoomRepository).findClosedRooms(SCAN_LIMIT);
    }

    @Test
    void cleanupClosedRoomCanRunTwiceSafelyAfterMarkerIsCreated() {
        RelayRoomState roomState = closedRoom(ROOM_CODE, assignment("relay/tmp/AB3K9Q/0/face.png", null));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false, false, true);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        RelayRoomTempCleanupResult firstResult = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);
        RelayRoomTempCleanupResult secondResult = relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE,
            NOW.plusSeconds(1));

        assertThat(firstResult.cleaned()).isTrue();
        assertThat(secondResult.cleaned()).isFalse();
        verify(relayTempFileStorage).deleteObjects(List.of("relay/tmp/AB3K9Q/0/face.png"));
    }

    @Test
    void cleanupClosedRoomReleasesLockEvenWhenMarkerIsWritten() {
        RelayRoomState roomState = closedRoom(ROOM_CODE, assignment("relay/tmp/AB3K9Q/0/face.png", null));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        verify(relayRoomRepository).releaseTempCleanupLock(ROOM_CODE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupClosedRoomPassesOnlyRelayTmpRoomPrefixObjects() {
        RelayRoomState roomState = closedRoom(ROOM_CODE,
            assignment("relay/tmp/AB3K9Q/0/body.png", "relay/tmp/AB3K9Q/0/body-hint.png"),
            assignment("relay/tmp/AB3K9Q-old/0/body.png", "relay/tmp/AB3K9Q"),
            assignment("relay/results/result-id/original.png", "uploads/relay/tmp/AB3K9Q/file.png"));
        given(relayRoomRepository.isTempCleanupMarked(ROOM_CODE)).willReturn(false);
        given(relayRoomRepository.acquireTempCleanupLock(ROOM_CODE, Duration.ofSeconds(LOCK_TTL_SECONDS)))
            .willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(roomState));

        relayRoomTempCleanupService.cleanupClosedRoom(ROOM_CODE, NOW);

        ArgumentCaptor<List<String>> objectKeysCaptor = ArgumentCaptor.forClass(List.class);
        verify(relayTempFileStorage).deleteObjects(objectKeysCaptor.capture());
        assertThat(objectKeysCaptor.getValue()).containsExactly("relay/tmp/AB3K9Q/0/body.png",
            "relay/tmp/AB3K9Q/0/body-hint.png");
    }

    @Test
    void cleanupOldTempObjectsDeletesObjectsOlderThanThreshold() {
        LocalDateTime cutoff = NOW.minusHours(24);
        List<String> oldObjectKeys = List.of("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/CD4L8M/1/body.png");
        given(relayTempFileStorage.findOldTempObjectKeys(cutoff, 1000)).willReturn(oldObjectKeys);
        given(relayRoomRepository.findByRoomCode(anyString())).willReturn(Optional.empty());

        RelayOldTempCleanupResult result = relayRoomTempCleanupService.cleanupOldTempObjects(NOW);

        assertThat(result.scannedObjectCount()).isEqualTo(2);
        assertThat(result.deletedObjectCount()).isEqualTo(2);
        verify(relayTempFileStorage).deleteObjects(oldObjectKeys);
    }

    @Test
    void cleanupOldTempObjectsDoesNotDeleteWhenThereAreNoOldObjects() {
        given(relayTempFileStorage.findOldTempObjectKeys(NOW.minusHours(24), 1000)).willReturn(List.of());

        RelayOldTempCleanupResult result = relayRoomTempCleanupService.cleanupOldTempObjects(NOW);

        assertThat(result.scannedObjectCount()).isZero();
        assertThat(result.deletedObjectCount()).isZero();
        verify(relayTempFileStorage, never()).deleteObjects(anyList());
    }

    @Test
    void cleanupOldTempObjectsLogsAndRetriesLaterWhenListFails() {
        given(relayTempFileStorage.findOldTempObjectKeys(NOW.minusHours(24), 1000))
            .willThrow(new FileStorageException("storage error", new RuntimeException("boom")));

        RelayOldTempCleanupResult result = relayRoomTempCleanupService.cleanupOldTempObjects(NOW);

        assertThat(result.scannedObjectCount()).isZero();
        assertThat(result.deletedObjectCount()).isZero();
        verify(relayTempFileStorage, never()).deleteObjects(anyList());
    }

    @Test
    void cleanupOldTempObjectsDoesNotMarkSuccessWhenDeleteFails() {
        List<String> oldObjectKeys = List.of("relay/tmp/AB3K9Q/0/face.png");
        given(relayTempFileStorage.findOldTempObjectKeys(NOW.minusHours(24), 1000)).willReturn(oldObjectKeys);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());
        doThrow(new FileStorageException("storage error", new RuntimeException("boom"))).when(relayTempFileStorage)
            .deleteObjects(oldObjectKeys);

        RelayOldTempCleanupResult result = relayRoomTempCleanupService.cleanupOldTempObjects(NOW);

        assertThat(result.scannedObjectCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isZero();
    }

    @Test
    void cleanupOldTempObjectsSkipsActiveRoomObjects() {
        List<String> oldObjectKeys = List.of("relay/tmp/AB3K9Q/0/face.png");
        given(relayTempFileStorage.findOldTempObjectKeys(NOW.minusHours(24), 1000)).willReturn(oldObjectKeys);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(
            Optional.of(room(ROOM_CODE, RelayRoomStatus.PLAYING, assignment("relay/tmp/AB3K9Q/0/face.png", null))));

        RelayOldTempCleanupResult result = relayRoomTempCleanupService.cleanupOldTempObjects(NOW);

        assertThat(result.scannedObjectCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isZero();
        verify(relayTempFileStorage, never()).deleteObjects(anyList());
    }

    private RelayRoomState closedRoom(String roomCode, RelayRoomAssignment... assignments) {
        return room(roomCode, RelayRoomStatus.CLOSED, assignments);
    }

    private RelayRoomState room(String roomCode, RelayRoomStatus status, RelayRoomAssignment... assignments) {
        LocalDateTime createdAt = NOW.minusMinutes(30);
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = new RelayRoomParticipant(hostUuid.toString(), "Mango", true, 0, true, null,
            createdAt);

        return new RelayRoomState(roomCode, status, hostUuid.toString(), 45, 2, 6, RelayDrawingPart.LEGS, List.of(host),
            List.of(assignments), NOW.minusMinutes(10), NOW.minusMinutes(9), NOW.minusMinutes(20), createdAt,
            NOW.minusMinutes(1));
    }

    private RelayRoomAssignment assignment(String objectKey, String hintObjectKey) {
        return new RelayRoomAssignment(0, RelayDrawingPart.FACE, UUID.randomUUID().toString(),
            RelayAssignmentStatus.SUBMITTED, null, objectKey, hintObjectKey, false, false, NOW.minusMinutes(5));
    }
}
