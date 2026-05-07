package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.finalization.RelayFinalizationArtifactResult;
import com.nemonicworld.relay.service.finalization.RelayResultComposer;
import com.nemonicworld.relay.service.finalization.RelayResultStorage;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationResult;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationService;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelayRoomFinalizationServiceTest {

    private static final String ROOM_CODE = "AB3K9Q";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 6, 14, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private RelayRoomRepository relayRoomRepository;

    @Mock
    private RelayArtifactRepository relayArtifactRepository;

    @Mock
    private RelayResultStorage relayResultStorage;

    @Mock
    private RelayRoomEventPublisher relayRoomEventPublisher;

    @Mock
    private RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    private RelayRoomFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new RelayRoomFinalizationService(relayRoomRepository, relayArtifactRepository, relayResultStorage,
            new RelayResultComposer(4, 3, 4), relayRoomEventPublisher, new ObjectMapper().findAndRegisterModules(),
            relayInviteMetadataSyncService, 50, 60);
    }

    @Test
    void processFinalizingRoomCreatesResultPerCanvasAndFinishesRoom() {
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();
        UUID participantC = UUID.randomUUID();
        RelayRoomState roomState = finalizingRoom(participantA, participantB, participantC);
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(java.util.Optional.of(roomState));
        given(relayArtifactRepository.findRelayArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(relayResultStorage.download(anyString())).willAnswer(invocation -> pngForKey(invocation.getArgument(0)));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomFinalizationResult result = service.processFinalizingRoom(ROOM_CODE);

        assertThat(result.processed()).isTrue();
        assertThat(result.resultCount()).isEqualTo(3);
        assertThat(result.artifacts()).extracting(RelayFinalizationArtifactResult::canvasIndex).containsExactly(0, 1,
            2);
        assertThat(result.artifacts()).allSatisfy(artifact -> {
            assertThat(artifact.originalObjectKey())
                .isEqualTo("relay/results/%s/original.png".formatted(artifact.artifactId()));
            assertThat(artifact.thumbnailObjectKey())
                .isEqualTo("relay/results/%s/thumbnail.png".formatted(artifact.artifactId()));
            assertThat(artifact.meta()).contains("\"canvasIndex\":").contains("\"roomCode\":\"AB3K9Q\"")
                .contains("\"drawerUserUuid\"").contains("\"drawerNickname\"");
        });

        ArgumentCaptor<List<RelayFinalizationArtifactResult>> artifactCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> participantCaptor = ArgumentCaptor.forClass(List.class);
        verify(relayArtifactRepository).saveRelayDrawingResults(anyString(), artifactCaptor.capture(),
            participantCaptor.capture(), any(LocalDateTime.class));
        assertThat(artifactCaptor.getValue()).hasSize(3);
        assertThat(participantCaptor.getValue()).containsExactly(participantA.toString(), participantB.toString(),
            participantC.toString());

        ArgumentCaptor<RelayRoomState> updatedRoomCaptor = ArgumentCaptor.forClass(RelayRoomState.class);
        verify(relayRoomRepository).saveIfUnchanged(any(RelayRoomState.class), updatedRoomCaptor.capture());
        assertThat(updatedRoomCaptor.getValue().status()).isEqualTo(RelayRoomStatus.FINISHED);
        assertThat(updatedRoomCaptor.getValue().assignments()).isEqualTo(roomState.assignments());
        verify(relayRoomEventPublisher).publishResultCreated(any(RelayRoomFinalizationResult.class));
    }

    @Test
    void processFinalizingRoomDoesNotCreateGalleryRowsForDroppedParticipants() {
        UUID participantA = UUID.randomUUID();
        UUID droppedUuid = UUID.randomUUID();
        RelayRoomState roomState = finalizingRoom(participantA, droppedUuid).withParticipants(
            List.of(participant(participantA, true, 0), droppedParticipant(droppedUuid, false, 1)), NOW);
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(java.util.Optional.of(roomState));
        given(relayArtifactRepository.findRelayArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(relayResultStorage.download(anyString())).willAnswer(invocation -> pngForKey(invocation.getArgument(0)));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        service.processFinalizingRoom(ROOM_CODE);

        ArgumentCaptor<List<String>> participantCaptor = ArgumentCaptor.forClass(List.class);
        verify(relayArtifactRepository).saveRelayDrawingResults(anyString(), any(), participantCaptor.capture(),
            any(LocalDateTime.class));
        assertThat(participantCaptor.getValue()).containsExactly(participantA.toString());
    }

    @Test
    void processFinalizingRoomCreatesBlankAreasForAutoSubmittedParts() {
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();
        RelayRoomState roomState = finalizingRoom(participantA, participantB).withAssignments(List.of(
            assignment(0, RelayDrawingPart.FACE, participantA, RelayAssignmentStatus.AUTO_SUBMITTED, null, true, true),
            assignment(0, RelayDrawingPart.BODY, participantB, RelayAssignmentStatus.SUBMITTED,
                "relay/tmp/AB3K9Q/0/body.png", false, false),
            assignment(0, RelayDrawingPart.LEGS, participantA, RelayAssignmentStatus.SUBMITTED,
                "relay/tmp/AB3K9Q/0/legs.png", false, false),
            assignment(1, RelayDrawingPart.FACE, participantB, RelayAssignmentStatus.SUBMITTED,
                "relay/tmp/AB3K9Q/1/face.png", false, false),
            assignment(1, RelayDrawingPart.BODY, participantA, RelayAssignmentStatus.AUTO_SUBMITTED, null, true, true),
            assignment(1, RelayDrawingPart.LEGS, participantB, RelayAssignmentStatus.SUBMITTED,
                "relay/tmp/AB3K9Q/1/legs.png", false, false)),
            NOW);
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(java.util.Optional.of(roomState));
        given(relayArtifactRepository.findRelayArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(relayResultStorage.download(anyString())).willAnswer(invocation -> pngForKey(invocation.getArgument(0)));
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomFinalizationResult result = service.processFinalizingRoom(ROOM_CODE);

        assertThat(result.resultCount()).isEqualTo(2);
        verify(relayResultStorage, never()).download((String) null);
        verify(relayRoomEventPublisher).publishResultCreated(any(RelayRoomFinalizationResult.class));
    }

    @Test
    void processFinalizingRoomFailsWhenSubmittedAssignmentHasNoObjectKey() {
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();
        RelayRoomState roomState = finalizingRoom(participantA, participantB).withAssignments(List.of(
            assignment(0, RelayDrawingPart.FACE, participantA, RelayAssignmentStatus.SUBMITTED, null, false, false),
            assignment(0, RelayDrawingPart.BODY, participantB, RelayAssignmentStatus.SUBMITTED,
                "relay/tmp/AB3K9Q/0/body.png", false, false),
            assignment(0, RelayDrawingPart.LEGS, participantA, RelayAssignmentStatus.SUBMITTED,
                "relay/tmp/AB3K9Q/0/legs.png", false, false)),
            NOW);
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(java.util.Optional.of(roomState));
        given(relayArtifactRepository.findRelayArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());

        assertThatThrownBy(() -> service.processFinalizingRoom(ROOM_CODE)).isInstanceOf(IllegalStateException.class);

        verify(relayArtifactRepository, never()).saveRelayDrawingResults(anyString(), any(), any(), any());
        verify(relayRoomRepository, never()).saveIfUnchanged(any(), any());
        verifyNoInteractions(relayRoomEventPublisher);
    }

    @Test
    void processFinalizingRoomReusesExistingArtifactsAndOnlyFinishesRoom() {
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();
        RelayRoomState roomState = finalizingRoom(participantA, participantB);
        List<RelayFinalizationArtifactResult> existingArtifacts = List.of(artifact(0, UUID.randomUUID()),
            artifact(1, UUID.randomUUID()));
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(java.util.Optional.of(roomState));
        given(relayArtifactRepository.findRelayArtifactsBySourceRoomId(ROOM_CODE)).willReturn(existingArtifacts);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(true);

        RelayRoomFinalizationResult result = service.processFinalizingRoom(ROOM_CODE);

        assertThat(result.artifacts()).isEqualTo(existingArtifacts);
        verifyNoInteractions(relayResultStorage);
        verify(relayArtifactRepository, never()).saveRelayDrawingResults(anyString(), any(), any(), any());
        verify(relayRoomEventPublisher).publishResultCreated(any(RelayRoomFinalizationResult.class));
    }

    @Test
    void processFinalizingRoomFailsWhenRedisSaveConflicts() {
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();
        RelayRoomState roomState = finalizingRoom(participantA, participantB);
        List<RelayFinalizationArtifactResult> existingArtifacts = List.of(artifact(0, UUID.randomUUID()),
            artifact(1, UUID.randomUUID()));
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(true);
        given(relayRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(java.util.Optional.of(roomState));
        given(relayArtifactRepository.findRelayArtifactsBySourceRoomId(ROOM_CODE)).willReturn(existingArtifacts);
        given(relayRoomRepository.saveIfUnchanged(any(RelayRoomState.class), any(RelayRoomState.class)))
            .willReturn(false);

        assertThatThrownBy(() -> service.processFinalizingRoom(ROOM_CODE)).isInstanceOf(ConflictException.class)
            .hasMessage("릴레이 방 상태를 갱신할 수 없습니다.");

        verifyNoInteractions(relayResultStorage);
        verify(relayRoomEventPublisher, never()).publishResultCreated(any(RelayRoomFinalizationResult.class));
    }

    @Test
    void processFinalizingRoomDoesNothingWhenLockIsNotAcquired() {
        given(relayRoomRepository.acquireFinalizationLock(ROOM_CODE, Duration.ofSeconds(60))).willReturn(false);

        RelayRoomFinalizationResult result = service.processFinalizingRoom(ROOM_CODE);

        assertThat(result.processed()).isFalse();
        verify(relayRoomRepository, never()).findByRoomCode(anyString());
        verifyNoInteractions(relayArtifactRepository, relayResultStorage, relayRoomEventPublisher);
    }

    private RelayRoomState finalizingRoom(UUID... participants) {
        List<RelayRoomParticipant> roomParticipants = java.util.stream.IntStream.range(0, participants.length)
            .mapToObj(index -> participant(participants[index], index == 0, index)).toList();
        List<RelayRoomAssignment> assignments = java.util.stream.IntStream.range(0, participants.length).boxed()
            .flatMap(canvasIndex -> java.util.Arrays.stream(RelayDrawingPart.values())
                .map(part -> assignment(canvasIndex, part,
                    participants[(canvasIndex + part.ordinal()) % participants.length], RelayAssignmentStatus.SUBMITTED,
                    "relay/tmp/AB3K9Q/%d/%s.png".formatted(canvasIndex, part.name().toLowerCase()), false, false)))
            .toList();

        return new RelayRoomState(ROOM_CODE, RelayRoomStatus.FINALIZING, participants[0].toString(), 45, 2, 6,
            RelayDrawingPart.LEGS, roomParticipants, assignments, NOW.minusMinutes(1), NOW, NOW.minusMinutes(5),
            NOW.minusMinutes(10), NOW.minusSeconds(1));
    }

    private RelayRoomParticipant participant(UUID userUuid, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), "Mango-%d".formatted(joinOrder), host, joinOrder, true,
            null, NOW.minusMinutes(10));
    }

    private RelayRoomParticipant droppedParticipant(UUID userUuid, boolean host, int joinOrder) {
        return new RelayRoomParticipant(userUuid.toString(), "Mango-%d".formatted(joinOrder), host, joinOrder, false,
            NOW.minusSeconds(20), NOW.minusMinutes(10), true, NOW.minusSeconds(5));
    }

    private RelayRoomAssignment assignment(int canvasIndex, RelayDrawingPart part, UUID assignedUserUuid,
        RelayAssignmentStatus status, String objectKey, boolean empty, boolean autoSubmitted) {
        return new RelayRoomAssignment(canvasIndex, part, assignedUserUuid.toString(), status, null, objectKey, null,
            empty, autoSubmitted, NOW.minusSeconds(5));
    }

    private RelayFinalizationArtifactResult artifact(int canvasIndex, UUID artifactId) {
        return new RelayFinalizationArtifactResult(artifactId, canvasIndex,
            "relay/results/%s/original.png".formatted(artifactId),
            "relay/results/%s/thumbnail.png".formatted(artifactId),
            "{\"canvasIndex\":%d,\"roomCode\":\"AB3K9Q\"}".formatted(canvasIndex));
    }

    private byte[] pngForKey(String objectKey) throws Exception {
        Color color = objectKey.contains("/0/") ? Color.RED : objectKey.contains("/1/") ? Color.GREEN : Color.BLUE;
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
