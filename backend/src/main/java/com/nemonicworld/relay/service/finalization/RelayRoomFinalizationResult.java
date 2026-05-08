package com.nemonicworld.relay.service.finalization;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FINALIZING 방 처리 결과와 WebSocket 이벤트 payload에 필요한 정보를 담습니다.
 */
public record RelayRoomFinalizationResult(String roomCode, boolean processed, RelayRoomStatus roomStatus,
    List<RelayFinalizationArtifactResult> artifacts, LocalDateTime createdAt) {

    public RelayRoomFinalizationResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static RelayRoomFinalizationResult noOp(String roomCode) {
        return new RelayRoomFinalizationResult(roomCode, false, null, List.of(), null);
    }

    public static RelayRoomFinalizationResult finished(String roomCode, List<RelayFinalizationArtifactResult> artifacts,
        LocalDateTime createdAt) {
        return new RelayRoomFinalizationResult(roomCode, true, RelayRoomStatus.FINISHED, artifacts, createdAt);
    }

    public int resultCount() {
        return artifacts.size();
    }
}
