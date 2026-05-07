package com.nemonicworld.flipbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultsResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookArtifactRepository;
import com.nemonicworld.flipbook.repository.FlipbookResultArtifactRow;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.flipbook.service.result.FlipbookGifComposer;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import com.nemonicworld.flipbook.service.result.FlipbookResultStorage;
import com.nemonicworld.flipbook.service.result.FlipbookThumbnailComposer;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.global.storage.minio.MinioStorageProperties;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
class FlipbookRoomResultQueryUseCaseTest {

    private static final String ROOM_CODE = "FB3K9Q";
    private static final String MINIO_PUBLIC_URL = "http://localhost:9000/nemonic-local/";
    private static final UUID VIEWER_UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID PARTICIPANT_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DROPPED_UUID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 8, 14, 0).truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private FlipbookArtifactRepository flipbookArtifactRepository;

    @Mock
    private FlipbookRoomRepository flipbookRoomRepository;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    @Mock
    private FlipbookResultStorage flipbookResultStorage;

    private FlipbookRoomResultQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        FlipbookRoomPolicy flipbookRoomPolicy = new FlipbookRoomPolicy(roomCodeGenerator, flipbookRoomRepository);
        MinioPublicUrlResolver minioPublicUrlResolver = new MinioPublicUrlResolver(minioStorageProperties());
        useCase = new FlipbookRoomResultQueryUseCase(anonymousUserResolver, flipbookArtifactRepository,
            flipbookRoomRepository, flipbookRoomPolicy, flipbookResultStorage, new FlipbookGifComposer(200),
            new ObjectMapper().findAndRegisterModules(), new FlipbookThumbnailComposer(512), minioPublicUrlResolver);
    }

    @Test
    void getResultsReturnsOwnedResultsSortedByFlipbookIndexEvenWhenRedisExpired() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookArtifactRepository.findActiveFlipbookResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of(resultRow(1), resultRow(0)));
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());

        FlipbookRoomResultsResponse response = useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE);

        assertThat(response.roomCode()).isEqualTo(ROOM_CODE);
        assertThat(response.roomStatus()).isNull();
        assertThat(response.ready()).isTrue();
        assertThat(response.resultCount()).isEqualTo(2);
        assertThat(response.results()).extracting("flipbookIndex").containsExactly(0, 1);
        assertThat(response.results().get(0).gifUrl()).startsWith(MINIO_PUBLIC_URL).contains("/result.gif");
        assertThat(response.results().get(0).firstImageUrl()).startsWith(MINIO_PUBLIC_URL).contains("/frame-0.png");
        assertThat(response.results().get(0).frames()).extracting("drawnByNickname").containsExactly("Mango", "Peach");
    }

    @Test
    void getResultsReturnsReadyFalseBeforeRoomIsFinished() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookArtifactRepository.findActiveFlipbookResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of());
        given(flipbookArtifactRepository.countFlipbookResultsByRoomCode(ROOM_CODE)).willReturn(0L);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(playingRoomState()));

        FlipbookRoomResultsResponse response = useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE);

        assertThat(response.ready()).isFalse();
        assertThat(response.roomStatus()).isEqualTo(FlipbookRoomStatus.PLAYING);
        assertThat(response.resultCount()).isZero();
        assertThat(response.results()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getResultsCreatesGifArtifactsAndGalleryRowsWhenRoomIsFinished() throws Exception {
        FlipbookRoomState finishedRoomState = finishedRoomState();
        FlipbookResultArtifactRow createdRow = resultRow(0);
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookArtifactRepository.findActiveFlipbookResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of()).willReturn(List.of(createdRow));
        given(flipbookArtifactRepository.countFlipbookResultsByRoomCode(ROOM_CODE)).willReturn(0L);
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.of(finishedRoomState));
        given(flipbookArtifactRepository.findFlipbookArtifactsBySourceRoomId(ROOM_CODE)).willReturn(List.of());
        given(flipbookResultStorage.download(anyString())).willReturn(pngBytes(Color.RED))
            .willReturn(pngBytes(Color.BLUE));

        FlipbookRoomResultsResponse response = useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE);

        assertThat(response.ready()).isTrue();
        assertThat(response.resultCount()).isEqualTo(1);

        ArgumentCaptor<List<FlipbookResultArtifactResult>> artifactsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> participantUuidsCaptor = ArgumentCaptor.forClass(List.class);
        verify(flipbookArtifactRepository).saveFlipbookResults(anyString(), artifactsCaptor.capture(),
            participantUuidsCaptor.capture(), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        assertThat(artifactsCaptor.getValue()).hasSize(1);
        assertThat(artifactsCaptor.getValue().get(0).gifObjectKey()).startsWith("flipbook/results/")
            .endsWith("/result.gif");
        assertThat(artifactsCaptor.getValue().get(0).firstImageObjectKey()).isEqualTo("uploads/flipbook/frame-0.png");
        assertThat(artifactsCaptor.getValue().get(0).thumbnailObjectKey()).startsWith("flipbook/results/")
            .endsWith("/thumbnail.png");
        assertThat(participantUuidsCaptor.getValue()).containsExactlyInAnyOrder(VIEWER_UUID.toString(),
            PARTICIPANT_UUID.toString());
        assertThat(participantUuidsCaptor.getValue()).doesNotContain(DROPPED_UUID.toString());
        verify(flipbookResultStorage).upload(anyString(), org.mockito.ArgumentMatchers.any(byte[].class),
            org.mockito.ArgumentMatchers.eq("image/gif"));
        verify(flipbookResultStorage).upload(anyString(), org.mockito.ArgumentMatchers.any(byte[].class),
            org.mockito.ArgumentMatchers.eq("image/png"));
    }

    @Test
    void getResultsRejectsUserWithoutActiveGalleryWhenRoomResultsExist() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookArtifactRepository.findActiveFlipbookResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of());
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());
        given(flipbookArtifactRepository.countFlipbookResultsByRoomCode(ROOM_CODE)).willReturn(2L);

        assertThatThrownBy(() -> useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE))
            .isInstanceOf(ForbiddenException.class).hasMessage("플립북 결과를 조회할 권한이 없습니다.");
    }

    @Test
    void getResultsReturnsNotFoundWhenNoRedisAndNoDbResultExist() {
        given(anonymousUserResolver.resolve(VIEWER_UUID.toString())).willReturn(appUser(VIEWER_UUID));
        given(roomCodeGenerator.isValid(ROOM_CODE)).willReturn(true);
        given(flipbookArtifactRepository.findActiveFlipbookResultsByRoomCodeAndUserUuid(ROOM_CODE, VIEWER_UUID))
            .willReturn(List.of());
        given(flipbookRoomRepository.findByRoomCode(ROOM_CODE)).willReturn(Optional.empty());
        given(flipbookArtifactRepository.countFlipbookResultsByRoomCode(ROOM_CODE)).willReturn(0L);

        assertThatThrownBy(() -> useCase.getResults(VIEWER_UUID.toString(), ROOM_CODE))
            .isInstanceOf(NotFoundException.class).hasMessage("플립북 결과를 찾을 수 없습니다.");
    }

    private AppUser appUser(UUID userUuid) {
        AppUser appUser = AppUser.createAnonymous(userUuid, "MangoApp/1.0", NOW.minusDays(1));
        appUser.updateNickname("Mango", NOW.minusHours(1));

        return appUser;
    }

    private FlipbookResultArtifactRow resultRow(int flipbookIndex) {
        UUID artifactId = UUID.randomUUID();
        return new FlipbookResultArtifactRow(UUID.randomUUID(), artifactId,
            "flipbook/results/%s/thumbnail.png".formatted(artifactId),
            "flipbook/results/%s/result.gif".formatted(artifactId),
            "uploads/flipbook/%d/frame-0.png".formatted(flipbookIndex), meta(flipbookIndex),
            NOW.plusSeconds(flipbookIndex));
    }

    private String meta(int flipbookIndex) {
        return """
            {
              "flipbookIndex": %d,
              "roomCode": "%s",
              "frames": [
                {
                  "frameIndex": 0,
                  "imageObjectKey": "uploads/flipbook/%d/frame-0.png",
                  "drawnByUserUuid": "%s",
                  "drawnByNickname": "Mango"
                },
                {
                  "frameIndex": 1,
                  "imageObjectKey": "uploads/flipbook/%d/frame-1.png",
                  "drawnByUserUuid": "%s",
                  "drawnByNickname": "Peach"
                }
              ]
            }
            """.formatted(flipbookIndex, ROOM_CODE, flipbookIndex, VIEWER_UUID, flipbookIndex, PARTICIPANT_UUID);
    }

    private FlipbookRoomState playingRoomState() {
        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.PLAYING, VIEWER_UUID.toString(), 45, 2, 6, 1, 2,
            NOW.minusSeconds(10), NOW.plusSeconds(35), NOW.minusSeconds(10), List.of(),
            List.of(participant(VIEWER_UUID, "Mango", true, false)), NOW.minusMinutes(1), NOW, List.of());
    }

    private FlipbookRoomState finishedRoomState() {
        return new FlipbookRoomState(ROOM_CODE, FlipbookRoomStatus.FINISHED, VIEWER_UUID.toString(), 45, 2, 6, 2, 2,
            NOW.minusSeconds(45), NOW, NOW.minusMinutes(2),
            List.of(assignment(0, 0, VIEWER_UUID, "uploads/flipbook/frame-0.png"),
                assignment(0, 1, PARTICIPANT_UUID, "uploads/flipbook/frame-1.png"),
                autoSubmittedAssignment(1, 0, DROPPED_UUID)),
            List.of(participant(VIEWER_UUID, "Mango", true, false),
                participant(PARTICIPANT_UUID, "Peach", false, false), participant(DROPPED_UUID, "Berry", false, true)),
            NOW.minusMinutes(3), NOW, List.of());
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
            NOW.minusMinutes(3), dropped, dropped ? NOW.minusSeconds(30) : null);
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

    private MinioStorageProperties minioStorageProperties() {
        return new MinioStorageProperties("http://minio:9000", "http://localhost:9000", "minioadmin", "minioadmin",
            "nemonic-local", 10, 10 * 1024 * 1024);
    }
}
