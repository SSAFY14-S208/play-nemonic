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

@Service
public class RelayRoomConnectionUseCase {

    private final AnonymousUserResolver anonymousUserResolver;
    private final RelayRoomRepository relayRoomRepository;
    private final RelayRoomPolicy relayRoomPolicy;
    private final RelayRoomViewerFactory relayRoomViewerFactory;

    public RelayRoomConnectionUseCase(AnonymousUserResolver anonymousUserResolver,
        RelayRoomRepository relayRoomRepository, RelayRoomPolicy relayRoomPolicy,
        RelayRoomViewerFactory relayRoomViewerFactory) {
        this.anonymousUserResolver = anonymousUserResolver;
        this.relayRoomRepository = relayRoomRepository;
        this.relayRoomPolicy = relayRoomPolicy;
        this.relayRoomViewerFactory = relayRoomViewerFactory;
    }

    @Transactional(readOnly = true)
    public RelayRoomStateResponse connectRoom(String userUuidValue, String roomCodeValue) {
        AppUser viewerUser = anonymousUserResolver.resolve(userUuidValue);
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUser.getId().toString(), roomCodeValue, true);
    }

    @Transactional(readOnly = true)
    public RelayRoomStateResponse disconnectRoom(String userUuidValue, String roomCodeValue) {
        String viewerUserUuid = anonymousUserResolver.parseUuid(userUuidValue).toString();
        relayRoomPolicy.validateRoomCode(roomCodeValue);

        return updateParticipantConnectionState(viewerUserUuid, roomCodeValue, false);
    }

    private RelayRoomStateResponse updateParticipantConnectionState(String viewerUserUuid, String roomCodeValue,
        boolean connected) {
        for (int attempt = 0; attempt < RelayRoomPolicy.ROOM_UPDATE_MAX_RETRIES; attempt++) {
            RelayRoomState roomState = relayRoomPolicy.findRoomState(roomCodeValue);
            relayRoomPolicy.validateWebSocketConnectableRoom(roomState);
            RelayRoomParticipant participant = relayRoomPolicy.requireConnectionParticipant(roomState, viewerUserUuid);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RelayRoomParticipant updatedParticipant = new RelayRoomParticipant(participant.userUuid(),
                participant.nickname(), participant.host(), participant.joinOrder(), connected, connected ? null : now,
                participant.joinedAt());
            RelayRoomState updatedRoomState = replaceParticipant(roomState, updatedParticipant, now);

            if (relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
                RelayRoomViewerResponse viewer = relayRoomViewerFactory.create(viewerUserUuid, updatedRoomState, now);

                return RelayRoomStateResponse.from(updatedRoomState, viewer);
            }
        }

        throw new IllegalStateException(RelayRoomPolicy.ROOM_UPDATE_CONFLICT_MESSAGE);
    }

    private RelayRoomState replaceParticipant(RelayRoomState roomState, RelayRoomParticipant updatedParticipant,
        LocalDateTime updatedAt) {
        List<RelayRoomParticipant> participants = roomState.participants().stream()
            .map(participant -> participant.userUuid().equals(updatedParticipant.userUuid())
                ? updatedParticipant
                : participant)
            .toList();

        return roomState.withParticipants(participants, updatedAt);
    }
}
