package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "무한 캔버스 revision 충돌 복구 응답")
public record InfiniteCanvasRevisionConflictResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "클라이언트가 기준으로 보낸 revision") long baseRevision,
    @Schema(description = "서버의 최신 revision") long latestRevision,
    @Schema(description = "baseRevision 이후 서버가 보관 중인 연산 목록") List<InfiniteCanvasOperation> missingOperations,
    @Schema(description = "delta 복구가 불가능해 전체 상태 재조회가 필요한지 여부") boolean fullStateRequired) {
}
