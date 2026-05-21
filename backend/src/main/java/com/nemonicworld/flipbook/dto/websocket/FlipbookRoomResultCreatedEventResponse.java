package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.finalization.FlipbookRoomFinalizationResult;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "플립북 최종 GIF 결과 생성 완료 WebSocket 이벤트")
public record FlipbookRoomResultCreatedEventResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "방 상태", example = "FINISHED") FlipbookRoomStatus roomStatus,
    @Schema(description = "생성된 산출물 ID 목록") List<UUID> artifactIds,
    @Schema(description = "생성 결과물 수", example = "4") int resultCount,
    @Schema(description = "생성 완료 시각", example = "2026-05-08T14:03:00") LocalDateTime createdAt,
    @Schema(description = "플립북 번호별 결과 목록") List<Result> results) {

    public FlipbookRoomResultCreatedEventResponse {
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static FlipbookRoomResultCreatedEventResponse from(FlipbookRoomFinalizationResult result) {
        List<UUID> artifactIds = result.artifacts().stream().map(FlipbookResultArtifactResult::artifactId).toList();
        List<Result> results = result.artifacts().stream().map(Result::from).toList();

        return new FlipbookRoomResultCreatedEventResponse(result.roomCode(), result.roomStatus(), artifactIds,
            result.resultCount(), result.createdAt(), results);
    }

    public record Result(@Schema(description = "플립북 번호", example = "0") int flipbookIndex,
        @Schema(description = "산출물 ID") UUID artifactId, @Schema(description = "최종 썸네일 객체 키") String thumbnailUrl,
        @Schema(description = "최종 GIF 객체 키") String gifUrl, @Schema(description = "첫 프레임 객체 키") String firstImageUrl) {

        private static Result from(FlipbookResultArtifactResult artifact) {
            return new Result(artifact.flipbookIndex(), artifact.artifactId(), artifact.thumbnailObjectKey(),
                artifact.gifObjectKey(), artifact.firstImageObjectKey());
        }
    }
}
