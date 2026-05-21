package com.nemonicworld.flipbook.service.finalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookArtifactRepository;
import com.nemonicworld.flipbook.repository.FlipbookFinalizationRetryRepository;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.support.FlipbookInviteMetadataSyncService;
import com.nemonicworld.flipbook.service.close.FlipbookRoomCloseCommand;
import com.nemonicworld.flipbook.service.result.FlipbookGifComposer;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import com.nemonicworld.flipbook.service.result.FlipbookResultStorage;
import com.nemonicworld.flipbook.service.result.FlipbookThumbnailComposer;
import com.nemonicworld.flipbook.websocket.FlipbookRoomEventPublisher;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlipbookRoomFinalizationServiceTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final UUID HOST_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PARTICIPANT_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DROPPED_UUID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 11, 10, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private FlipbookArtifactRepository flipbookArtifactRepository;

    @Mock
    private FlipbookResultStorage flipbookResultStorage;

    @Mock
    private FlipbookRoomEventPublisher flipbookRoomEventPublisher;

    @Mock
    private FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    @Mock
    private FlipbookFinalizationRetryRepository flipbookFinalizationRetryRepository;

    private FlipbookRoomFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new FlipbookRoomFinalizationService(flipbookRoomRepository, flipbookArtifactRepository,
            flipbookResultStorage, new FlipbookGifComposer(200), new FlipbookThumbnailComposer(512),
            flipbookRoomEventPublisher, new ObjectMapper().findAndRegisterModules(), flipbookInviteMetadataSyncService,
            flipbookFinalizationRetryRepository,
            new FlipbookRoomCloseCommand(flipbookRoomRepository, flipbookInviteMetadataSyncService), 50, 60, 0, 60);
    }

    @Test
    void processFinalizingRoomCreatesArtifactsSavesGalleryAndFinishesRoom() throws Exception {
        FlipbookRoomState finalizingRoomState = finalizingRoomState();
        given(flipbookRoomRepository.acquireFinalizationLock(eq(ROOM_CODE), anyString(), eq(Duration.ofSeconds(60))))
            .willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(finalizingRoomState));
        given(flipbookArtifactRepository.findFlipbookArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(flipbookResultStorage.download("uploads/flipbook/frame-0.png")).willReturn(pngBytes(Color.RED));
        given(flipbookResultStorage.download("uploads/flipbook/frame-1.png")).willReturn(pngBytes(Color.BLUE));
        given(flipbookRoomRepository.saveIfUnchanged(eq(finalizingRoomState), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomFinalizationResult result = service.processFinalizingRoom(ROOM_CODE);

        assertThat(result.processed()).isTrue();
        assertThat(result.roomStatus()).isEqualTo(FlipbookRoomStatus.FINISHED);
        assertThat(result.resultCount()).isEqualTo(1);

        ArgumentCaptor<List<FlipbookResultArtifactResult>> artifactsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> participantUuidsCaptor = ArgumentCaptor.forClass(List.class);
        verify(flipbookArtifactRepository).saveFlipbookResults(eq(ROOM_CODE), artifactsCaptor.capture(),
            participantUuidsCaptor.capture(), any(LocalDateTime.class));
        assertThat(artifactsCaptor.getValue()).hasSize(1);
        assertThat(artifactsCaptor.getValue().get(0).gifObjectKey()).startsWith("flipbook/results/")
            .endsWith("/result.gif");
        assertThat(artifactsCaptor.getValue().get(0).thumbnailObjectKey()).startsWith("flipbook/results/")
            .endsWith("/thumbnail.png");
        assertThat(artifactsCaptor.getValue().get(0).firstImageObjectKey()).isEqualTo("uploads/flipbook/frame-0.png");
        assertThat(participantUuidsCaptor.getValue()).containsExactlyInAnyOrder(HOST_UUID.toString(),
            PARTICIPANT_UUID.toString());
        assertThat(participantUuidsCaptor.getValue()).doesNotContain(DROPPED_UUID.toString());

        ArgumentCaptor<FlipbookRoomState> updatedRoomStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(finalizingRoomState), updatedRoomStateCaptor.capture());
        assertThat(updatedRoomStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.FINISHED);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomStateCaptor.getValue());
        verify(flipbookFinalizationRetryRepository).clearFailureCount(ROOM_CODE);
        verify(flipbookRoomEventPublisher).publishResultCreated(any(FlipbookRoomFinalizationResult.class));
        verify(flipbookResultStorage).upload(anyString(), any(byte[].class), eq("image/gif"));
        verify(flipbookResultStorage).upload(anyString(), any(byte[].class), eq("image/png"));
    }

    @Test
    void processFinalizingRoomClosesRoomWhenNoResultFramesExist() {
        FlipbookRoomState finalizingRoomState = noResultFramesFinalizingRoomState();
        given(flipbookRoomRepository.acquireFinalizationLock(eq(ROOM_CODE), anyString(), eq(Duration.ofSeconds(60))))
            .willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(finalizingRoomState));
        given(flipbookRoomRepository.saveIfUnchanged(eq(finalizingRoomState), any(FlipbookRoomState.class)))
            .willReturn(true);

        FlipbookRoomFinalizationResult result = service.processFinalizingRoom(ROOM_CODE);

        assertThat(result.processed()).isTrue();
        assertThat(result.roomStatus()).isEqualTo(FlipbookRoomStatus.CLOSED);
        assertThat(result.resultCount()).isZero();

        ArgumentCaptor<FlipbookRoomState> updatedRoomStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(finalizingRoomState), updatedRoomStateCaptor.capture());
        assertThat(updatedRoomStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomStateCaptor.getValue());
        verify(flipbookFinalizationRetryRepository).clearFailureCount(ROOM_CODE);
        verify(flipbookRoomEventPublisher).publishRoomClosed(eq(ROOM_CODE), any(LocalDateTime.class),
            eq("no_result_frames"));
        verify(flipbookRoomEventPublisher, never()).publishResultCreated(any(FlipbookRoomFinalizationResult.class));
        verify(flipbookArtifactRepository, never()).findFlipbookArtifactsBySourceRoomId(anyString());
        verify(flipbookArtifactRepository, never()).saveFlipbookResults(anyString(), any(), any(),
            any(LocalDateTime.class));
        verify(flipbookResultStorage, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void processFinalizingRoomsKeepsRoomFinalizingBeforeMaxRetryCount() {
        FlipbookRoomState finalizingRoomState = finalizingRoomState();
        given(flipbookRoomRepository.findFinalizingRooms(50)).willReturn(List.of(finalizingRoomState));
        given(flipbookRoomRepository.acquireFinalizationLock(eq(ROOM_CODE), anyString(), eq(Duration.ofSeconds(60))))
            .willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(finalizingRoomState));
        given(flipbookArtifactRepository.findFlipbookArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(flipbookResultStorage.download(anyString())).willThrow(new InternalServerException("boom"));
        given(flipbookFinalizationRetryRepository.incrementFailureCount(eq(ROOM_CODE), eq(Duration.ofHours(24))))
            .willReturn(59);

        var result = service.processFinalizingRooms();

        assertThat(result.scannedRoomCount()).isEqualTo(1);
        assertThat(result.processedRoomCount()).isZero();
        assertThat(result.resultCount()).isZero();
        verify(flipbookRoomEventPublisher, org.mockito.Mockito.never()).publishRoomClosed(anyString(),
            any(LocalDateTime.class), any());
    }

    @Test
    void processFinalizingRoomsClosesRoomWhenMaxRetryCountIsReached() {
        FlipbookRoomState finalizingRoomState = finalizingRoomState();
        given(flipbookRoomRepository.findFinalizingRooms(50)).willReturn(List.of(finalizingRoomState));
        given(flipbookRoomRepository.acquireFinalizationLock(eq(ROOM_CODE), anyString(), eq(Duration.ofSeconds(60))))
            .willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(finalizingRoomState),
            Optional.of(finalizingRoomState));
        given(flipbookArtifactRepository.findFlipbookArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(flipbookResultStorage.download(anyString())).willThrow(new InternalServerException("boom"));
        given(flipbookFinalizationRetryRepository.incrementFailureCount(eq(ROOM_CODE), eq(Duration.ofHours(24))))
            .willReturn(60);
        given(flipbookRoomRepository.saveIfUnchanged(any(FlipbookRoomState.class), any(FlipbookRoomState.class)))
            .willReturn(true);

        service.processFinalizingRooms();

        ArgumentCaptor<FlipbookRoomState> updatedRoomStateCaptor = ArgumentCaptor.forClass(FlipbookRoomState.class);
        verify(flipbookRoomRepository).saveIfUnchanged(eq(finalizingRoomState), updatedRoomStateCaptor.capture());
        assertThat(updatedRoomStateCaptor.getValue().status()).isEqualTo(FlipbookRoomStatus.CLOSED);
        verify(flipbookInviteMetadataSyncService).syncWithRoomState(updatedRoomStateCaptor.getValue());
        verify(flipbookRoomEventPublisher).publishRoomClosed(eq(ROOM_CODE), any(LocalDateTime.class),
            eq("finalization_failed"));
    }

    @Test
    void triggerFinalizationReturnsNoOpWhenImmediateFinalizationFails() {
        RuntimeException error = new RuntimeException("boom");
        given(flipbookRoomRepository.acquireFinalizationLock(eq(ROOM_CODE), anyString(), eq(Duration.ofSeconds(60))))
            .willReturn(true);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willThrow(error);

        FlipbookRoomFinalizationResult result = service.triggerFinalization(ROOM_CODE);

        assertThat(result.processed()).isFalse();
        verify(flipbookRoomRepository).releaseFinalizationLock(eq(ROOM_CODE), anyString());
        verify(flipbookFinalizationRetryRepository).incrementFailureCount(eq(ROOM_CODE), eq(Duration.ofHours(24)));
    }

    private FlipbookRoomState finalizingRoomState() {
        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.FINALIZING, HOST_UUID.toString(), 45, 2, 6, 8, 8,
            NOW.minusSeconds(45), NOW, NOW.minusMinutes(6),
            List.of(assignment(0, 0, HOST_UUID, "uploads/flipbook/frame-0.png"),
                assignment(0, 1, PARTICIPANT_UUID, "uploads/flipbook/frame-1.png"),
                autoSubmittedAssignment(1, 0, DROPPED_UUID)),
            List.of(participant(HOST_UUID, "Mango", true, false), participant(PARTICIPANT_UUID, "Peach", false, false),
                participant(DROPPED_UUID, "Berry", false, true)),
            NOW.minusMinutes(7), NOW.minusSeconds(2), List.of());
    }

    private FlipbookRoomState noResultFramesFinalizingRoomState() {
        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.FINALIZING, HOST_UUID.toString(), 45, 2, 6, 8, 8,
            NOW.minusSeconds(45), NOW, NOW.minusMinutes(6),
            List.of(autoSubmittedAssignment(0, 0, HOST_UUID), autoSubmittedAssignment(0, 1, PARTICIPANT_UUID)),
            List.of(participant(HOST_UUID, "Mango", true, false), participant(PARTICIPANT_UUID, "Peach", false, false)),
            NOW.minusMinutes(7), NOW.minusSeconds(2), List.of());
    }

    private FlipbookFrameAssignment assignment(int flipbookIndex, int frameIndex, UUID userUuid, String objectKey) {
        return new FlipbookFrameAssignment(flipbookIndex, frameIndex, frameIndex + 1, userUuid.toString(),
            FlipbookFrameAssignmentStatus.SUBMITTED, UUID.randomUUID().toString(), objectKey, false, false,
            NOW.minusSeconds(5));
    }

    private FlipbookFrameAssignment autoSubmittedAssignment(int flipbookIndex, int frameIndex, UUID userUuid) {
        return new FlipbookFrameAssignment(flipbookIndex, frameIndex, frameIndex + 1, userUuid.toString(),
            FlipbookFrameAssignmentStatus.AUTO_SUBMITTED, null, null, true, true, NOW.minusSeconds(5));
    }

    private FlipbookRoomParticipant participant(UUID userUuid, String nickname, boolean host, boolean dropped) {
        return new FlipbookRoomParticipant(userUuid.toString(), nickname, host, host ? 0 : 1, true, null,
            NOW.minusMinutes(7), dropped, dropped ? NOW.minusSeconds(30) : null);
    }

    private byte[] pngBytes(Color color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 2, 2);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);

        return outputStream.toByteArray();
    }
}
