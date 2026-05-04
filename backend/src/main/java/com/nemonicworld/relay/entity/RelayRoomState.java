package com.nemonicworld.relay.entity;

import java.time.LocalDateTime;
import java.util.List;

public record RelayRoomState(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
    int minParticipants, int maxParticipants, List<RelayRoomParticipant> participants, LocalDateTime createdAt,
    LocalDateTime updatedAt) {

    public RelayRoomState {
        participants = List.copyOf(participants);
    }

    public int participantCount() {
        return participants.size();
    }
}
