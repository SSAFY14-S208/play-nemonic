package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayFinalizationAttemptRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.cleanup.RelayObjectStorage;
import com.nemonicworld.relay.service.cleanup.RelayOrphanObjectCleanupResult;
import com.nemonicworld.relay.service.cleanup.RelayOrphanObjectCleanupService;
import com.nemonicworld.relay.service.cleanup.RelayStoredObject;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelayOrphanObjectCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 11, 22, 0).truncatedTo(ChronoUnit.SECONDS);
    private static final String ROOM_CODE = "AB3K9Q";

    @Mock
    private RelayObjectStorage relayObjectStorage;

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelayArtifactRepository relayArtifactRepository;

    @Mock
    private RelayFinalizationAttemptRepository relayFinalizationAttemptRepository;

    private RelayOrphanObjectCleanupService service;

    @BeforeEach
    void setUp() {
        service = new RelayOrphanObjectCleanupService(relayObjectStorage, relayRoomRepository, relayArtifactRepository,
            relayFinalizationAttemptRepository, 10, 24, 24);
    }

    @Test
    void cleanupTempObjectsDeletesOldObjectsWhenRoomStateIsMissing() {
        String objectKey = "relay/tmp/AB3K9Q/0/face.png";
        given(relayObjectStorage.findObjects("relay/tmp/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(objectKey, NOW.minusHours(25))));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());

        RelayOrphanObjectCleanupResult result = service.cleanupTempObjects(NOW);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        verify(relayObjectStorage).deleteObject(objectKey);
    }

    @Test
    void cleanupTempObjectsSkipsActiveRoomObjects() {
        String objectKey = "relay/tmp/AB3K9Q/0/face.png";
        given(relayObjectStorage.findObjects("relay/tmp/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(objectKey, NOW.minusHours(25))));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(room(RelayRoomStatus.PLAYING)));

        RelayOrphanObjectCleanupResult result = service.cleanupTempObjects(NOW);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(relayObjectStorage, never()).deleteObject(anyString());
    }

    @Test
    void cleanupTempObjectsCachesRoomStateLookupWithinRun() {
        String faceObjectKey = "relay/tmp/AB3K9Q/0/face.png";
        String bodyObjectKey = "relay/tmp/AB3K9Q/0/body.png";
        given(relayObjectStorage.findObjects("relay/tmp/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(faceObjectKey, NOW.minusHours(25)),
                new RelayStoredObject(bodyObjectKey, NOW.minusHours(25))));
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());

        RelayOrphanObjectCleanupResult result = service.cleanupTempObjects(NOW);

        assertThat(result.deletedCount()).isEqualTo(2);
        verify(relayRoomRepository).findByRoomCode(ROOM_CODE);
        verify(relayObjectStorage).deleteObject(faceObjectKey);
        verify(relayObjectStorage).deleteObject(bodyObjectKey);
    }

    @Test
    void cleanupResultObjectsDeletesUnreferencedOldResultObjects() {
        String objectKey = "relay/results/artifact-id/original.png";
        given(relayObjectStorage.findObjects("relay/results/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(objectKey, NOW.minusHours(25))));
        given(relayArtifactRepository.findReferencedRelayResultObjectKeys(Set.of(objectKey))).willReturn(Set.of());
        given(relayFinalizationAttemptRepository.findReferencedObjectKeys(Set.of(objectKey), 10)).willReturn(Set.of());

        RelayOrphanObjectCleanupResult result = service.cleanupResultObjects(NOW);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        verify(relayObjectStorage).deleteObject(objectKey);
    }

    @Test
    void cleanupResultObjectsSkipsDbReferencedObjects() {
        String objectKey = "relay/results/artifact-id/original.png";
        given(relayObjectStorage.findObjects("relay/results/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(objectKey, NOW.minusHours(25))));
        given(relayArtifactRepository.findReferencedRelayResultObjectKeys(Set.of(objectKey)))
            .willReturn(Set.of(objectKey));

        RelayOrphanObjectCleanupResult result = service.cleanupResultObjects(NOW);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(relayFinalizationAttemptRepository).findReferencedObjectKeys(Set.of(), 10);
        verify(relayObjectStorage, never()).deleteObject(anyString());
    }

    @Test
    void cleanupResultObjectsSkipsActiveAttemptObjects() {
        String objectKey = "relay/results/artifact-id/original.png";
        given(relayObjectStorage.findObjects("relay/results/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(objectKey, NOW.minusHours(25))));
        given(relayArtifactRepository.findReferencedRelayResultObjectKeys(Set.of(objectKey))).willReturn(Set.of());
        given(relayFinalizationAttemptRepository.findReferencedObjectKeys(Set.of(objectKey), 10))
            .willReturn(Set.of(objectKey));

        RelayOrphanObjectCleanupResult result = service.cleanupResultObjects(NOW);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(relayObjectStorage, never()).deleteObject(anyString());
    }

    @Test
    void cleanupResultObjectsContinuesAfterDeleteFailure() {
        String failedKey = "relay/results/failed/original.png";
        String deletedKey = "relay/results/deleted/original.png";
        given(relayObjectStorage.findObjects("relay/results/", NOW.minusHours(24), 10))
            .willReturn(List.of(new RelayStoredObject(failedKey, NOW.minusHours(25)),
                new RelayStoredObject(deletedKey, NOW.minusHours(25))));
        given(relayArtifactRepository.findReferencedRelayResultObjectKeys(anySet())).willReturn(Set.of());
        given(relayFinalizationAttemptRepository.findReferencedObjectKeys(anySet(), eq(10))).willReturn(Set.of());
        doThrow(new FileStorageException("storage", new RuntimeException("boom"))).when(relayObjectStorage)
            .deleteObject(failedKey);

        RelayOrphanObjectCleanupResult result = service.cleanupResultObjects(NOW);

        assertThat(result.scannedCount()).isEqualTo(2);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(relayObjectStorage).deleteObject(failedKey);
        verify(relayObjectStorage).deleteObject(deletedKey);
    }

    private RelayRoomState room(RelayRoomStatus status) {
        UUID hostUuid = UUID.randomUUID();
        RelayRoomParticipant host = new RelayRoomParticipant(hostUuid.toString(), "Mango", true, 0, true, null,
            NOW.minusMinutes(10));

        return new RelayRoomState(ROOM_CODE, status, hostUuid.toString(), 45, 2, 6, RelayDrawingPart.FACE,
            List.of(host), List.of(), NOW.minusMinutes(5), NOW.minusMinutes(4), NOW.minusMinutes(5),
            NOW.minusMinutes(10), NOW.minusMinutes(1));
    }
}
