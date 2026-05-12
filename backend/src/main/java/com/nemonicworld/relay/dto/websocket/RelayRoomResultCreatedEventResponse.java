package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.service.finalization.RelayFinalizationArtifactResult;
import com.nemonicworld.relay.service.finalization.RelayRoomFinalizationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "릴레이 최종 결과 생성 완료 WebSocket 이벤트")
public record RelayRoomResultCreatedEventResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "방 상태", example = "FINISHED") RelayRoomStatus roomStatus,
    @Schema(description = "생성된 산출물 ID 목록") List<UUID> artifactIds,
    @Schema(description = "생성 결과물 수", example = "3") int resultCount,
    @Schema(description = "생성 완료 시각", example = "2026-05-06T14:00:00") LocalDateTime createdAt,
    @Schema(description = "캔버스 번호별 결과 목록") List<Result> results) {

    public RelayRoomResultCreatedEventResponse {
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static RelayRoomResultCreatedEventResponse from(RelayRoomFinalizationResult result) {
        List<UUID> artifactIds = result.artifacts().stream().map(RelayFinalizationArtifactResult::artifactId).toList();
        List<Result> results = result.artifacts().stream().map(Result::from).toList();

        return new RelayRoomResultCreatedEventResponse(result.roomCode(), result.roomStatus(), artifactIds,
            result.resultCount(), result.createdAt(), results);
    }

    public record Result(@Schema(description = "캔버스 번호", example = "0") int canvasIndex,
        @Schema(description = "산출물 ID") UUID artifactId, @Schema(description = "최종 썸네일 객체 키") String thumbnailUrl,
        @Schema(description = "최종 원본 객체 키") String contentUrl) {

        private static Result from(RelayFinalizationArtifactResult artifact) {
            return new Result(artifact.canvasIndex(), artifact.artifactId(), artifact.thumbnailObjectKey(),
                artifact.originalObjectKey());
        }
    }
}
