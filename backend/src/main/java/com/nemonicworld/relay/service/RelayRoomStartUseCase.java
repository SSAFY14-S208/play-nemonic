package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomStateResponse;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 게임 시작 유스케이스입니다.
 */
@Service
public class RelayRoomStartUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;

    public RelayRoomStartUseCase(AnonymousUserResolver anonymousUserResolver, RelayRoomRepository relayRoomRepository,
        RelayRoomPolicy relayRoomPolicy, RelayRoomViewerFactory relayRoomViewerFactory) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
    }

    /**
     * 릴레이 게임을 시작합니다.
     */
    @Transactional(readOnly = true)
    public RelayRoomStateResponse startRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);
        String viewerUserUuid = viewerUser.getId().toString();

        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            RelayRoomParticipant participant = relayRoomPolicy.requireParticipant(roomState, viewerUserUuid);
            relayRoomPolicy.validateRoomHost(viewerUserUuid, roomState, participant);
            relayRoomPolicy.validateStartableRoomStatus(roomState);

            List<RelayRoomParticipant> startParticipants = relayRoomPolicy.findStartParticipants(roomState);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RelayRoomState updatedRoomState = roomState
                .startGame(RelayRoomAssignmentGenerator.generate(startParticipants), now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, updatedRoomState, now);

                return RelayRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }
}
