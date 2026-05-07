package com.nemonicworld.flipbook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ForbiddenException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultFrameResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomResultItemResponse;
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
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 플립북 최종 결과 화면에서 사용할 gallery 소유 결과물을 조회하고 필요 시 생성합니다.
 */
@Service
public class FlipbookRoomResultQueryUseCase {

    private static final String GIF_CONTENT_TYPE = "image/gif";
    private static final String RESULT_NOT_FOUND_MESSAGE = "플립북 결과를 찾을 수 없습니다.";
    private static final String RESULT_ACCESS_DENIED_MESSAGE = "플립북 결과를 조회할 권한이 없습니다.";
    private static final String RESULT_STATE_ERROR_MESSAGE = "플립북 결과 상태가 올바르지 않습니다.";
    private static final String RESULT_META_ERROR_MESSAGE = "플립북 결과 메타데이터를 생성할 수 없습니다.";

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookArtifactRepository flipbookArtifactRepository;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookResultStorage flipbookResultStorage;
    private final FlipbookGifComposer flipbookGifComposer;
    private final ObjectMapper objectMapper;
    private final MinioPublicUrlResolver minioPublicUrlResolver;

    public FlipbookRoomResultQueryUseCase(AnonymousUserResolver anonymousUserResolver,
        FlipbookArtifactRepository flipbookArtifactRepository, FlipbookRoomRepository flipbookRoomRepository,
        FlipbookRoomPolicy flipbookRoomPolicy, FlipbookResultStorage flipbookResultStorage,
        FlipbookGifComposer flipbookGifComposer, ObjectMapper objectMapper,
        MinioPublicUrlResolver minioPublicUrlResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.flipbookArtifactRepository = flipbookArtifactRepository;
        this.flipbookRoomRepository = flipbookRoomRepository;
        this.flipbookRoomPolicy = flipbookRoomPolicy;
        this.flipbookResultStorage = flipbookResultStorage;
        this.flipbookGifComposer = flipbookGifComposer;
        this.objectMapper = objectMapper;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
    }

