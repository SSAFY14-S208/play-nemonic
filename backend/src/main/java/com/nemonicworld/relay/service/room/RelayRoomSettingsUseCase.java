package com.nemonicworld.relay.service.room;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomViewerFactory;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsSnapshot;
import com.nemonicworld.relay.service.support.RelayRuntimeSettingsProvider;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

/**
 * 릴레이 방 설정 변경 유스케이스입니다.
 */
@Service
public class RelayRoomSettingsUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final RelayRuntimeSettingsProvider relayRuntimeSettingsProvider;

    public RelayRoomSettingsUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomViewerFactory relayRoomViewerFactory, RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        RelayRuntimeSettingsProvider relayRuntimeSettingsProvider) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.relayRuntimeSettingsProvider = relayRuntimeSettingsProvider;
    }

    /**
     * 릴레이 방 제한 시간을 변경합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        RelayRoomSettingsRequest request) {
        RelayRuntimeSettingsSnapshot settings = relayRuntimeSettingsProvider.currentSettingsSnapshot();
        int timeLimitSeconds = relayRoomPolicy.resolveTimeLimitSeconds(request, settings.roomTimeLimitSettings());
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            relayRoomPolicy.validateWaitingRoomForSettings(roomState);
            RelayRoomParticipant participant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            relayRoomPolicy.validateRoomHost(viewerUserUuid, roomState, participant);

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RelayRoomState updatedRoomState = roomState.withTimeLimitSeconds(timeLimitSeconds, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                relayInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
                RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, updatedRoomState, now,
                    settings.reconnectGracePeriod());
                RelayRoomEventLogger.apiBusiness("relay_room_settings_changed",
                    metadata("room_id", updatedRoomState.roomCode(), "actor_uuid", viewerUserUuid, "before",
                        metadata("time_limit_seconds", roomState.timeLimitSeconds(), "max_participants",
                            roomState.maxParticipants()),
                        "after", metadata("time_limit_seconds", updatedRoomState.timeLimitSeconds(), "max_participants",
                            updatedRoomState.maxParticipants())));

                return RelayRoomStateResponse.from(updatedRoomState, viewer, settings.roomTimeLimitSettings(),
                    settings.reconnectGracePeriod());
            }
        }

        throw new ConflictException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
