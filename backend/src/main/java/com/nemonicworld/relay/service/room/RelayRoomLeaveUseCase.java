package com.nemonicworld.relay.service.room;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.response.RelayRoomLeaveResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 대기실 자발적 퇴장 유스케이스입니다.
 */
@Service
public class RelayRoomLeaveUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    public RelayRoomLeaveUseCase(AnonymousUserResolver anonymousUserResolver, RelayRoomRepository relayRoomRepository,
        RelayRoomPolicy relayRoomPolicy, RelayInviteMetadataSyncService relayInviteMetadataSyncService) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
    }

    /**
     * WAITING 상태의 릴레이 방에서 요청 참여자를 제거하고 필요하면 방장을 승계하거나 방을 닫습니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomLeaveResponse leaveRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant leavingParticipant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            relayRoomPolicy.validateWaitingRoomForLeave(roomState);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            LeaveResult leaveResult = leaveParticipant(roomState, leavingParticipant, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, leaveResult.roomState())) {
                relayInviteMetadataSyncService.syncWithRoomState(leaveResult.roomState());
                return new RelayRoomLeaveResponse(leaveResult.roomState().roomCode(), leavingParticipant.userUuid(),
                    leavingParticipant.nickname(), leaveResult.roomState().participantCount(),
                    leaveResult.hostChanged(), leaveResult.newHostUserUuid(), leaveResult.newHostNickname(),
                    leaveResult.roomState().status() == RelayRoomStatus.CLOSED, leaveResult.roomState().status(), now);
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private LeaveResult leaveParticipant(RelayRoomState roomState, RelayRoomParticipant leavingParticipant,
        LocalDateTime leftAt) {
        List<RelayRoomParticipant> remainingParticipants = roomState.participants().stream()
            .filter(participant -> !participant.userUuid().equals(leavingParticipant.userUuid())).toList();

        if (remainingParticipants.isEmpty()) {
            RelayRoomState closedRoomState = roomState.withParticipantsHostAndStatus(List.of(), null,
                RelayRoomStatus.CLOSED, leftAt);

            return new LeaveResult(closedRoomState, false, null, null);
        }

        if (!isHost(roomState, leavingParticipant)) {
            RelayRoomState updatedRoomState = roomState.withParticipantsHostAndStatus(remainingParticipants,
                roomState.hostUserUuid(), RelayRoomStatus.WAITING, leftAt);

            return new LeaveResult(updatedRoomState, false, null, null);
        }

        RelayRoomParticipant newHost = remainingParticipants.stream()
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder)).orElseThrow();
        List<RelayRoomParticipant> transferredParticipants = remainingParticipants.stream()
            .map(participant -> withHost(participant, participant.userUuid().equals(newHost.userUuid()))).toList();
        RelayRoomState updatedRoomState = roomState.withParticipantsHostAndStatus(transferredParticipants,
            newHost.userUuid(), RelayRoomStatus.WAITING, leftAt);

        return new LeaveResult(updatedRoomState, true, newHost.userUuid(), newHost.nickname());
    }

    private boolean isHost(RelayRoomState roomState, RelayRoomParticipant participant) {
        return participant.host() || participant.userUuid().equals(roomState.hostUserUuid());
    }

    private RelayRoomParticipant withHost(RelayRoomParticipant participant, boolean host) {
        return new RelayRoomParticipant(participant.userUuid(), participant.nickname(), host, participant.joinOrder(),
            participant.connected(), participant.disconnectedAt(), participant.joinedAt());
    }

    private record LeaveResult(RelayRoomState roomState, boolean hostChanged, String newHostUserUuid,
        String newHostNickname) {
    }
}