    /**
     * PostgreSQL의 artifact/gallery를 우선 조회하고, 없으면 Redis 종료 상태에서 결과를 생성합니다.
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
        if (existingRoomState.status() != FlipbookRoomStatus.FINISHED) {
            return new FlipbookRoomResultsResponse(roomCodeValue, existingRoomState.status(), false, 0, List.of());
        }

        createAndSaveResults(existingRoomState);
        List<FlipbookResultArtifactRow> createdRows = flipbookArtifactRepository
            .findActiveFlipbookResultsByRoomCodeAndUserUuid(roomCodeValue, viewerUserUuid);

        return createReadyResponse(roomCodeValue, existingRoomState.status(), createdRows);
    }

    private void validateResultPollingAllowed(FlipbookRoomState roomState, String viewerUserUuid) {
        FlipbookRoomParticipant participant = flipbookRoomPolicy.findParticipant(roomState, viewerUserUuid)
            .orElseThrow(() -> new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE));
        if (participant.dropped()) {
            throw new ForbiddenException(RESULT_ACCESS_DENIED_MESSAGE);
        }
    }

    private void createAndSaveResults(FlipbookRoomState roomState) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<FlipbookResultArtifactResult> existingArtifacts = flipbookArtifactRepository
            .findFlipbookArtifactsBySourceRoomId(roomState.roomCode());
        List<Integer> expectedIndexes = findFlipbookIndexes(roomState);
        List<FlipbookResultArtifactResult> artifacts = resolveArtifacts(roomState, expectedIndexes, existingArtifacts);
        flipbookArtifactRepository.saveFlipbookResults(roomState.roomCode(), artifacts,
            findParticipantUuidValues(roomState), now);
    }

    private List<FlipbookResultArtifactResult> resolveArtifacts(FlipbookRoomState roomState,
        List<Integer> expectedIndexes, List<FlipbookResultArtifactResult> existingArtifacts) {
        if (existingArtifacts.isEmpty()) {
            return createAndUploadResults(roomState);
        }

        if (matchesExpectedFlipbookIndexes(existingArtifacts, expectedIndexes)) {
            return existingArtifacts;
        }

        throw new IllegalStateException(RESULT_STATE_ERROR_MESSAGE);
    }

    private List<FlipbookResultArtifactResult> createAndUploadResults(FlipbookRoomState roomState) {
        Map<Integer, List<FrameSource>> groupedFrameSources = groupFrameSourcesByFlipbookIndex(roomState);

        return groupedFrameSources.entrySet().stream()
            .map(entry -> createAndUploadResult(roomState, entry.getKey(), entry.getValue())).toList();
    }

    private FlipbookResultArtifactResult createAndUploadResult(FlipbookRoomState roomState, int flipbookIndex,
        List<FrameSource> frameSources) {
        UUID artifactId = UUID.randomUUID();
        String gifObjectKey = createResultObjectKey(artifactId, "result.gif");
        String firstImageObjectKey = frameSources.get(0).objectKey();
        byte[] gifBytes = flipbookGifComposer
            .compose(frameSources.stream().map(FrameSource::objectKey).map(flipbookResultStorage::download).toList());

        flipbookResultStorage.upload(gifObjectKey, gifBytes, GIF_CONTENT_TYPE);

        return new FlipbookResultArtifactResult(artifactId, flipbookIndex, gifObjectKey, firstImageObjectKey,
            firstImageObjectKey, createArtifactMeta(roomState, flipbookIndex, frameSources));
    }

    private Map<Integer, List<FrameSource>> groupFrameSourcesByFlipbookIndex(FlipbookRoomState roomState) {
        Map<Integer, List<FrameSource>> groupedFrameSources = new TreeMap<>();
        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (!isResultFrame(assignment)) {
                continue;
            }

            groupedFrameSources.computeIfAbsent(assignment.flipbookIndex(), key -> new java.util.ArrayList<>())
                .add(createFrameSource(roomState, assignment));
        }

        groupedFrameSources.replaceAll(
            (key, value) -> value.stream().sorted(Comparator.comparingInt(FrameSource::frameIndex)).toList());

        return groupedFrameSources;
    }

    private boolean isResultFrame(FlipbookFrameAssignment assignment) {
        return assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED && !assignment.empty()
            && !assignment.autoSubmitted() && StringUtils.hasText(assignment.objectKey());
    }

    private FrameSource createFrameSource(FlipbookRoomState roomState, FlipbookFrameAssignment assignment) {
        return new FrameSource(assignment.frameIndex(), assignment.objectKey(), assignment.assignedUserUuid(),
            findParticipantNickname(roomState, assignment.assignedUserUuid()));
    }

    private String createArtifactMeta(FlipbookRoomState roomState, int flipbookIndex, List<FrameSource> frameSources) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("flipbookIndex", flipbookIndex);
        meta.put("roomCode", roomState.roomCode());
        meta.put("frames", frameSources.stream().map(this::createFrameMeta).toList());

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(RESULT_META_ERROR_MESSAGE, e);
        }
    }

    private Map<String, Object> createFrameMeta(FrameSource frameSource) {
        Map<String, Object> frameMeta = new LinkedHashMap<>();
        frameMeta.put("frameIndex", frameSource.frameIndex());
        frameMeta.put("imageObjectKey", frameSource.objectKey());
        frameMeta.put("drawnByUserUuid", frameSource.drawnByUserUuid());
        frameMeta.put("drawnByNickname", frameSource.drawnByNickname());

        return frameMeta;
    }

    private List<String> findParticipantUuidValues(FlipbookRoomState roomState) {
        return roomState.participants().stream().filter(participant -> !participant.dropped())
            .map(FlipbookRoomParticipant::userUuid).distinct().toList();
    }

    private List<Integer> findFlipbookIndexes(FlipbookRoomState roomState) {
        return roomState.assignments().stream().filter(this::isResultFrame).map(FlipbookFrameAssignment::flipbookIndex)
            .distinct().sorted().toList();
    }

    private boolean matchesExpectedFlipbookIndexes(List<FlipbookResultArtifactResult> existingArtifacts,
        List<Integer> expectedIndexes) {
        List<Integer> existingIndexes = existingArtifacts.stream().map(FlipbookResultArtifactResult::flipbookIndex)
            .distinct().sorted().toList();

        return existingArtifacts.size() == expectedIndexes.size() && existingIndexes.equals(expectedIndexes);
    }

    private String createResultObjectKey(UUID artifactId, String fileName) {
        return "flipbook/results/%s/%s".formatted(artifactId, fileName);
    }

    private String findParticipantNickname(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse(null);
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

    private record FrameSource(int frameIndex, String objectKey, String drawnByUserUuid, String drawnByNickname) {
    }
}
