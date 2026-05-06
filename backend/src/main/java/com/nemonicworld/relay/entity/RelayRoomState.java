package com.nemonicworld.relay.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis에 JSON으로 저장되는 진행 중 릴레이 방 상태입니다.
 */
public record RelayRoomState(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
    int minParticipants, int maxParticipants, RelayDrawingPart currentPart, List<RelayRoomParticipant> participants,
    List<RelayRoomAssignment> assignments, LocalDateTime partStartedAt, LocalDateTime partDeadlineAt,
    LocalDateTime gameStartedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public RelayRoomState {
        // 생성 이후 외부에서 참여자 목록을 바꾸지 못하도록 불변 복사본으로 보관합니다.
        participants = participants == null ? List.of() : List.copyOf(participants);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    public RelayRoomState(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, RelayDrawingPart currentPart, List<RelayRoomParticipant> participants,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, currentPart,
            participants, List.of(), null, null, null, createdAt, updatedAt);
    }

    /**
     * Redis 저장 모델에는 중복 저장하지 않고 응답 변환 시 현재 목록 크기로 계산합니다.
     */
    public int participantCount() {
        return participants.size();
    }

    public RelayRoomState withParticipants(List<RelayRoomParticipant> updatedParticipants, LocalDateTime updatedAt) {
        return new RelayRoomState(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants,
            currentPart, updatedParticipants, assignments, partStartedAt, partDeadlineAt, gameStartedAt, createdAt,
            updatedAt);
    }

    public RelayRoomState withTimeLimitSeconds(int updatedTimeLimitSeconds, LocalDateTime updatedAt) {
        return new RelayRoomState(roomCode, status, hostUserUuid, updatedTimeLimitSeconds, minParticipants,
            maxParticipants, currentPart, participants, assignments, partStartedAt, partDeadlineAt, gameStartedAt,
            createdAt, updatedAt);
    }

    public RelayRoomState withAssignments(List<RelayRoomAssignment> updatedAssignments, LocalDateTime updatedAt) {
        return new RelayRoomState(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants,
            currentPart, participants, updatedAssignments, partStartedAt, partDeadlineAt, gameStartedAt, createdAt,
            updatedAt);
    }

    public RelayRoomState startPart(RelayDrawingPart nextPart, LocalDateTime startedAt) {
        return new RelayRoomState(roomCode, RelayRoomStatus.PLAYING, hostUserUuid, timeLimitSeconds, minParticipants,
            maxParticipants, nextPart, participants, assignments, startedAt, startedAt.plusSeconds(timeLimitSeconds),
            gameStartedAt, createdAt, startedAt);
    }

    public RelayRoomState finalizeParts(LocalDateTime completedAt) {
        return new RelayRoomState(roomCode, RelayRoomStatus.FINALIZING, hostUserUuid, timeLimitSeconds, minParticipants,
            maxParticipants, currentPart, participants, assignments, partStartedAt, partDeadlineAt, gameStartedAt,
            createdAt, completedAt);
    }

    public RelayRoomState finish(LocalDateTime finishedAt) {
        return new RelayRoomState(roomCode, RelayRoomStatus.FINISHED, hostUserUuid, timeLimitSeconds, minParticipants,
            maxParticipants, currentPart, participants, assignments, partStartedAt, partDeadlineAt, gameStartedAt,
            createdAt, finishedAt);
    }

    public RelayRoomState startGame(List<RelayRoomAssignment> generatedAssignments, LocalDateTime startedAt) {
        return new RelayRoomState(roomCode, RelayRoomStatus.PLAYING, hostUserUuid, timeLimitSeconds, minParticipants,
            maxParticipants, RelayDrawingPart.FACE, participants, generatedAssignments, startedAt,
            startedAt.plusSeconds(timeLimitSeconds), startedAt, createdAt, startedAt);
    }
}
