package com.nemonicworld.relay.service.game;

import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import java.time.LocalDateTime;

/**
 * 릴레이 파트 완료 후 다음 단계 전환 결과를 담습니다.
 */
public record RelayPartAdvanceResult(RelayRoomState roomState, boolean advanced, RelayDrawingPart nextPart,
    LocalDateTime nextPartStartedAt, LocalDateTime nextPartDeadlineAt, boolean allPartsCompleted,
    RelayPartProgress progress) {

    public static RelayPartAdvanceResult notAdvanced(RelayRoomState roomState, RelayPartProgress progress) {
        return new RelayPartAdvanceResult(roomState, false, null, null, null,
            roomState.status() == RelayRoomStatus.FINALIZING, progress);
    }

    public static RelayPartAdvanceResult advanced(RelayRoomState roomState, RelayDrawingPart nextPart,
        RelayPartProgress progress) {
        return new RelayPartAdvanceResult(roomState, true, nextPart, roomState.partStartedAt(),
            roomState.partDeadlineAt(), false, progress);
    }

    public static RelayPartAdvanceResult allPartsCompleted(RelayRoomState roomState, RelayPartProgress progress) {
        return new RelayPartAdvanceResult(roomState, true, null, null, null, true, progress);
    }
}
