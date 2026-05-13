package com.nemonicworld.flipbook.service.game;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import java.util.ArrayList;
import java.util.List;

/**
 * 게임 시작 시점의 플립북 프레임 배정표를 생성합니다.
 */
final class FlipbookFrameAssignmentGenerator {

    private FlipbookFrameAssignmentGenerator() {
    }

    static List<FlipbookFrameAssignment> generate(List<FlipbookRoomParticipant> startParticipants, int totalRounds) {
        int participantCount = startParticipants.size();
        List<FlipbookFrameAssignment> assignments = new ArrayList<>(participantCount * totalRounds);

        for (int flipbookIndex = 0; flipbookIndex < participantCount; flipbookIndex++) {
            for (int round = 1; round <= totalRounds; round++) {
                FlipbookRoomParticipant assignedParticipant = startParticipants
                    .get((flipbookIndex + round - 1) % participantCount);
                assignments
                    .add(new FlipbookFrameAssignment(flipbookIndex, round - 1, round, assignedParticipant.userUuid(),
                        FlipbookFrameAssignmentStatus.PENDING, null, null, false, false, null));
            }
        }

        return assignments;
    }
}
