package com.nemonicworld.flipbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultFrameResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultItemResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultsResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookArtifactRepository;
import com.nemonicworld.flipbook.repository.FlipbookResultArtifactRow;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플립북 최종 결과 화면에서 사용할 gallery 소유 결과물을 조회합니다.
 */
@Service
public class FlipbookRoomResultQueryUseCase {

    private static final String RESULT_NOT_FOUND_MESSAGE = "플립북 결과를 찾을 수 없습니다.";
    private static final String RESULT_ACCESS_DENIED_MESSAGE = "플립북 결과를 조회할 권한이 없습니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookArtifactRepository flipbookArtifactRepository;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final ObjectMapper objectMapper;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public FlipbookRoomResultQueryUseCase(AnonymousUserResolver anonymousUserResolver,
        FlipbookArtifactRepository flipbookArtifactRepository, FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomPolicy flipbookRoomPolicy, ObjectMapper objectMapper,
        MinioPublicUrlResolver minioPublicUrlResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.flipbookArtifactRepository = flipbookArtifactRepository;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.objectMapper = objectMapper;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    /**
     * PostgreSQL의 artifact/gallery를 조회하고, 최종화 진행 중이면 ready=false로 응답합니다.
     */
    @Transactional
    public FlipbookRoomResultsResponse getResults(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);

        UUID viewerUserUuid = viewerUser.getId();
        Optional<FlipbookRoomState> roomState = flipbookRoomRepository.findByRoomCode(roomCodeValue);
        List<FlipbookResultArtifactRow> rows = flipbookArtifactRepository
            .findActiveFlipbookResultsByRoomCodeAndUserUuid(roomCodeValue, viewerUserUuid);

        if (!rows.isEmpty()) {
            return createReadyResponse(roomCodeValue, roomState.map(FlipbookRoomState::status).orElse(null), rows);
        }

        if (flipbookArtifactRepository.countFlipbookResultsByRoomCode(roomCodeValue) > 0) {
            throw new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE);
        }

        FlipbookRoomState existingRoomState = roomState
            .orElseThrow(() -> new NotFoundException(RESULT_NOT_FOUND_MESSAGE));
        validateResultPollingAllowed(existingRoomState, viewerUserUuid.toString());
        return new FlipbookRoomResultsResponse(roomCodeValue, existingRoomState.status(), false, 0, List.of());
    }

    private void validateResultPollingAllowed(FlipbookRoomState roomState, String viewerUserUuid) {
        FlipbookRoomParticipant participant = flipbookRoomPolicy.findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE));
        if (participant.dropped()) {
            throw new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE);
        }
    }

    private FlipbookRoomResultsResponse createReadyResponse(String roomCode, FlipbookRoomStatus roomStatus,
        List<FlipbookResultArtifactRow> rows) {
        List<FlipbookRoomResultItemResponse> results = rows.stream().sorted(resultRowComparator()).map(this::toResponse)
            .toList();

        return new FlipbookRoomResultsResponse(roomCode, roomStatus, true, results.size(), results);
    }

    private Comparator<FlipbookResultArtifactRow> resultRowComparator() {
        return Comparator
            .comparing((FlipbookResultArtifactRow row) -> extractFlipbookIndex(row.meta()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FlipbookResultArtifactRow::createdAt, Comparator.nullsLast(LocalDateTime::compareTo))
            .thenComparing(row -> row.artifactId().toString());
    }

    private FlipbookRoomResultItemResponse toResponse(FlipbookResultArtifactRow row) {
        return new FlipbookRoomResultItemResponse(extractFlipbookIndex(row.meta()), row.galleryId().toString(),
            row.artifactId().toString(), minioPublicUrlResolver.resolve(row.thumbnailUrl()),
            minioPublicUrlResolver.resolve(row.gifUrl()), minioPublicUrlResolver.resolve(row.firstImageUrl()),
            row.createdAt(), extractFrames(row.meta()));
    }

    private Integer extractFlipbookIndex(String meta) {
        try {
            JsonNode metaNode = objectMapper.readTree(meta);
            JsonNode flipbookIndexNode = metaNode.path("flipbookIndex");
            return flipbookIndexNode.canConvertToInt() ? flipbookIndexNode.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<FlipbookRoomResultFrameResponse> extractFrames(String meta) {
        try {
            JsonNode framesNode = objectMapper.readTree(meta).path("frames");
            if (!framesNode.isArray()) {
                return List.of();
            }

            return StreamSupport.stream(framesNode.spliterator(), false).map(this::toFrameResponse)
                .flatMap(Optional::stream).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Optional<FlipbookRoomResultFrameResponse> toFrameResponse(JsonNode frameNode) {
        if (!frameNode.path("frameIndex").canConvertToInt()) {
            return Optional.empty();
        }

        String imageUrl = minioPublicUrlResolver.resolve(textOrNull(frameNode.path("imageObjectKey")));
        return Optional.of(new FlipbookRoomResultFrameResponse(frameNode.path("frameIndex").asInt(), imageUrl,
            textOrNull(frameNode.path("drawnByUserUuid")), textOrNull(frameNode.path("drawnByNickname"))));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        return node.asText();
    }
}
