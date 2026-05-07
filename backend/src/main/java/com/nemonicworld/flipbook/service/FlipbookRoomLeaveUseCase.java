package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.flipbook.dto.response.FlipbookRoomLeaveResponse;
import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import com.nemonicworld.flipbook.repository.FlipbookRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플립북 대기실 자발적 퇴장 유스케이스입니다.
 */
@Service
@RequiredArgsConstructor
public class FlipbookRoomLeaveUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final FlipbookRoomRepository flipbookRoomRepository;
    private final FlipbookRoomPolicy flipbookRoomPolicy;
    private final FlipbookInviteMetadataSyncService flipbookInviteMetadataSyncService;

    /**
     * WAITING 상태의 플립북 방에서 요청 참여자를 제거하고 필요하면 방장을 승계하거나 방을 닫습니다.
     */
    @Transactional(readOnly = true)
    public FlipbookRoomLeaveResponse leaveRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        flipbookRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        for (int attempt = 0; attempt < FlipbookRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            FlipbookRoomState roomState = flipbookRoomPolicy.findRoomState(roomCodeValue);
            FlipbookRoomParticipant leavingParticipant = flipbookRoomPolicy.requireParticipant(roomState,
                viewerUserUuid);
            flipbookRoomPolicy.validateWaitingRoomForLeave(roomState);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            LeaveResult leaveResult = leaveParticipant(roomState, leavingParticipant, now);

            if (flipbookRoomRepository.saveIfUnchanged(roomState, leaveResult.roomState())) {
                flipbookInviteMetadataSyncService.syncWithRoomState(leaveResult.roomState());
                return new FlipbookRoomLeaveResponse(leaveResult.roomState().roomCode(), leavingParticipant.userUuid(),
                    leavingParticipant.nickname(), leaveResult.roomState().participantCount(),
                    leaveResult.hostChanged(), leaveResult.newHostUserUuid(), leaveResult.newHostNickname(),
                    leaveResult.roomState().status() == FlipbookRoomStatus.CLOSED, leaveResult.roomState().status(),
                    now);
            }
        }

        throw new ConflictException(FlipbookRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private LeaveResult leaveParticipant(FlipbookRoomState roomState, FlipbookRoomParticipant leavingParticipant,
        LocalDateTime leftAt) {
        List<FlipbookRoomParticipant> remainingParticipants = roomState.participants().stream()
            .filter(participant -> !participant.userUuid().equals(leavingParticipant.userUuid())).toList();

        if (remainingParticipants.isEmpty()) {
            FlipbookRoomState closedRoomState = roomState.withParticipantsHostAndStatus(List.of(), null,
                FlipbookRoomStatus.CLOSED, leftAt);

            return new LeaveResult(closedRoomState, false, null, null);
        }

        if (!isHost(roomState, leavingParticipant)) {
            FlipbookRoomState updatedRoomState = roomState.withParticipantsHostAndStatus(remainingParticipants,
                roomState.hostUserUuid(), FlipbookRoomStatus.WAITING, leftAt);

            return new LeaveResult(updatedRoomState, false, null, null);
        }

        FlipbookRoomParticipant newHost = remainingParticipants.stream()
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder)).orElseThrow();
        List<FlipbookRoomParticipant> transferredParticipants = remainingParticipants.stream()
            .map(participant -> withHost(participant, participant.userUuid().equals(newHost.userUuid()))).toList();
        FlipbookRoomState updatedRoomState = roomState.withParticipantsHostAndStatus(transferredParticipants,
            newHost.userUuid(), FlipbookRoomStatus.WAITING, leftAt);

        return new LeaveResult(updatedRoomState, true, newHost.userUuid(), newHost.nickname());
    }

    private boolean isHost(FlipbookRoomState roomState, FlipbookRoomParticipant participant) {
        return participant.host() || participant.userUuid().equals(roomState.hostUserUuid());
    }

    private FlipbookRoomParticipant withHost(FlipbookRoomParticipant participant, boolean host) {
        return new FlipbookRoomParticipant(participant.userUuid(), participant.nickname(), host,
            participant.joinOrder(), participant.connected(), participant.disconnectedAt(), participant.joinedAt());
    }

    private record LeaveResult(FlipbookRoomState roomState, boolean hostChanged, String newHostUserUuid,
        String newHostNickname) {
    }
}
