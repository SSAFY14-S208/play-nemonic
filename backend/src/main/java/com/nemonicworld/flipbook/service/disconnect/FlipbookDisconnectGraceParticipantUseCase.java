package com.nemonicworld.flipbook.service.disconnect;

import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FlipbookDisconnectGraceParticipantUseCase {

    public FlipbookParticipantDropUpdate dropExpiredParticipants(FlipbookRoomState roomState, LocalDateTime droppedAt,
        Duration reconnectGrace) {
        List<FlipbookRoomParticipant> participants = new ArrayList<>(roomState.participants().size());
        List<FlipbookDroppedParticipantResult> droppedParticipants = new ArrayList<>();
        boolean changed = false;

        for (FlipbookRoomParticipant participant : roomState.participants()) {
            if (shouldDrop(participant, droppedAt, reconnectGrace)) {
                FlipbookRoomParticipant droppedParticipant = participant.drop(droppedAt);
                participants.add(droppedParticipant);
                droppedParticipants.add(new FlipbookDroppedParticipantResult(roomState.roomCode(),
                    participant.userUuid(), participant.nickname(), participant.disconnectedAt(), droppedAt));
                changed = true;
            } else {
                participants.add(participant);
            }
        }

        HostTransferUpdate hostTransferUpdate = transferHostIfNeeded(roomState, participants, droppedAt);
        changed = changed || hostTransferUpdate.changed();

        return new FlipbookParticipantDropUpdate(hostTransferUpdate.participants(), changed,
            hostTransferUpdate.hostUserUuid(), droppedParticipants, hostTransferUpdate.hostChange());
    }

    private boolean shouldDrop(FlipbookRoomParticipant participant, LocalDateTime now, Duration reconnectGrace) {
        return !participant.dropped() && !participant.connected() && participant.disconnectedAt() != null
            && !participant.disconnectedAt().plus(reconnectGrace).isAfter(now);
    }

    private HostTransferUpdate transferHostIfNeeded(FlipbookRoomState roomState,
        List<FlipbookRoomParticipant> participants, LocalDateTime changedAt) {
        Optional<FlipbookRoomParticipant> currentHost = participants.stream()
            .filter(participant -> participant.userUuid().equals(roomState.hostUserUuid()) || participant.host())
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder));

        if (currentHost.isEmpty() || !currentHost.get().dropped()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        Optional<FlipbookRoomParticipant> newHost = participants.stream()
            .filter(participant -> !participant.dropped() && participant.connected())
            .min(Comparator.comparingInt(FlipbookRoomParticipant::joinOrder));

        if (newHost.isEmpty()) {
            return new HostTransferUpdate(participants, roomState.hostUserUuid(), false, null);
        }

        FlipbookRoomParticipant newHostParticipant = newHost.get();
        List<FlipbookRoomParticipant> transferredParticipants = participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(newHostParticipant.userUuid())))
            .toList();
        FlipbookHostChangeResult hostChange = new FlipbookHostChangeResult(roomState.roomCode(),
            currentHost.get().userUuid(), newHostParticipant.userUuid(), newHostParticipant.nickname(), changedAt);

        return new HostTransferUpdate(transferredParticipants, newHostParticipant.userUuid(), true, hostChange);
    }

    private record HostTransferUpdate(List<FlipbookRoomParticipant> participants, String hostUserUuid, boolean changed,
        FlipbookHostChangeResult hostChange) {
    }
}
