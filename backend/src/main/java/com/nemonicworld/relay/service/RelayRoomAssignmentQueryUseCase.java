package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomAssignmentHintResponse;
import com.nemonicworld.relay.dto.response.RelayRoomMyAssignmentResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 릴레이 내 현재 배정 조회 유스케이스입니다.
 */
@Service
public class RelayRoomAssignmentQueryUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomPolicy relayRoomPolicy;

    public RelayRoomAssignmentQueryUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomPolicy relayRoomPolicy) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomPolicy = relayRoomPolicy;
    }

    /**
     * 현재 사용자가 그릴 배정과 힌트를 조회합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomMyAssignmentResponse getMyAssignment(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
        relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);
        relayRoomPolicy.validateAssignmentQueryableRoom(roomState);

        RelayRoomAssignment currentAssignment = relayRoomPolicy.requireCurrentAssignment(roomState, viewerUserUuid);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long remainingSeconds = calculateRemainingSeconds(roomState.partDeadlineAt(), now);
        RelayRoomAssignmentHintResponse hint = resolveHint(roomState, currentAssignment);

        return RelayRoomMyAssignmentResponse.from(roomState, currentAssignment, remainingSeconds, hint);
    }

    private long calculateRemainingSeconds(LocalDateTime partDeadlineAt, LocalDateTime now) {
        if (partDeadlineAt == null) {
            return 0;
        }

        return Math.max(0, Duration.between(now, partDeadlineAt).getSeconds());
    }

    private RelayRoomAssignmentHintResponse resolveHint(RelayRoomState roomState,
        RelayRoomAssignment currentAssignment) {
        RelayDrawingPart previousPart = previousPart(currentAssignment.part());
        if (previousPart == null) {
            return null;
        }

        return relayRoomPolicy.findAssignment(roomState, currentAssignment.canvasIndex(), previousPart)
            .filter(this::hasHint).map(RelayRoomAssignmentHintResponse::from).orElse(null);
    }

    private RelayDrawingPart previousPart(RelayDrawingPart currentPart) {
        if (currentPart == RelayDrawingPart.BODY) {
            return RelayDrawingPart.FACE;
        }

        if (currentPart == RelayDrawingPart.LEGS) {
            return RelayDrawingPart.BODY;
        }

        return null;
    }

    private boolean hasHint(RelayRoomAssignment assignment) {
        return assignment.empty() || StringUtils.hasText(assignment.hintObjectKey());
    }
}
