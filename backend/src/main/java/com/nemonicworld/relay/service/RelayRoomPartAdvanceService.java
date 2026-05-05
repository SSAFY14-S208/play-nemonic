package com.nemonicworld.relay.service;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomState;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 릴레이 파트 완료 여부를 계산하고 다음 파트로 전환합니다.
 */
@Component
public class RelayRoomPartAdvanceService {

    /**
     * 완료된 파트를 기준으로 다음 파트 또는 최종 합성 준비 상태로 전환합니다.
     */
    public RelayPartAdvanceResult advancePartIfCompleted(RelayRoomState roomState, RelayDrawingPart completedPart,
        LocalDateTime now) {
        RelayPartProgress progress = calculateProgress(roomState, completedPart);
        if (!progress.currentPartCompleted()) {
            return RelayPartAdvanceResult.notAdvanced(roomState, progress);
        }

        if (completedPart == RelayDrawingPart.FACE) {
            RelayRoomState advancedRoomState = roomState.startPart(RelayDrawingPart.BODY, now);
            return RelayPartAdvanceResult.advanced(advancedRoomState, RelayDrawingPart.BODY, progress);
        }

        if (completedPart == RelayDrawingPart.BODY) {
            RelayRoomState advancedRoomState = roomState.startPart(RelayDrawingPart.LEGS, now);
            return RelayPartAdvanceResult.advanced(advancedRoomState, RelayDrawingPart.LEGS, progress);
        }

        RelayRoomState finalizedRoomState = roomState.finalizeParts(now);
        return RelayPartAdvanceResult.allPartsCompleted(finalizedRoomState, progress);
    }

    /**
     * 특정 파트의 전체 배정 수와 제출 완료 수를 계산합니다.
     */
    public RelayPartProgress calculateProgress(RelayRoomState roomState, RelayDrawingPart part) {
        int totalCount = 0;
        int submittedCount = 0;

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() != part) {
                continue;
            }

            totalCount++;
            if (isCompleted(assignment)) {
                submittedCount++;
            }
        }

        return new RelayPartProgress(submittedCount, totalCount, totalCount > 0 && submittedCount == totalCount);
    }

    /**
     * 수동 제출과 자동 제출을 모두 제출 완료 상태로 봅니다.
     */
    boolean isCompleted(RelayRoomAssignment assignment) {
        return assignment.status() == RelayAssignmentStatus.SUBMITTED
            || assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted();
    }
}
