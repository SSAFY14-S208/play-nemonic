package com.nemonicworld.flipbook.service.assignment;

import com.nemonicworld.flipbook.dto.response.FlipbookFrameHintResponse;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomMyAssignmentResponse;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.service.submission.FlipbookFrameImageUrlResolver;
import com.nemonicworld.flipbook.service.support.FlipbookRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 플립북 내 현재 프레임 배정 조회 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomAssignmentQueryUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookFrameImageUrlResolver flipbookFrameImageUrlResolver;

    /**
     * 현재 사용자가 이번 라운드에 그릴 플립북 프레임과 이전 프레임 힌트를 조회합니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomMyAssignmentResponse getMyAssignment(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
        FlipbookRoomParticipant participant = flipbookRoomPolicy.requireParticipant(roomState, viewerUserUuid);
        flipbookRoomPolicy.validateNotDropped(roomState, participant.userUuid());
        flipbookRoomPolicy.validateAssignmentQueryableRoom(roomState);

        FlipbookFrameAssignment currentAssignment = flipbookRoomPolicy.requireCurrentAssignment(roomState,
            viewerUserUuid);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long remainingSeconds = calculateRemainingSeconds(roomState.roundDeadlineAt(), now);
        FlipbookFrameHintResponse hint = resolveHint(roomState, currentAssignment);

        return FlipbookRoomMyAssignmentResponse.from(roomState, currentAssignment, remainingSeconds, hint);
    }

    private long calculateRemainingSeconds(LocalDateTime roundDeadlineAt, LocalDateTime now) {
        if (roundDeadlineAt == null) {
            return 0;
        }

        return Math.max(0, Duration.between(now, roundDeadlineAt).getSeconds());
    }

    private FlipbookFrameHintResponse resolveHint(FlipbookRoomState roomState,
        FlipbookFrameAssignment currentAssignment) {
        int previousFrameIndex = currentAssignment.frameIndex() - 1;
        if (previousFrameIndex < 0) {
            return null;
        }

        return flipbookRoomPolicy.findFrameAssignment(roomState, currentAssignment.flipbookIndex(), previousFrameIndex)
            .filter(this::hasVisiblePreviousAssignment).map(this::toHintResponse).orElse(null);
    }

    private FlipbookFrameHintResponse toHintResponse(FlipbookFrameAssignment assignment) {
        String frameUrl = assignment.empty() ? null : flipbookFrameImageUrlResolver.resolve(assignment.objectKey());

        return FlipbookFrameHintResponse.from(assignment, frameUrl);
    }

    private boolean hasVisiblePreviousAssignment(FlipbookFrameAssignment assignment) {
        return assignment.empty() || assignment.autoSubmitted()
            || assignment.status() == FlipbookFrameAssignmentStatus.AUTO_SUBMITTED
            || assignment.status() == FlipbookFrameAssignmentStatus.SUBMITTED
            || StringUtils.hasText(assignment.objectKey());
    }
}
