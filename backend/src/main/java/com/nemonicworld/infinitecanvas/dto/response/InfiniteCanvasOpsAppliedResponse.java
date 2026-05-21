package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "무한 캔버스 편집 연산 적용 응답")
public record InfiniteCanvasOpsAppliedResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "적용 후 revision") long revision, @Schema(description = "현재 요소 개수") int elementCount,
    @Schema(description = "서버가 확정한 편집 연산 목록") List<InfiniteCanvasOperation> operations) {
}
