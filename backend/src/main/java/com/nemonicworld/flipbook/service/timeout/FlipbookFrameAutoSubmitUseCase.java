package com.nemonicworld.flipbook.service.timeout;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.repository.FlipbookSubmissionLockRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FlipbookFrameAutoSubmitUseCase {

    private final FlipbookSubmissionLockRepository flipbookSubmissionLockRepository;

    public FlipbookFrameAutoSubmitUseCase(FlipbookSubmissionLockRepository flipbookSubmissionLockRepository) {
        this.flipbookSubmissionLockRepository = flipbookSubmissionLockRepository;
    }

    public FlipbookFrameAutoSubmitUpdate autoSubmitPendingAssignments(FlipbookRoomState roomState, int currentRound,
        LocalDateTime submittedAt) {
        List<FlipbookFrameAssignment> assignments = new ArrayList<>(roomState.assignments().size());
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (assignment.round() == currentRound && assignment.status() == FlipbookFrameAssignmentStatus.PENDING
                && !isSubmissionLocked(roomState, assignment)) {
                FlipbookFrameAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                assignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new FlipbookFrameAutoSubmissionResult(roomState.roomCode(),
                    findNickname(roomState.participants(), assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                assignments.add(assignment);
            }
        }

        return new FlipbookFrameAutoSubmitUpdate(assignments, autoSubmissions);
    }

    public FlipbookFrameAutoSubmitUpdate autoSubmitDroppedCurrentAssignments(FlipbookRoomState roomState,
        List<FlipbookRoomParticipant> participants, LocalDateTime submittedAt) {
        Set<String> droppedUserUuids = droppedUserUuids(participants);

        if (droppedUserUuids.isEmpty()) {
            return new FlipbookFrameAutoSubmitUpdate(roomState.assignments(), List.of());
        }

        int currentRound = roomState.currentRound();
        List<FlipbookFrameAssignment> assignments = new ArrayList<>(roomState.assignments().size());
        List<FlipbookFrameAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (FlipbookFrameAssignment assignment : roomState.assignments()) {
            if (assignment.round() == currentRound && assignment.status() == FlipbookFrameAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid())
                && !isSubmissionLocked(roomState, assignment)) {
                FlipbookFrameAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                assignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new FlipbookFrameAutoSubmissionResult(roomState.roomCode(),
                    findNickname(participants, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                assignments.add(assignment);
            }
        }

        return new FlipbookFrameAutoSubmitUpdate(assignments, autoSubmissions);
    }

    private Set<String> droppedUserUuids(List<FlipbookRoomParticipant> participants) {
        Set<String> droppedUserUuids = new HashSet<>();
        for (FlipbookRoomParticipant participant : participants) {
            if (participant.dropped()) {
                droppedUserUuids.add(participant.userUuid());
            }
        }

        return droppedUserUuids;
    }

    private boolean isSubmissionLocked(FlipbookRoomState roomState, FlipbookFrameAssignment assignment) {
        return flipbookSubmissionLockRepository.isSubmissionLocked(roomState.roomCode(), assignment.flipbookIndex(),
            assignment.frameIndex(), assignment.round(), assignment.assignedUserUuid());
    }

    private FlipbookFrameAssignment autoSubmitAssignment(FlipbookFrameAssignment assignment,
        LocalDateTime submittedAt) {
        return new FlipbookFrameAssignment(assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(),
            assignment.assignedUserUuid(), FlipbookFrameAssignmentStatus.AUTO_SUBMITTED, null, null, true, true,
            submittedAt);
    }

    private String findNickname(List<FlipbookRoomParticipant> participants, String userUuid) {
        return participants.stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(FlipbookRoomParticipant::nickname).findFirst().orElse(null);
    }
}
