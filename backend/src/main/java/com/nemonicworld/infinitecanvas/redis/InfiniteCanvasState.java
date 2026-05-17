package com.nemonicworld.infinitecanvas.redis;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record InfiniteCanvasState(String roomCode, InfiniteCanvasStatus status,
    @JsonAlias("ownerUserUuid") String hostUserUuid, List<InfiniteCanvasParticipant> participants,
    List<JsonNode> elements, List<InfiniteCanvasOperation> operations, Map<String, InfiniteCanvasLock> locks,
    Map<String, InfiniteCanvasCursor> cursors, JsonNode viewport, int maxParticipants, long revision,
    LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt) {

    public InfiniteCanvasState {
        participants = participants == null ? List.of() : List.copyOf(participants);
        participants = normalizeHostParticipants(hostUserUuid, participants);
        elements = elements == null ? List.of() : List.copyOf(elements);
        operations = operations == null ? List.of() : List.copyOf(operations);
        locks = locks == null ? Map.of() : Map.copyOf(locks);
        cursors = cursors == null ? Map.of() : Map.copyOf(cursors);
    }

    public static InfiniteCanvasState create(String roomCode, String hostUserUuid,
        InfiniteCanvasParticipant hostParticipant, JsonNode viewport, int maxParticipants, LocalDateTime now) {
        return new InfiniteCanvasState(roomCode, InfiniteCanvasStatus.ACTIVE, hostUserUuid, List.of(hostParticipant),
            List.of(), List.of(), Map.of(), Map.of(), viewport, maxParticipants, 0L, now, now, null);
    }

    @JsonIgnore
    public int participantCount() {
        return participants.size();
    }

    @JsonIgnore
    public int connectedParticipantCount() {
        return (int) participants.stream().filter(InfiniteCanvasParticipant::connected).count();
    }

    @JsonIgnore
    public boolean isActive() {
        return status == InfiniteCanvasStatus.ACTIVE;
    }

    public Optional<InfiniteCanvasParticipant> findParticipant(String userUuid) {
        return participants.stream().filter(participant -> participant.userUuid().equals(userUuid)).findFirst();
    }

    public boolean hasParticipant(String userUuid) {
        return findParticipant(userUuid).isPresent();
    }

    private static List<InfiniteCanvasParticipant> normalizeHostParticipants(String hostUserUuid,
        List<InfiniteCanvasParticipant> participants) {
        return participants.stream()
            .map(participant -> participant.withHost(participant.userUuid().equals(hostUserUuid))).toList();
    }
}
