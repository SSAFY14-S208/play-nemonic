package com.nemonicworld.relay.service.game;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import java.util.ArrayList;
import java.util.List;

/**
 * 게임 시작 시점의 릴레이 드로잉 파트 배정표를 생성합니다.
 */
final class RelayRoomAssignmentGenerator {

    private static final List<RelayDrawingPart> DRAWING_PARTS = List.of(RelayDrawingPart.FACE, RelayDrawingPart.BODY,
        RelayDrawingPart.LEGS);

    private RelayRoomAssignmentGenerator() {
    }

    static List<RelayRoomAssignment> generate(List<RelayRoomParticipant> startParticipants) {
        int participantCount = startParticipants.size();
        List<RelayRoomAssignment> assignments = new ArrayList<>(participantCount * DRAWING_PARTS.size());

        for (int canvasIndex = 0; canvasIndex < participantCount; canvasIndex++) {
            for (int partIndex = 0; partIndex < DRAWING_PARTS.size(); partIndex++) {
                RelayRoomParticipant assignedParticipant = startParticipants
                    .get((canvasIndex + partIndex) % participantCount);
                assignments.add(
                    new RelayRoomAssignment(canvasIndex, DRAWING_PARTS.get(partIndex), assignedParticipant.userUuid(),
                        RelayAssignmentStatus.PENDING, null, null, null, false, false, null));
            }
        }

        return assignments;
    }
}
