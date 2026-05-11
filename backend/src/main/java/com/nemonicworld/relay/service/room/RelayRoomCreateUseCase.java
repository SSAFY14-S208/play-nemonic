package com.nemonicworld.relay.service.room;

import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.invite.repository.InviteRepository;
import com.nemonicworld.relay.dto.response.RelayRoomCreateResponse;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomParticipantLimit;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomTimeLimitSettings;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 릴레이 방 생성 유스케이스입니다.
 */
@Service
public class RelayRoomCreateUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RoomCodeGenerator roomCodeGenerator;
    private final RelayRoomRepository relayRoomRepository;
    private final InviteRepository inviteRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayRuntimeSettingsProvider relayRuntimeSettingsProvider;

    public RelayRoomCreateUseCase(AnonymousUserResolver anonymousUserResolver, RoomCodeGenerator roomCodeGenerator,
        RelayRoomRepository relayRoomRepository, InviteRepository inviteRepository, RelayRoomPolicy relayRoomPolicy,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayRuntimeSettingsProvider relayRuntimeSettingsProvider) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.roomCodeGenerator = roomCodeGenerator;
        this.relayRoomRepository = relayRoomRepository;
        this.inviteRepository = inviteRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayRuntimeSettingsProvider = relayRuntimeSettingsProvider;
    }

    /**
     * 새 릴레이 방을 생성합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomCreateResponse createRoom(String userUuidValue) {
        AppUser hostUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateNicknameRegistered(hostUser);

        String roomCode = roomCodeGenerator.generateUnique(inviteRepository::existsByInviteCode);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RelayRoomParticipant hostParticipant = new RelayRoomParticipant(hostUser.getId().toString(),
            hostUser.getNickname(), true, RelayRoomPolicy.HOST_JOIN_ORDER, false, null, now);
        RelayRoomParticipantLimit participantLimit = relayRuntimeSettingsProvider.currentParticipantLimit();
        RelayRoomTimeLimitSettings timeLimitSettings = relayRuntimeSettingsProvider.currentRoomTimeLimitSettings();
        RelayRoomState roomState = new RelayRoomState(roomCode, RelayRoomStatus.WAITING, hostUser.getId().toString(),
            timeLimitSettings.defaultSeconds(), participantLimit.minParticipants(), participantLimit.maxParticipants(),
            null, List.of(hostParticipant), List.of(), null, null, null, now, now);

        relayRoomRepository.save(roomState);
        relayInviteMetadataSyncService.syncWithRoomState(roomState);
        RelayRoomEventLogger.apiBusiness("relay_room_created",
            metadata("room_id", roomState.roomCode(), "host_uuid", roomState.hostUserUuid(), "time_limit_seconds",
                roomState.timeLimitSeconds(), "min_participants", roomState.minParticipants(), "max_participants",
                roomState.maxParticipants()));

        return RelayRoomCreateResponse.from(roomState);
    }
}
