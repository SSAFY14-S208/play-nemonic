package com.nemonicworld.flipbook.service.finalization;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.service.result.FlipbookResultArtifactResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FINALIZING 방 처리 결과와 WebSocket 이벤트 payload에 필요한 정보를 담습니다.
 */
public record FlipbookRoomFinalizationResult(String roomCode, boolean processed, FlipbookRoomStatus roomStatus,
    List<FlipbookResultArtifactResult> artifacts, LocalDateTime createdAt) {

    public FlipbookRoomFinalizationResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static FlipbookRoomFinalizationResult noOp(String roomCode) {
        return new FlipbookRoomFinalizationResult(roomCode, false, null, List.of(), null);
    }

    public static FlipbookRoomFinalizationResult finished(String roomCode, List<FlipbookResultArtifactResult> artifacts,
        LocalDateTime createdAt) {
        return new FlipbookRoomFinalizationResult(roomCode, true, FlipbookRoomStatus.FINISHED, artifacts, createdAt);
    }

    public static FlipbookRoomFinalizationResult closed(String roomCode, LocalDateTime closedAt) {
        return new FlipbookRoomFinalizationResult(roomCode, true, FlipbookRoomStatus.CLOSED, List.of(), closedAt);
    }

    public int resultCount() {
        return artifacts.size();
    }
}
