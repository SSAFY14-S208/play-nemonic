package com.nemonicworld.relay.service.room;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.response.RelayRoomKickResponse;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 릴레이 대기실 참여자 강퇴 유스케이스입니다.
 */
@Service
public class RelayRoomKickUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;

    public RelayRoomKickUseCase(AnonymousUserResolver anonymousUserResolver, RelayRoomRepository relayRoomRepository,
        RelayRoomPolicy relayRoomPolicy, RelayInviteMetadataSyncService relayInviteMetadataSyncService) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
    }

    /**
     * 방장이 WAITING 상태의 릴레이 방에서 일반 참여자를 강퇴합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomKickResponse kickParticipant(String userUuidValue, String roomCodeValue,
        String targetUserUuidValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        UUID targetUserUuid = anonymousUserResolver.parseUuid(targetUserUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();
        String targetUserUuidString = targetUserUuid.toString();

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant viewerParticipant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            relayRoomPolicy.validateKickHost(viewerUserUuid, roomState, viewerParticipant);
            relayRoomPolicy.validateWaitingRoomForKick(roomState);
            RelayRoomParticipant targetParticipant = relayRoomPolicy.requireKickTargetParticipant(roomState,
                targetUserUuidString);
            relayRoomPolicy.validateKickTarget(viewerUserUuid, roomState, targetParticipant);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RelayRoomState updatedRoomState = kickParticipant(roomState, targetParticipant, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                relayInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                RelayRoomEventLogger.apiBusiness("relay_participant_kicked",
                    metadata("room_id", updatedRoomState.roomCode(), "host_uuid", viewerUserUuid, "kicked_uuid",
                        targetParticipant.userUuid(), "room_status", updatedRoomState.status(), "participant_count",
                        updatedRoomState.participantCount()));
                return new RelayRoomKickResponse(updatedRoomState.roomCode(), targetParticipant.userUuid(),
                    targetParticipant.nickname(), updatedRoomState.participantCount(), now);
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private RelayRoomState kickParticipant(RelayRoomState roomState, RelayRoomParticipant targetParticipant,
        LocalDateTime kickedAt) {
        List<RelayRoomParticipant> participants = roomState.participants().stream()
            .filter(participant -> !participant.userUuid().equals(targetParticipant.userUuid())).toList();
        List<String> kickedUserUuids = new ArrayList<>(roomState.kickedUserUuids());

        if (!kickedUserUuids.contains(targetParticipant.userUuid())) {
            kickedUserUuids.add(targetParticipant.userUuid());
        }

        return roomState.withParticipantsAndKickedUserUuids(participants, kickedUserUuids, kickedAt);
    }
}
