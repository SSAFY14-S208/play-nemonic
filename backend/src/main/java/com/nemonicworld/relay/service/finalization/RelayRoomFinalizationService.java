package com.nemonicworld.relay.service.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * FINALIZING 방의 canvasIndex별 최종 이미지를 만들고 artifact/gallery 저장 후 방을 FINISHED로
 * 전환합니다.
 */
@Service
public class RelayRoomFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomFinalizationService.class);
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String FINALIZATION_STATE_ERROR_MESSAGE = "릴레이 최종화 상태가 올바르지 않습니다.";
    private static final String FINALIZATION_META_ERROR_MESSAGE = "릴레이 최종화 메타데이터를 생성할 수 없습니다.";

    private final RelayRoomRepository relayRoomRepository;
    private final RelayArtifactRepository relayArtifactRepository;
    private final RelayResultStorage relayResultStorage;
    private final RelayResultComposer relayResultComposer;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final ObjectMapper objectMapper;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final int scanLimit;
    private final Duration lockTtl;
    private final Duration finalizationReadyDelay;

    public RelayRoomFinalizationService(RelayRoomRepository relayRoomRepository,
        RelayArtifactRepository relayArtifactRepository, RelayResultStorage relayResultStorage,
        RelayResultComposer relayResultComposer, RelayRoomEventPublisher relayRoomEventPublisher,
        ObjectMapper objectMapper, RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        @Value("${nemonic.relay.finalization.scan-limit:50}") int scanLimit,
        @Value("${nemonic.relay.finalization.lock-ttl-seconds:60}") long lockTtlSeconds,
        @Value("${nemonic.relay.finalization.ready-delay-ms:1000}") long readyDelayMs) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayArtifactRepository = relayArtifactRepository;
        this.relayResultStorage = relayResultStorage;
        this.relayResultComposer = relayResultComposer;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.objectMapper = objectMapper;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.scanLimit = scanLimit;
        this.lockTtl = Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
        this.finalizationReadyDelay = Duration.ofMillis(Math.max(0L, readyDelayMs));
    }

    /**
     * 스케줄러가 찾은 FINALIZING 방들을 순회하며 최종화를 시도합니다.
     */
    public RelayFinalizationProcessResult processFinalizingRooms() {
        List<RelayRoomState> finalizingRooms = relayRoomRepository.findFinalizingRooms(scanLimit);
        LocalDateTime readyCutoff = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minus(finalizationReadyDelay);
        int processedRoomCount = 0;
        int resultCount = 0;

        for (RelayRoomState finalizingRoom : finalizingRooms) {
            if (!isReadyForFinalization(finalizingRoom, readyCutoff)) {
                continue;
            }

            try {
                RelayRoomFinalizationResult result = processFinalizingRoom(finalizingRoom.roomCode());
                if (result.processed()) {
                    processedRoomCount++;
                    resultCount += result.resultCount();
                }
            } catch (RuntimeException e) {
                RelayRoomEventLogger.apiWarn("relay_finalization_failed", "failed to finalize relay room",
                    metadata("room_id", finalizingRoom.roomCode(), "stage", "process", "artifact_id", null, "operation",
                        "finalization"),
                    e);
                log.warn("릴레이 최종 결과물 생성 중 오류가 발생했습니다. roomCode={}", finalizingRoom.roomCode(), e);
            }
        }

        return new RelayFinalizationProcessResult(finalizingRooms.size(), processedRoomCount, resultCount);
    }

    private boolean isReadyForFinalization(RelayRoomState roomState, LocalDateTime readyCutoff) {
        return roomState.updatedAt() == null || !roomState.updatedAt().isAfter(readyCutoff);
    }

    /**
     * 한 방에 대한 최종화 lock을 획득한 뒤 실제 최종화 처리를 실행합니다.
     */
    public RelayRoomFinalizationResult processFinalizingRoom(String roomCode) {
        String lockToken = createFinalizationLockToken(roomCode);
        if (!relayRoomRepository.acquireFinalizationLock(roomCode, lockToken, lockTtl)) {
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        try {
            return processLockedFinalizingRoom(roomCode);
        } finally {
            relayRoomRepository.releaseFinalizationLock(roomCode, lockToken);
        }
    }

    private String createFinalizationLockToken(String roomCode) {
        return "token=%s,requestedAt=%s,owner=finalization,roomCode=%s".formatted(UUID.randomUUID(),
            LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), roomCode);
    }

    /**
     * 최신 Redis 상태를 기준으로 결과물을 만들고 방 상태를 FINISHED로 전환합니다.
     */
    private RelayRoomFinalizationResult processLockedFinalizingRoom(String roomCode) {
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINALIZING) {
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long startedNanos = System.nanoTime();
        List<Integer> canvasIndexes = findCanvasIndexes(roomState);
        if (canvasIndexes.isEmpty()) {
            throw new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
        }

        List<RelayFinalizationArtifactResult> existingArtifacts = relayArtifactRepository
            .findRelayArtifactsBySourceRoomId(roomCode);
        List<RelayFinalizationArtifactResult> artifacts = resolveArtifacts(roomState, canvasIndexes, existingArtifacts,
            now);
        RelayRoomState finishedRoomState = roomState.finish(now);
        if (!relayRoomRepository.saveIfUnchanged(roomState, finishedRoomState)) {
            ConflictException error = new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
            logFinalizationFailure(roomCode, "redis_update",
                artifacts.stream().findFirst().map(RelayFinalizationArtifactResult::artifactId).orElse(null), error);
            throw error;
        }
        relayInviteMetadataSyncService.syncWithRoomState(finishedRoomState);

        RelayRoomFinalizationResult result = RelayRoomFinalizationResult.finished(roomCode, artifacts, now);
        relayRoomEventPublisher.publishResultCreated(result);
        RelayRoomEventLogger.websocketBusiness("relay_result_created",
            metadata("room_id", roomCode, "result_count", result.resultCount(), "artifact_ids",
                artifacts.stream().map(artifact -> artifact.artifactId().toString()).toList(), "duration_ms",
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()));

        return result;
    }

    /**
     * 이미 생성된 결과물이 있으면 재사용하고, 없으면 새로 합성해 DB에 저장합니다.
     */
    private List<RelayFinalizationArtifactResult> resolveArtifacts(RelayRoomState roomState,
        List<Integer> canvasIndexes, List<RelayFinalizationArtifactResult> existingArtifacts, LocalDateTime now) {
        if (existingArtifacts.isEmpty()) {
            List<RelayFinalizationArtifactResult> artifacts = createAndUploadResults(roomState, canvasIndexes);
            try {
                relayArtifactRepository.saveRelayDrawingResults(roomState.roomCode(), artifacts,
                    findParticipantUuidValues(roomState), now);
            } catch (RuntimeException e) {
                logFinalizationFailure(roomState.roomCode(), "db_save",
                    artifacts.stream().findFirst().map(RelayFinalizationArtifactResult::artifactId).orElse(null), e);
                throw e;
            }

            return artifacts;
        }

        if (matchesExpectedCanvasIndexes(existingArtifacts, canvasIndexes)) {
            return existingArtifacts;
        }

        throw new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
    }

    /**
     * canvasIndex별 최종 원본/썸네일 이미지를 생성해 MinIO에 업로드합니다.
     */
    private List<RelayFinalizationArtifactResult> createAndUploadResults(RelayRoomState roomState,
        List<Integer> canvasIndexes) {
        return canvasIndexes.stream().map(canvasIndex -> createAndUploadResult(roomState, canvasIndex)).toList();
    }

    /**
     * 특정 canvasIndex 하나의 FACE/BODY/LEGS를 합성해 최종 artifact 후보를 만듭니다.
     */
    private RelayFinalizationArtifactResult createAndUploadResult(RelayRoomState roomState, int canvasIndex) {
        UUID artifactId = UUID.randomUUID();
        String originalObjectKey = createResultObjectKey(artifactId, "original.png");
        String thumbnailObjectKey = createResultObjectKey(artifactId, "thumbnail.png");
        RelayComposedImage composedImage;
        try {
            composedImage = relayResultComposer.compose(loadPartImages(roomState, canvasIndex));
        } catch (RuntimeException e) {
            logFinalizationFailure(roomState.roomCode(), "compose", artifactId, e);
            throw e;
        }

        try {
            relayResultStorage.upload(originalObjectKey, composedImage.originalPng(), PNG_CONTENT_TYPE);
            relayResultStorage.upload(thumbnailObjectKey, composedImage.thumbnailPng(), PNG_CONTENT_TYPE);
        } catch (RuntimeException e) {
            logFinalizationFailure(roomState.roomCode(), "minio_upload", artifactId, e);
            throw e;
        }

        try {
            return new RelayFinalizationArtifactResult(artifactId, canvasIndex, originalObjectKey, thumbnailObjectKey,
                createArtifactMeta(roomState, canvasIndex));
        } catch (RuntimeException e) {
            logFinalizationFailure(roomState.roomCode(), "artifact_meta", artifactId, e);
            throw e;
        }
    }

    private void logFinalizationFailure(String roomCode, String stage, UUID artifactId, RuntimeException error) {
        RelayRoomEventLogger.apiWarn("relay_finalization_failed", "failed to finalize relay room",
            metadata("room_id", roomCode, "stage", stage, "artifact_id", artifactId, "operation", "finalization"),
            error);
    }

    /**
     * 빈 파트는 건너뛰고, 제출 완료된 파트 이미지만 저장소에서 읽어옵니다.
     */
    private Map<RelayDrawingPart, byte[]> loadPartImages(RelayRoomState roomState, int canvasIndex) {
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        for (RelayDrawingPart part : RelayDrawingPart.values()) {
            RelayRoomAssignment assignment = findAssignment(roomState, canvasIndex, part);
            if (isEmptyAssignment(assignment)) {
                continue;
            }

            if (assignment.status() != RelayAssignmentStatus.SUBMITTED) {
                throw new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
            }

            if (!StringUtils.hasText(assignment.objectKey())) {
                throw new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE);
            }

            partImages.put(part, relayResultStorage.download(assignment.objectKey()));
        }

        return partImages;
    }

    /**
     * 특정 canvasIndex와 파트에 해당하는 배정을 찾습니다.
     */
    private RelayRoomAssignment findAssignment(RelayRoomState roomState, int canvasIndex, RelayDrawingPart part) {
        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == canvasIndex)
            .filter(assignment -> assignment.part() == part).findFirst()
            .orElseThrow(() -> new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE));
    }

    /**
     * 자동 제출 또는 빈 제출로 처리된 파트인지 확인합니다.
     */
    private boolean isEmptyAssignment(RelayRoomAssignment assignment) {
        return assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.empty()
            || assignment.autoSubmitted();
    }

    /**
     * 최종 결과물을 만들어야 하는 canvasIndex 목록을 추출합니다.
     */
    private List<Integer> findCanvasIndexes(RelayRoomState roomState) {
        return roomState.assignments().stream().map(RelayRoomAssignment::canvasIndex).distinct().sorted().toList();
    }

    /**
     * 갤러리 지급 대상인 참여자 UUID 목록을 중복 없이 추출합니다.
     */
    private List<String> findParticipantUuidValues(RelayRoomState roomState) {
        return roomState.participants().stream().filter(participant -> !participant.dropped())
            .map(RelayRoomParticipant::userUuid).distinct().toList();
    }

    /**
     * 기존 결과물이 현재 방의 canvasIndex 개수와 정확히 맞는지 확인합니다.
     */
    private boolean matchesExpectedCanvasIndexes(List<RelayFinalizationArtifactResult> existingArtifacts,
        List<Integer> canvasIndexes) {
        List<Integer> existingCanvasIndexes = existingArtifacts.stream()
            .map(RelayFinalizationArtifactResult::canvasIndex).distinct().sorted().toList();

        return existingArtifacts.size() == canvasIndexes.size() && existingCanvasIndexes.equals(canvasIndexes);
    }

    /**
     * 최종 결과물 원본/썸네일의 MinIO objectKey를 생성합니다.
     */
    private String createResultObjectKey(UUID artifactId, String fileName) {
        return "relay/results/%s/%s".formatted(artifactId, fileName);
    }

    /**
     * artifact.meta에 저장할 canvasIndex와 방 정보를 JSON으로 생성합니다.
     */
    private String createArtifactMeta(RelayRoomState roomState, int canvasIndex) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("canvasIndex", canvasIndex);
        meta.put("roomCode", roomState.roomCode());
        meta.put("parts",
            List.of(createPartMeta(roomState, canvasIndex, RelayDrawingPart.FACE),
                createPartMeta(roomState, canvasIndex, RelayDrawingPart.BODY),
                createPartMeta(roomState, canvasIndex, RelayDrawingPart.LEGS)));

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new InternalServerException(FINALIZATION_META_ERROR_MESSAGE, e);
        }
    }

    private Map<String, Object> createPartMeta(RelayRoomState roomState, int canvasIndex, RelayDrawingPart part) {
        RelayRoomAssignment assignment = findAssignment(roomState, canvasIndex, part);
        Map<String, Object> partMeta = new LinkedHashMap<>();
        partMeta.put("part", part.name());
        partMeta.put("drawerUserUuid", assignment.assignedUserUuid());
        partMeta.put("drawerNickname", findParticipantNickname(roomState, assignment.assignedUserUuid()));

        return partMeta;
    }

    private String findParticipantNickname(RelayRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(RelayRoomParticipant::nickname).findFirst().orElse(null);
    }
}
