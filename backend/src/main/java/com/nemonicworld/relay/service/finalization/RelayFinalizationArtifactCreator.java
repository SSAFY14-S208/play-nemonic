package com.nemonicworld.relay.service.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelayFinalizationArtifactCreator {

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String FINALIZATION_STATE_ERROR_MESSAGE = "릴레이 최종화 상태가 올바르지 않습니다.";
    private static final String FINALIZATION_META_ERROR_MESSAGE = "릴레이 최종화 메타데이터를 생성할 수 없습니다.";

    private final RelayResultStorage relayResultStorage;
    private final RelayResultComposer relayResultComposer;
    private final ObjectMapper objectMapper;

    public RelayFinalizationArtifactCreator(RelayResultStorage relayResultStorage,
        RelayResultComposer relayResultComposer, ObjectMapper objectMapper) {
        this.relayResultStorage = relayResultStorage;
        this.relayResultComposer = relayResultComposer;
        this.objectMapper = objectMapper;
    }

    public List<RelayFinalizationArtifactResult> createAndUploadResults(RelayRoomState roomState,
        List<Integer> canvasIndexes, Consumer<String> objectKeyRegistrar, FailureReporter failureReporter) {
        return canvasIndexes.stream()
            .map(canvasIndex -> createAndUploadResult(roomState, canvasIndex, objectKeyRegistrar, failureReporter))
            .toList();
    }

    private RelayFinalizationArtifactResult createAndUploadResult(RelayRoomState roomState, int canvasIndex,
        Consumer<String> objectKeyRegistrar, FailureReporter failureReporter) {
        UUID artifactId = UUID.randomUUID();
        String originalObjectKey = createResultObjectKey(artifactId, "original.png");
        String thumbnailObjectKey = createResultObjectKey(artifactId, "thumbnail.png");
        RelayComposedImage composedImage;
        try {
            composedImage = relayResultComposer.compose(loadPartImages(roomState, canvasIndex));
        } catch (RuntimeException e) {
            failureReporter.report("compose", artifactId, e);
            throw e;
        }

        try {
            relayResultStorage.upload(originalObjectKey, composedImage.originalPng(), PNG_CONTENT_TYPE);
            objectKeyRegistrar.accept(originalObjectKey);
            relayResultStorage.upload(thumbnailObjectKey, composedImage.thumbnailPng(), PNG_CONTENT_TYPE);
            objectKeyRegistrar.accept(thumbnailObjectKey);
        } catch (RuntimeException e) {
            failureReporter.report("minio_upload", artifactId, e);
            throw e;
        }

        try {
            return new RelayFinalizationArtifactResult(artifactId, canvasIndex, originalObjectKey, thumbnailObjectKey,
                createArtifactMeta(roomState, canvasIndex));
        } catch (RuntimeException e) {
            failureReporter.report("artifact_meta", artifactId, e);
            throw e;
        }
    }

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

    private RelayRoomAssignment findAssignment(RelayRoomState roomState, int canvasIndex, RelayDrawingPart part) {
        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == canvasIndex)
            .filter(assignment -> assignment.part() == part).findFirst()
            .orElseThrow(() -> new InternalServerException(FINALIZATION_STATE_ERROR_MESSAGE));
    }

    private boolean isEmptyAssignment(RelayRoomAssignment assignment) {
        return assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.empty()
            || assignment.autoSubmitted();
    }

    private String createResultObjectKey(UUID artifactId, String fileName) {
        return "relay/results/%s/%s".formatted(artifactId, fileName);
    }

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

    @FunctionalInterface
    public interface FailureReporter {

        void report(String stage, UUID artifactId, RuntimeException error);
    }
}
