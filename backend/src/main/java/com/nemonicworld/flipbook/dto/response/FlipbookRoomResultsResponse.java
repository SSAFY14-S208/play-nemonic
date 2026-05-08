package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 플립북 최종 결과 화면에서 사용하는 조회 응답입니다.
 */
@Schema(description = "플립북 결과 조회 응답")
public record FlipbookRoomResultsResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "Redis에 남아 있는 방 상태. Redis가 만료되었으면 null입니다.") FlipbookRoomStatus roomStatus,
    @Schema(description = "최종 결과 조회 가능 여부", example = "true") boolean ready,
    @Schema(description = "반환된 결과 개수", example = "2") int resultCount,
    @Schema(description = "flipbookIndex별 최종 결과 목록") List<FlipbookRoomResultItemResponse> results) {

    public FlipbookRoomResultsResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
