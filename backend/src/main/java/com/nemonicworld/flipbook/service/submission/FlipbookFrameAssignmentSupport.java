package com.nemonicworld.flipbook.service.submission;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.files.entity.FileUpload;
import com.nemonicworld.flipbook.dto.request.FlipbookFrameSubmitRequest;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
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
public class FlipbookFrameAssignmentSupport {

    private static final String INVALID_ROUND_MESSAGE = "유효하지 않은 라운드입니다.";
    private static final String ASSIGNMENT_MISMATCH_MESSAGE = "현재 배정 정보와 일치하지 않습니다.";
    private static final String AUTO_SUBMITTED_MESSAGE = "이미 자동 제출 처리되었습니다.";
    private static final String SUBMISSION_EXPIRED_MESSAGE = "제출 시간이 만료되었습니다.";

    private final Duration autoSubmitGrace;

    public FlipbookFrameAssignmentSupport(
        @Value("${nemonic.flipbook.timeout.auto-submit-grace-ms:5000}") long autoSubmitGraceMs) {
        this.autoSubmitGrace = Duration.ofMillis(Math.max(0L, autoSubmitGraceMs));
    }

    public void validateRound(int round) {
        if (round <= 0) {
            throw new BadRequestException(INVALID_ROUND_MESSAGE);
        }
    }

    public void validateRequest(FlipbookFrameSubmitRequest request) {
        if (request == null || request.flipbookIndex() == null || request.frameIndex() == null
            || !StringUtils.hasText(request.fileId())) {
            throw new BadRequestException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    public Optional<FlipbookFrameAssignment> findRequestedAssignment(FlipbookRoomState roomState, String viewerUserUuid,
        int round, int flipbookIndex, int frameIndex) {
        return roomState.assignments().stream().filter(assignment -> assignment.round() == round)
            .filter(assignment -> assignment.flipbookIndex() == flipbookIndex)
            .filter(assignment -> assignment.frameIndex() == frameIndex)
            .filter(assignment -> viewerUserUuid.equals(assignment.assignedUserUuid())).findFirst();
    }

    public void validateAssignmentMatches(FlipbookRoomState roomState, FlipbookFrameAssignment currentAssignment,
        int requestedRound, int requestedFlipbookIndex, int requestedFrameIndex) {
        if (roomState.currentRound() == null || roomState.currentRound() != requestedRound
            || currentAssignment.round() != requestedRound
            || currentAssignment.flipbookIndex() != requestedFlipbookIndex
            || currentAssignment.frameIndex() != requestedFrameIndex) {
            throw new ConflictException(ASSIGNMENT_MISMATCH_MESSAGE);
        }
    }

    public void validateNotAutoSubmitted(FlipbookFrameAssignment assignment) {
        if (assignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED || assignment.autoSubmitted()) {
            throw new ConflictException(AUTO_SUBMITTED_MESSAGE);
        }
    }

    public void validateDeadline(LocalDateTime roundDeadlineAt) {
        if (roundDeadlineAt == null) {
            return;
        }

        LocalDateTime expiresAt = roundDeadlineAt.plus(autoSubmitGrace);
        if (!expiresAt.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflictException(SUBMISSION_EXPIRED_MESSAGE);
        }
    }

    public FlipbookFrameAssignment submitAssignment(FlipbookFrameAssignment currentAssignment, FileUpload frameFile,
        LocalDateTime submittedAt) {
        return new FlipbookFrameAssignment(currentAssignment.flipbookIndex(), currentAssignment.frameIndex(),
            currentAssignment.round(), currentAssignment.assignedUserUuid(), FlipbookFrameAssignmentStatus.SUBMITTED,
            frameFile.getId().toString(), frameFile.getObjectKey(), false, false, submittedAt);
    }

    public List<FlipbookFrameAssignment> replaceAssignment(List<FlipbookFrameAssignment> assignments,
        FlipbookFrameAssignment currentAssignment, FlipbookFrameAssignment submittedAssignment) {
        List<FlipbookFrameAssignment> updatedAssignments = new ArrayList<>(assignments.size());
        for (FlipbookFrameAssignment assignment : assignments) {
            if (assignment.flipbookIndex() == currentAssignment.flipbookIndex()
                && assignment.frameIndex() == currentAssignment.frameIndex()
                && assignment.round() == currentAssignment.round()
                && assignment.assignedUserUuid().equals(currentAssignment.assignedUserUuid())) {
                updatedAssignments.add(submittedAssignment);
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return updatedAssignments;
    }
}
