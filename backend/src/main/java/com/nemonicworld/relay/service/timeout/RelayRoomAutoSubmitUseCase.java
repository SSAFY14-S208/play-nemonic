package com.nemonicworld.relay.service.timeout;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelaySubmissionLockRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomAutoSubmitUseCase {

    private final RelaySubmissionLockRepository relaySubmissionLockRepository;

    public RelayRoomAutoSubmitUseCase(RelaySubmissionLockRepository relaySubmissionLockRepository) {
        this.relaySubmissionLockRepository = relaySubmissionLockRepository;
    }

    public RelayRoomAutoSubmitUpdate autoSubmitPendingAssignments(RelayRoomState roomState,
        RelayDrawingPart currentPart, LocalDateTime submittedAt) {
        List<RelayRoomAssignment> assignments = new ArrayList<>(roomState.assignments().size());
        List<RelayRoomAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() == currentPart && assignment.status() == RelayAssignmentStatus.PENDING
                && !isSubmissionLocked(roomState, assignment)) {
                RelayRoomAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                assignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new RelayRoomAutoSubmissionResult(roomState.roomCode(),
                    findNickname(roomState.participants(), assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                assignments.add(assignment);
            }
        }

        return new RelayRoomAutoSubmitUpdate(assignments, autoSubmissions);
    }

    public RelayRoomAutoSubmitUpdate autoSubmitDroppedCurrentAssignments(RelayRoomState roomState,
        List<RelayRoomParticipant> participants, LocalDateTime submittedAt) {
        Set<String> droppedUserUuids = droppedUserUuids(participants);

        if (droppedUserUuids.isEmpty()) {
            return new RelayRoomAutoSubmitUpdate(roomState.assignments(), List.of());
        }

        RelayDrawingPart currentPart = roomState.currentPart();
        List<RelayRoomAssignment> assignments = new ArrayList<>(roomState.assignments().size());
        List<RelayRoomAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() == currentPart && assignment.status() == RelayAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid())
                && !isSubmissionLocked(roomState, assignment)) {
                RelayRoomAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                assignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new RelayRoomAutoSubmissionResult(roomState.roomCode(),
                    findNickname(participants, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                assignments.add(assignment);
            }
        }

        return new RelayRoomAutoSubmitUpdate(assignments, autoSubmissions);
    }

    private Set<String> droppedUserUuids(List<RelayRoomParticipant> participants) {
        Set<String> droppedUserUuids = new HashSet<>();
        for (RelayRoomParticipant participant : participants) {
            if (participant.dropped()) {
                droppedUserUuids.add(participant.userUuid());
            }
        }

        return droppedUserUuids;
    }

    private boolean isSubmissionLocked(RelayRoomState roomState, RelayRoomAssignment assignment) {
        return relaySubmissionLockRepository.isSubmissionLocked(roomState.roomCode(), assignment.canvasIndex(),
            assignment.part(), assignment.assignedUserUuid());
    }

    private RelayRoomAssignment autoSubmitAssignment(RelayRoomAssignment assignment, LocalDateTime submittedAt) {
        return new RelayRoomAssignment(assignment.canvasIndex(), assignment.part(), assignment.assignedUserUuid(),
            RelayAssignmentStatus.AUTO_SUBMITTED, assignment.fileId(), null, null, true, true, submittedAt);
    }

    private String findNickname(List<RelayRoomParticipant> participants, String userUuid) {
        return participants.stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(RelayRoomParticipant::nickname).findFirst().orElse(null);
    }
}
