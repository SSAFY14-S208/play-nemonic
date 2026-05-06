package com.nemonicworld.relay.service.room;

import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayRoomPolicy;
import com.nemonicworld.relay.service.support.RelayRoomViewerFactory;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 방 설정 변경 유스케이스입니다.
 */
@Service
public class RelayRoomSettingsUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;

    public RelayRoomSettingsUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomViewerFactory relayRoomViewerFactory) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
    }

    /**
     * 릴레이 방 제한 시간을 변경합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse updateRoomSettings(String userUuidValue, String roomCodeValue,
        RelayRoomSettingsRequest request) {
        int timeLimitSeconds = relayRoomPolicy.resolveTimeLimitSeconds(request);
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
                RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, updatedRoomState, now);

                return RelayRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
