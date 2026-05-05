package com.nemonicworld.relay.service;

import com.nemonicworld.relay.dto.response.RelayRoomViewerBlockedReason;
import com.nemonicworld.relay.dto.response.RelayRoomViewerResponse;
import com.nemonicworld.relay.entity.RelayRoomParticipant;
import com.nemonicworld.relay.entity.RelayRoomState;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RelayRoomViewerFactory {

    private final RelayRoomPolicy relayRoomPolicy;

    public RelayRoomViewerFactory(RelayRoomPolicy relayRoomPolicy) {
        this.relayRoomPolicy = relayRoomPolicy;
    }

    RelayRoomViewerResponse create(String viewerUserUuid, RelayRoomState roomState, LocalDateTime now) {
        Optional<RelayRoomParticipant> participant = relayRoomPolicy.findParticipant(roomState, viewerUserUuid);

        if (participant.isPresent()) {
            return createParticipantViewerResponse(viewerUserUuid, roomState, participant.get(), now);
        }

        return createNonParticipantViewerResponse(viewerUserUuid, roomState);
    }

    private RelayRoomViewerResponse createParticipantViewerResponse(String viewerUserUuid, RelayRoomState roomState,
        RelayRoomParticipant participant, LocalDateTime now) {
        boolean host = participant.host() || roomState.hostUserUuid().equals(viewerUserUuid);

        if (participant.connected()) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false, null);
        }

        if (relayRoomPolicy.canReconnect(participant, now)) {
            return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, true, null);
        }

        return new RelayRoomViewerResponse(viewerUserUuid, true, host, false, false,
            RelayRoomViewerBlockedReason.RECONNECT_EXPIRED);
    }

    private RelayRoomViewerResponse createNonParticipantViewerResponse(String viewerUserUuid,
        RelayRoomState roomState) {
        RelayRoomViewerBlockedReason blockedReason = relayRoomPolicy.findJoinBlockedReason(roomState);
        boolean canJoin = blockedReason == null;

        return new RelayRoomViewerResponse(viewerUserUuid, false, false, canJoin, false, blockedReason);
    }
}
