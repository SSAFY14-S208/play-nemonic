package com.nemonicworld.relay.service;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.entity.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.websocket.RelayRoomEventPublisher;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 마감 시간이 지난 릴레이 현재 파트의 미제출 배정을 빈 그림으로 자동 제출합니다.
 */
@Service
public class RelayRoomTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomTimeoutService.class);

    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPartAdvanceService relayRoomPartAdvanceService;
    private final RelayRoomEventPublisher relayRoomEventPublisher;
    private final int scanLimit;

    public RelayRoomTimeoutService(RelayRoomRepository relayRoomRepository,
        RelayRoomPartAdvanceService relayRoomPartAdvanceService, RelayRoomEventPublisher relayRoomEventPublisher,
        @Value("${nemonic.relay.timeout.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPartAdvanceService = relayRoomPartAdvanceService;
        this.relayRoomEventPublisher = relayRoomEventPublisher;
        this.scanLimit = scanLimit;
    }

    /**
     * Redis에서 마감 시간이 지난 PLAYING 방을 찾아 자동 제출을 처리합니다.
     */
    public RelayTimeoutProcessResult processExpiredRooms() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomState> expiredRooms = relayRoomRepository.findExpiredPlayingRooms(now, scanLimit);
        int processedRoomCount = 0;
        int autoSubmittedCount = 0;

        for (RelayRoomState expiredRoom : expiredRooms) {
            try {
                RelayRoomTimeoutResult result = processExpiredRoom(expiredRoom.roomCode(), now);
                if (result.processed()) {
                    processedRoomCount++;
                    autoSubmittedCount += result.autoSubmissions().size();
                }
            } catch (RuntimeException e) {
                log.warn("릴레이 타임아웃 자동 제출 처리 중 오류가 발생했습니다. roomCode={}", expiredRoom.roomCode(), e);
            }
        }

        return new RelayTimeoutProcessResult(expiredRooms.size(), processedRoomCount, autoSubmittedCount);
    }

    /**
     * 지정한 방의 현재 파트가 만료되었으면 PENDING 배정을 AUTO_SUBMITTED로 바꿉니다.
     */
    public RelayRoomTimeoutResult processExpiredRoom(String roomCode, LocalDateTime now) {
        LocalDateTime processedAt = now.truncatedTo(ChronoUnit.SECONDS);

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomRepository.findByRoomCode(roomCode).orElse(null);
            if (!isExpiredPlayingRoom(roomState, processedAt)) {
                return RelayRoomTimeoutResult.noOp(roomCode);
            }

            RelayDrawingPart currentPart = roomState.currentPart();
            AutoSubmitUpdate autoSubmitUpdate = autoSubmitPendingAssignments(roomState, currentPart, processedAt);
            RelayRoomState submittedRoomState = autoSubmitUpdate.autoSubmissions().isEmpty()
                ? roomState
                : roomState.withAssignments(autoSubmitUpdate.updatedAssignments(), processedAt);
            RelayPartAdvanceResult advanceResult = relayRoomPartAdvanceService
                .advancePartIfCompleted(submittedRoomState, currentPart, processedAt);

            if (autoSubmitUpdate.autoSubmissions().isEmpty() && !advanceResult.advanced()) {
                return RelayRoomTimeoutResult.noOp(roomCode);
            }

            if (relayRoomRepository.saveIfUnchanged(roomState, advanceResult.roomState())) {
                RelayRoomTimeoutResult result = new RelayRoomTimeoutResult(roomCode, true, currentPart,
                    autoSubmitUpdate.autoSubmissions(), advanceResult);
                publishTimeoutEvents(result);

                return result;
            }
        }

        throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private boolean isExpiredPlayingRoom(RelayRoomState roomState, LocalDateTime now) {
        return roomState != null && roomState.status() == RelayRoomStatus.PLAYING && roomState.currentPart() != null
            && roomState.partDeadlineAt() != null && !roomState.partDeadlineAt().isAfter(now);
    }

    private AutoSubmitUpdate autoSubmitPendingAssignments(RelayRoomState roomState, RelayDrawingPart currentPart,
        LocalDateTime submittedAt) {
        List<RelayRoomAssignment> updatedAssignments = new ArrayList<>(roomState.assignments().size());
        List<RelayRoomAutoSubmissionResult> autoSubmissions = new ArrayList<>();

        for (RelayRoomAssignment assignment : roomState.assignments()) {
            if (assignment.part() == currentPart && assignment.status() == RelayAssignmentStatus.PENDING) {
                RelayRoomAssignment autoSubmittedAssignment = autoSubmitAssignment(assignment, submittedAt);
                updatedAssignments.add(autoSubmittedAssignment);
                autoSubmissions.add(new RelayRoomAutoSubmissionResult(roomState.roomCode(),
                    findNickname(roomState, assignment.assignedUserUuid()), autoSubmittedAssignment));
            } else {
                updatedAssignments.add(assignment);
            }
        }

        return new AutoSubmitUpdate(updatedAssignments, autoSubmissions);
    }

    private RelayRoomAssignment autoSubmitAssignment(RelayRoomAssignment assignment, LocalDateTime submittedAt) {
        return new RelayRoomAssignment(assignment.canvasIndex(), assignment.part(), assignment.assignedUserUuid(),
            RelayAssignmentStatus.AUTO_SUBMITTED, assignment.fileId(), null, null, true, true, submittedAt);
    }

    private String findNickname(RelayRoomState roomState, String userUuid) {
        return roomState.participants().stream().filter(participant -> participant.userUuid().equals(userUuid))
            .map(RelayRoomParticipant::nickname).findFirst().orElse(null);
    }

    private void publishTimeoutEvents(RelayRoomTimeoutResult result) {
        for (RelayRoomAutoSubmissionResult autoSubmission : result.autoSubmissions()) {
            relayRoomEventPublisher.publishPartAutoSubmitted(autoSubmission.roomCode(), autoSubmission.nickname(),
                autoSubmission.assignment());
        }

        RelayPartAdvanceResult advanceResult = result.advanceResult();
        if (advanceResult == null || !advanceResult.advanced()) {
            return;
        }

        if (advanceResult.allPartsCompleted()) {
            relayRoomEventPublisher.publishAllPartsCompleted(result.roomCode(), advanceResult.roomState().status(),
                advanceResult.roomState().updatedAt());
        } else {
            relayRoomEventPublisher.publishPartStarted(result.roomCode(), result.previousPart(),
                advanceResult.nextPart(), advanceResult.nextPartStartedAt(), advanceResult.nextPartDeadlineAt());
        }
    }

    private record AutoSubmitUpdate(List<RelayRoomAssignment> updatedAssignments,
        List<RelayRoomAutoSubmissionResult> autoSubmissions) {
    }
}
