package com.nemonicworld.flipbook.service.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.service.result.FlipbookGifComposer;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import com.nemonicworld.flipbook.service.result.FlipbookResultStorage;
import com.nemonicworld.flipbook.service.result.FlipbookThumbnailComposer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FlipbookFinalizationArtifactCreator {

    private static final String GIF_CONTENT_TYPE = "image/gif";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String FINALIZATION_META_ERROR_MESSAGE = "플립북 최종화 메타데이터를 생성할 수 없습니다.";

    private final FlipbookResultStorage flipbookResultStorage;
    private final FlipbookGifComposer flipbookGifComposer;
    private final FlipbookThumbnailComposer flipbookThumbnailComposer;
    private final ObjectMapper objectMapper;

    public FlipbookFinalizationArtifactCreator(FlipbookResultStorage flipbookResultStorage,
        FlipbookGifComposer flipbookGifComposer, FlipbookThumbnailComposer flipbookThumbnailComposer,
        ObjectMapper objectMapper) {
        this.flipbookResultStorage = flipbookResultStorage;
        this.flipbookGifComposer = flipbookGifComposer;
        this.flipbookThumbnailComposer = flipbookThumbnailComposer;
        this.objectMapper = objectMapper;
    }

    public List<FlipbookResultArtifactResult> createAndUploadResults(FlipbookRoomState roomState) {
        Map<Integer, List<FrameSource>> groupedFrameSources = groupFrameSourcesByFlipbookIndex(roomState);

        return groupedFrameSources.entrySet().stream()
            .map(entry -> createAndUploadResult(roomState, entry.getKey(), entry.getValue())).toList();
    }

    public List<Integer> findResultFlipbookIndexes(FlipbookRoomState roomState) {
        return roomState.assignments().stream().filter(this::isResultFrame).map(FlipbookFrameAssignment::flipbookIndex)
            .distinct().sorted().toList();
    }

    private FlipbookResultArtifactResult createAndUploadResult(FlipbookRoomState roomState, int flipbookIndex,
        List<FrameSource> frameSources) {
        UUID artifactId = UUID.randomUUID();
        String gifObjectKey = createResultObjectKey(artifactId, "result.gif");
        String thumbnailObjectKey = createResultObjectKey(artifactId, "thumbnail.png");
        String firstImageObjectKey = frameSources.get(0).objectKey();
        List<byte[]> frameImageBytes = frameSources.stream().map(FrameSource::objectKey)
            .map(flipbookResultStorage::download).toList();
        byte[] gifBytes = flipbookGifComposer.compose(frameImageBytes);
        byte[] thumbnailBytes = flipbookThumbnailComposer.compose(frameImageBytes.get(0));

        flipbookResultStorage.upload(gifObjectKey, gifBytes, GIF_CONTENT_TYPE);
        flipbookResultStorage.upload(thumbnailObjectKey, thumbnailBytes, PNG_CONTENT_TYPE);

        return new FlipbookResultArtifactResult(artifactId, flipbookIndex, gifObjectKey, firstImageObjectKey,
            thumbnailObjectKey, createArtifactMeta(roomState, flipbookIndex, frameSources));
    }

    private Map<Integer, List<FrameSource>> groupFrameSourcesByFlipbookIndex(FlipbookRoomState roomState) {
        Map<Integer, List<FrameSource>> groupedFrameSources = new TreeMap<>();
        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (!isResultFrame(assignment)) {
                continue;
            }

            groupedFrameSources.computeIfAbsent(assignment.flipbookIndex(), key -> new ArrayList<>())
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
            throw new InternalServerException(FINALIZATION_META_ERROR_MESSAGE, e);
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

    private String createResultObjectKey(UUID artifactId, String fileName) {
        return "flipbook/results/%s/%s".formatted(artifactId, fileName);
    }

    private String findParticipantNickname(FlipbookRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse(null);
    }

    private record FrameSource(int frameIndex, String objectKey, String drawnByUserUuid, String drawnByNickname) {
    }
}
