package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 릴레이 최종 결과 화면에서 사용하는 조회 응답입니다.
 */
@Schema(description = "릴레이 결과 조회 응답")
public record RelayRoomResultsResponse(@Schema(description = "공유 방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "Redis에 남아 있는 방 상태. Redis가 만료되었으면 null입니다.", example = "FINISHED") RelayRoomStatus roomStatus,
    @Schema(description = "최종 결과 조회 가능 여부", example = "true") boolean ready,
    @Schema(description = "반환된 결과 개수", example = "3") int resultCount,
    @Schema(description = "canvasIndex별 최종 결과 목록") List<RelayRoomResultItemResponse> results) {
}
