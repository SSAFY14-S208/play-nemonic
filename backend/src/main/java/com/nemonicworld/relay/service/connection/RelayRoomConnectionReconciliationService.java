package com.nemonicworld.relay.service.connection;

import com.nemonicworld.global.websocket.session.WebSocketSessionAttributes;
import com.nemonicworld.global.websocket.session.WebSocketSessionRegistry;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.logging.RelayRoomEventLogger;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import com.nemonicworld.relay.repository.RelayRoomRepository;
import com.nemonicworld.relay.service.support.RelayInviteMetadataSyncService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.nemonicworld.relay.logging.RelayRoomEventLogger.metadata;

@Service
public class RelayRoomConnectionReconciliationService {

    private final RelayRoomRepository relayRoomRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final RelayInviteMetadataSyncService relayInviteMetadataSyncService;
    private final int scanLimit;

    public RelayRoomConnectionReconciliationService(RelayRoomRepository relayRoomRepository,
        WebSocketSessionRegistry webSocketSessionRegistry,
        RelayInviteMetadataSyncService relayInviteMetadataSyncService,
        @Value("${nemonic.relay.reconciliation.scan-limit:100}") int scanLimit) {
        this.relayRoomRepository = relayRoomRepository;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.relayInviteMetadataSyncService = relayInviteMetadataSyncService;
        this.scanLimit = scanLimit;
    }

    public RelayRoomConnectionReconciliationResult reconcileConnections() {
        return reconcileConnections(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public RelayRoomConnectionReconciliationResult reconcileConnections(LocalDateTime now) {
        LocalDateTime reconciledAt = now.truncatedTo(ChronoUnit.SECONDS);
        List<RelayRoomState> rooms = relayRoomRepository.findRoomsForConnectionReconciliation(scanLimit);
        int reconciledRoomCount = 0;
        int reconciledParticipantCount = 0;

        for (RelayRoomState roomState : rooms) {
            ReconciledRoomResult result = reconcileRoom(roomState, reconciledAt);
            if (result.reconciled()) {
                reconciledRoomCount++;
                reconciledParticipantCount += result.reconciledUserUuids().size();
            }
        }

        return new RelayRoomConnectionReconciliationResult(rooms.size(), reconciledRoomCount,
            reconciledParticipantCount);
    }

    private ReconciledRoomResult reconcileRoom(RelayRoomState roomState, LocalDateTime reconciledAt) {
        if (roomState.status() != RelayRoomStatus.WAITING && roomState.status() != RelayRoomStatus.PLAYING) {
            return ReconciledRoomResult.noOp();
        }

        List<RelayRoomParticipant> updatedParticipants = roomState.participants().stream()
            .map(participant -> reconcileParticipant(roomState, participant, reconciledAt)).toList();
        List<String> reconciledUserUuids = updatedParticipants.stream()
            .filter(participant -> wasReconciled(roomState, participant)).map(RelayRoomParticipant::userUuid).toList();

        if (reconciledUserUuids.isEmpty()) {
            return ReconciledRoomResult.noOp();
        }

        RelayRoomState updatedRoomState = roomState.withParticipants(updatedParticipants, reconciledAt);
        if (!relayRoomRepository.saveIfUnchanged(roomState, updatedRoomState)) {
            return ReconciledRoomResult.noOp();
        }

        relayInviteMetadataSyncService.syncWithRoomState(updatedRoomState);
        logReconciled(roomState, updatedRoomState, reconciledUserUuids);

        return new ReconciledRoomResult(reconciledUserUuids);
    }

    private RelayRoomParticipant reconcileParticipant(RelayRoomState roomState, RelayRoomParticipant participant,
        LocalDateTime reconciledAt) {
        if (!participant.connected() || hasRelaySession(roomState.roomCode(), participant.userUuid())) {
            return participant;
        }

        return participant.withConnection(false, reconciledAt);
    }

    private boolean hasRelaySession(String roomCode, String userUuid) {
        return webSocketSessionRegistry.hasCurrentSession(WebSocketSessionAttributes.CONNECTION_TYPE_RELAY, roomCode,
            userUuid);
    }

    private boolean wasReconciled(RelayRoomState previousRoomState, RelayRoomParticipant updatedParticipant) {
        return previousRoomState.participants().stream()
            .filter(previous -> previous.userUuid().equals(updatedParticipant.userUuid()))
            .anyMatch(previous -> previous.connected() && !updatedParticipant.connected());
    }

    private void logReconciled(RelayRoomState previousRoomState, RelayRoomState updatedRoomState,
        List<String> reconciledUserUuids) {
        RelayRoomEventLogger.websocketBusiness("relay_room_recovered_or_reconciled",
            metadata("room_id", updatedRoomState.roomCode(), "reason", "missing_ws_session", "before",
                participantSnapshot(previousRoomState), "after", participantSnapshot(updatedRoomState),
                "reconciled_user_uuids", reconciledUserUuids, "room_status", updatedRoomState.status(), "current_part",
                updatedRoomState.currentPart()));
    }

    private List<Map<String, Object>> participantSnapshot(RelayRoomState roomState) {
        return roomState.participants().stream()
            .map(participant -> metadata("uuid", participant.userUuid(), "connected", participant.connected(),
                "disconnected_at", participant.disconnectedAt(), "dropped", participant.dropped(), "dropped_at",
                participant.droppedAt()))
            .toList();
    }

    private record ReconciledRoomResult(List<String> reconciledUserUuids) {

        private static ReconciledRoomResult noOp() {
            return new ReconciledRoomResult(List.of());
        }

        private boolean reconciled() {
            return !reconciledUserUuids.isEmpty();
        }
    }
}
