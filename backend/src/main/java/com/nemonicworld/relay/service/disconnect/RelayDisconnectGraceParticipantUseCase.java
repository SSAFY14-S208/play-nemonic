package com.nemonicworld.relay.service.disconnect;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.redis.RelayRoomParticipant;
import com.nemonicworld.relay.redis.RelayRoomState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RelayDisconnectGraceParticipantUseCase {

    public boolean needsDisconnectGraceProcessing(RelayRoomState roomState, LocalDateTime now,
        Duration reconnectGrace) {
        return hasExpiredDisconnectedParticipant(roomState, now, reconnectGrace)
            || hasDroppedParticipantPendingCurrentAssignment(roomState)
            || hasDroppedHostWithConnectedCandidate(roomState);
    }

    public RelayParticipantDropUpdate dropExpiredParticipants(RelayRoomState roomState, LocalDateTime droppedAt,
        Duration reconnectGrace) {
        List<RelayRoomParticipant> participants = new ArrayList<>(roomState.participants().size());
        List<RelayDroppedParticipantResult> droppedParticipants = new ArrayList<>();
        boolean changed = false;

        for (RelayRoomParticipant participant : roomState.participants()) {
            if (shouldDrop(participant, droppedAt, reconnectGrace)) {
                RelayRoomParticipant droppedParticipant = participant.drop(droppedAt);
                participants.add(droppedParticipant);
                droppedParticipants.add(new RelayDroppedParticipantResult(roomState.roomCode(), participant.userUuid(),
                    participant.nickname(), participant.disconnectedAt(), droppedAt));
                changed = true;
            } else {
                participants.add(participant);
            }
        }

        HostTransferUpdate hostTransferUpdate = transferHostIfNeeded(roomState, participants, droppedAt);
        changed = changed || hostTransferUpdate.changed();

        return new RelayParticipantDropUpdate(hostTransferUpdate.participants(), changed,
            hostTransferUpdate.hostUserUuid(), droppedParticipants, hostTransferUpdate.hostChange());
    }

    public String findDisconnectGraceCandidateUuid(RelayRoomState roomState, LocalDateTime now,
        Duration reconnectGrace) {
        return roomState.participants().stream()
            .filter(participant -> shouldDrop(participant, now, reconnectGrace) || participant.dropped())
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder)).map(RelayRoomParticipant::userUuid)
            .orElse(null);
    }

    private boolean hasExpiredDisconnectedParticipant(RelayRoomState roomState, LocalDateTime now,
        Duration reconnectGrace) {
        return roomState.participants().stream().anyMatch(participant -> shouldDrop(participant, now, reconnectGrace));
    }

    private boolean hasDroppedParticipantPendingCurrentAssignment(RelayRoomState roomState) {
        Set<String> droppedUserUuids = droppedUserUuids(roomState.participants());
        if (droppedUserUuids.isEmpty()) {
            return false;
        }

        return roomState.assignments().stream()
            .anyMatch(assignment -> assignment.part() == roomState.currentPart()
                && assignment.status() == RelayAssignmentStatus.PENDING
                && droppedUserUuids.contains(assignment.assignedUserUuid()));
    }

    private boolean hasDroppedHostWithConnectedCandidate(RelayRoomState roomState) {
        boolean droppedHostExists = roomState.participants().stream().anyMatch(participant -> participant.dropped()
            && (participant.host() || participant.userUuid().equals(roomState.hostUserUuid())));
        if (!droppedHostExists) {
            return false;
        }

        return roomState.participants().stream()
            .anyMatch(participant -> !participant.dropped() && participant.connected());
    }

    private boolean shouldDrop(RelayRoomParticipant participant, LocalDateTime now, Duration reconnectGrace) {
        return !participant.dropped() && !participant.connected() && participant.disconnectedAt() != null
            && !participant.disconnectedAt().plus(reconnectGrace).isAfter(now);
    }

    private HostTransferUpdate transferHostIfNeeded(RelayRoomState roomState, List<RelayRoomParticipant> participants,
        LocalDateTime changedAt) {
        Optional<RelayRoomParticipant> currentHost = participants.stream()
            .filter(participant -> participant.userUuid().equals(roomState.hostUserUuid()) || participant.host())
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder));

        if (currentHost.isEmpty() || !currentHost.get().dropped()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        Optional<RelayRoomParticipant> newHost = participants.stream()
            .filter(participant -> !participant.dropped() && participant.connected())
            .min(Comparator.comparingInt(RelayRoomParticipant::joinOrder));

        if (newHost.isEmpty()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        RelayRoomParticipant newHostParticipant = newHost.get();
        List<RelayRoomParticipant> transferredParticipants = participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(newHostParticipant.userUuid())))
            .toList();
        RelayHostChangeResult hostChange = new RelayHostChangeResult(roomState.roomCode(), currentHost.get().userUuid(),
            newHostParticipant.userUuid(), newHostParticipant.nickname(), changedAt);

        return new HostTransferUpdate(transferredParticipants, newHostParticipant.userUuid(), true, hostChange);
    }

    private Set<String> droppedUserUuids(List<RelayRoomParticipant> participants) {
        Set<String> droppedUserUuids = new HashSet<>();
        for (RelayRoomParticipant participant : participants) {
            if (participant.dropped()) {
                droppedUserUuids.add(participant.userUuid());
            }
        }

        return droppedUserUuids;
    }

    private record HostTransferUpdate(List<RelayRoomParticipant> participants, String hostUserUuid, boolean changed,
        RelayHostChangeResult hostChange) {
    }
}
