package com.nemonicworld.relay.service.submission;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.request.RelayRoomSubmissionRequest;
import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.service.game.RelayPartAdvanceResult;
import com.nemonicworld.relay.service.game.RelayPartProgress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelaySubmissionAssignmentSupport {

    private static final String ASSIGNMENT_MISMATCH_MESSAGE = "현재 배정 정보와 일치하지 않습니다.";
    private static final String AUTO_SUBMITTED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String SUBMISSION_EXPIRED_MESSAGE = "제출 시간이 만료되었습니다.";

    private final Duration autoSubmitGrace;

    public RelaySubmissionAssignmentSupport(
        @Value("${nemonic.relay.timeout.auto-submit-grace-ms:2000}") long autoSubmitGraceMs) {
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
    }

    public RelayDrawingPart parsePart(RelayRoomSubmissionRequest request) {
        if (request == null || !StringUtils.hasText(request.part())) {
            throw new BadRequestException(ASSIGNMENT_MISMATCH_MESSAGE);
        }

        try {
            return RelayDrawingPart.valueOf(request.part().trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    public Optional<RelayRoomAssignment> findRequestedAssignment(RelayRoomState roomState, String viewerUserUuid,
        Integer requestedCanvasIndex, RelayDrawingPart requestedPart) {
        if (requestedCanvasIndex == null) {
            return Optional.empty();
        }

        return roomState.assignments().stream().filter(assignment -> assignment.canvasIndex() == requestedCanvasIndex)
            .filter(assignment -> assignment.part() == requestedPart)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst();
    }

    public boolean isDuplicateLookupAllowed(RelayRoomState roomState) {
        return roomState.status() == RelayRoomStatus.PLAYING || roomState.status() == RelayRoomStatus.FINALIZING;
    }

    public void validateAssignmentMatches(RelayRoomAssignment currentAssignment, Integer requestedCanvasIndex,
        RelayDrawingPart requestedPart) {
        if (requestedCanvasIndex == null || currentAssignment.canvasIndex() != requestedCanvasIndex
            || currentAssignment.part() != requestedPart) {
            throw new ConflictException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    public void validateNotAutoSubmitted(RelayRoomAssignment assignment) {
        if (assignment.status() == RelayAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted()) {
            throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
        }
    }

    public void validateDeadline(LocalDateTime partDeadlineAt) {
        if (partDeadlineAt == null) {
            return;
        }

        LocalDateTime expiresAt = partDeadlineAt.plus(autoSubmitGrace);
        if (!expiresAt.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflictException(SUBMISSION_EXPIRED_MESSAGE);
        }
    }

    public RelayRoomAssignment submitAssignment(RelayRoomAssignment currentAssignment, String drawingObjectKey,
        String hintObjectKey, LocalDateTime submittedAt) {
        return new RelayRoomAssignment(currentAssignment.canvasIndex(), currentAssignment.part(),
            currentAssignment.assignedUserUuid(), RelayAssignmentStatus.SUBMITTED, currentAssignment.fileId(),
            drawingObjectKey, hintObjectKey, false, false, submittedAt);
    }

    public List<RelayRoomAssignment> replaceAssignment(List<RelayRoomAssignment> assignments,
        RelayRoomAssignment currentAssignment, RelayRoomAssignment submittedAssignment) {
        List<RelayRoomAssignment> updatedAssignments = new ArrayList<>(assignments.size());
        for (RelayRoomAssignment assignment : assignments) {
            if (assignment.canvasIndex() == currentAssignment.canvasIndex()
                && assignment.part() == currentAssignment.part()
                && assignment.assignedUserUuid().equals(currentAssignment.assignedUserUuid())) {
                updatedAssignments.add(submittedAssignment);
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return updatedAssignments;
    }

    public RelayRoomSubmissionResponse createResponse(RelayRoomState roomState, RelayRoomAssignment assignment,
        boolean alreadySubmitted, String userUuid, String nickname, RelayPartAdvanceResult advanceResult) {
        RelayPartProgress progress = advanceResult.progress();

        return RelayRoomSubmissionResponse.from(roomState.roomCode(), assignment, alreadySubmitted,
            progress.currentPartCompleted(), progress.submittedCount(), progress.totalCount(), userUuid, nickname,
            advanceResult.advanced(), advanceResult.nextPart(), advanceResult.nextPartStartedAt(),
            advanceResult.nextPartDeadlineAt(), advanceResult.allPartsCompleted(), advanceResult.roomState().status());
    }
}
