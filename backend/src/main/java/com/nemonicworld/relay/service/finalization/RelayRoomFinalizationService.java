package com.nemonicworld.relay.service.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayArtifactRepository;
import com.nemonicworld.relay.repository.RelayRoomRepository;
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
    private final int scanLimit;
    private final Duration lockTtl;

    public RelayRoomFinalizationService(RelayRoomRepository relayRoomRepository,
        RelayArtifactRepository relayArtifactRepository, RelayResultStorage relayResultStorage,
        RelayResultComposer relayResultComposer, RelayRoomEventPublisher relayRoomEventPublisher,
        ObjectMapper objectMapper, @Value("${nemonic.relay.finalization.scan-limit:50}") int scanLimit,
        @Value("${nemonic.relay.finalization.lock-ttl-seconds:60}") long lockTtlSeconds) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayArtifactRepository = relayArtifactRepository;
        this.relayResultStorage = relayResultStorage;
        this.relayResultComposer = relayResultComposer;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.objectMapper = objectMapper;
        this.scanLimit = scanLimit;
        this.lockTtl = Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
    }

    public RelayFinalizationProcessResult processFinalizingRooms() {
        List<RelayRoomState> finalizingRooms = relayRoomRepository.findFinalizingRooms(scanLimit);
        int processedRoomCount = 0;
        int resultCount = 0;

        for (RelayRoomState finalizingRoom : finalizingRooms) {
            try {
                RelayRoomFinalizationResult result = processFinalizingRoom(finalizingRoom.roomCode());
                if (result.processed()) {
                    processedRoomCount++;
                    resultCount += result.resultCount();
                }
            } catch (RuntimeException e) {
                log.warn("릴레이 최종 결과물 생성 중 오류가 발생했습니다. roomCode={}", finalizingRoom.roomCode(), e);
            }
        }

        return new RelayFinalizationProcessResult(finalizingRooms.size(), processedRoomCount, resultCount);
    }

    public RelayRoomFinalizationResult processFinalizingRoom(String roomCode) {
        if (!relayRoomRepository.acquireFinalizationLock(roomCode, lockTtl)) {
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        try {
            return processLockedFinalizingRoom(roomCode);
        } finally {
            relayRoomRepository.releaseFinalizationLock(roomCode);
        }
    }

    private RelayRoomFinalizationResult processLockedFinalizingRoom(String roomCode) {
        RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
        if (roomState == null || roomState.status() != RelayRoomStatus.FINALIZING) {
            return RelayRoomFinalizationResult.noOp(roomCode);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<Integer> canvasIndexes = findCanvasIndexes(roomState);
        if (canvasIndexes.isEmpty()) {
            throw new IllegalStateException(FINALIZATION_STATE_ERROR_MESSAGE);
        }

        List<RelayFinalizationArtifactResult> existingArtifacts = relayArtifactRepository
            .findRelayArtifactsBySourceRoomId(roomCode);
        List<RelayFinalizationArtifactResult> artifacts = resolveArtifacts(roomState, canvasIndexes, existingArtifacts,
            now);
        RelayRoomState finishedRoomState = roomState.finish(now);
        if (!relayRoomRepository.saveIfUnchanged(roomState, finishedRoomState)) {
            throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
        }

        RelayRoomFinalizationResult result = RelayRoomFinalizationResult.finished(roomCode, artifacts, now);
        relayRoomEventPublisher.publishResultCreated(result);

        return result;
    }

    private List<RelayFinalizationArtifactResult> resolveArtifacts(RelayRoomState roomState,
        List<Integer> canvasIndexes, List<RelayFinalizationArtifactResult> existingArtifacts, LocalDateTime now) {
        if (existingArtifacts.isEmpty()) {
            List<RelayFinalizationArtifactResult> artifacts = createAndUploadResults(roomState, canvasIndexes);
            relayArtifactRepository.saveRelayDrawingResults(roomState.roomCode(), artifacts,
                findParticipantUuidValues(roomState), now);

            return artifacts;
        }

        if (matchesExpectedCanvasIndexes(existingArtifacts, canvasIndexes)) {
            return existingArtifacts;
        }

        throw new IllegalStateException(FINALIZATION_STATE_ERROR_MESSAGE);
    }

    private List<RelayFinalizationArtifactResult> createAndUploadResults(RelayRoomState roomState,
        List<Integer> canvasIndexes) {
        return canvasIndexes.stream().map(canvasIndex -> createAndUploadResult(roomState, canvasIndex)).toList();
    }

    private RelayFinalizationArtifactResult createAndUploadResult(RelayRoomState roomState, int canvasIndex) {
        UUID artifactId = UUID.randomUUID();
        String originalObjectKey = createResultObjectKey(artifactId, "original.png");
        String thumbnailObjectKey = createResultObjectKey(artifactId, "thumbnail.png");
        RelayComposedImage composedImage = relayResultComposer.compose(loadPartImages(roomState, canvasIndex));

        relayResultStorage.upload(originalObjectKey, composedImage.originalPng(), PNG_CONTENT_TYPE);
        relayResultStorage.upload(thumbnailObjectKey, composedImage.thumbnailPng(), PNG_CONTENT_TYPE);

        return new RelayFinalizationArtifactResult(artifactId, canvasIndex, originalObjectKey, thumbnailObjectKey,
            createArtifactMeta(roomState.roomCode(), canvasIndex));
    }

    private Map<RelayDrawingPart, byte[]> loadPartImages(RelayRoomState roomState, int canvasIndex) {
        Map<RelayDrawingPart, byte[]> partImages = new EnumMap<>(RelayDrawingPart.class);
        for (RelayDrawingPart part : RelayDrawingPart.values()) {
            RelayRoomAssignment assignment = findAssignment(roomState, canvasIndex, part);
            if (isEmptyAssignment(assignment)) {
                continue;
            }

            if (assignment.status() != RelayAssignmentStatus.SUBMITTED) {
                throw new IllegalStateException(FINALIZATION_STATE_ERROR_MESSAGE);
            }

            if (!StringUtils.hasText(assignment.objectKey())) {
                throw new IllegalStateException(FINALIZATION_STATE_ERROR_MESSAGE);
            }

            partImages.put(part, relayResultStorage.download(assignment.objectKey()));
        }

        return partImages;
    }

    private RelayRoomAssignment findAssignment(RelayRoomState roomState, int canvasIndex, RelayDrawingPart part) {
        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == canvasIndex)
            .filter(assignment -> assignment.part() == part).findFirst()
            .orElseThrow(() -> new IllegalStateException(FINALIZATION_STATE_ERROR_MESSAGE));
    }

    private boolean isEmptyAssignment(RelayRoomAssignment assignment) {
        return assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.empty()
            || assignment.autoSubmitted();
    }

    private List<Integer> findCanvasIndexes(RelayRoomState roomState) {
        return roomState.assignments().stream().map(RelayRoomAssignment::canvasIndex).distinct().sorted().toList();
    }

    private List<String> findParticipantUuidValues(RelayRoomState roomState) {
        return roomState.participants().stream().map(RelayRoomParticipant::userUuid).distinct().toList();
    }

    private boolean matchesExpectedCanvasIndexes(List<RelayFinalizationArtifactResult> existingArtifacts,
        List<Integer> canvasIndexes) {
        List<Integer> existingCanvasIndexes = existingArtifacts.stream()
            .map(RelayFinalizationArtifactResult::canvasIndex).distinct().sorted().toList();

        return existingArtifacts.size() == canvasIndexes.size() && existingCanvasIndexes.equals(canvasIndexes);
    }

    private String createResultObjectKey(UUID artifactId, String fileName) {
        return "relay/results/%s/%s".formatted(artifactId, fileName);
    }

    private String createArtifactMeta(String roomCode, int canvasIndex) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("canvasIndex", canvasIndex);
        meta.put("roomCode", roomCode);
        meta.put("parts",
            List.of(RelayDrawingPart.FACE.name(), RelayDrawingPart.BODY.name(), RelayDrawingPart.LEGS.name()));

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(FINALIZATION_META_ERROR_MESSAGE, e);
        }
    }
}
