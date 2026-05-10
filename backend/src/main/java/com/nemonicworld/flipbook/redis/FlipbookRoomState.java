package com.nemonicworld.flipbook.redis;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis에 JSON 문자열로 저장되는 플립북 방 상태입니다.
 */
public record FlipbookRoomState(String roomCode, FlipbookRoomStatus status, String hostUserUuid, int timeLimitSeconds,
    int minParticipants, int maxParticipants, Integer currentRound, Integer totalRounds, LocalDateTime roundStartedAt,
    LocalDateTime roundDeadlineAt, LocalDateTime gameStartedAt, List<FlipbookFrameAssignment> assignments,
    List<FlipbookRoomParticipant> participants, LocalDateTime createdAt, LocalDateTime updatedAt,
    List<String> kickedUserUuids) {

    public FlipbookRoomState {
        // Redis에서 복원한 뒤에도 외부 코드가 참여자 목록을 직접 바꾸지 못하도록 불변 복사합니다.
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        participants = participants == null ? List.of() : List.copyOf(participants);
        kickedUserUuids = kickedUserUuids == null ? List.of() : List.copyOf(kickedUserUuids);
    }

    public FlipbookRoomState(String roomCode, FlipbookRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, Integer currentRound, Integer totalRounds,
        LocalDateTime roundStartedAt, LocalDateTime roundDeadlineAt, LocalDateTime gameStartedAt,
        List<FlipbookRoomParticipant> participants, LocalDateTime createdAt, LocalDateTime updatedAt,
        List<String> kickedUserUuids) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, currentRound,
            totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt, List.of(), participants, createdAt, updatedAt,
            kickedUserUuids);
    }

    public FlipbookRoomState(String roomCode, FlipbookRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, List<FlipbookRoomParticipant> participants, LocalDateTime createdAt,
        LocalDateTime updatedAt, List<String> kickedUserUuids) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, null, null, null, null,
            null, List.of(), participants, createdAt, updatedAt, kickedUserUuids);
    }

    public FlipbookRoomState(String roomCode, FlipbookRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, List<FlipbookRoomParticipant> participants, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
        this(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants, participants,
            createdAt, updatedAt, List.of());
    }

    /**
     * 참여자 수는 Redis에 중복 저장하지 않고 현재 참여자 목록 크기로 계산합니다.
     */
    public int participantCount() {
        return participants.size();
    }

    public FlipbookRoomState withParticipants(List<FlipbookRoomParticipant> updatedParticipants,
        LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants,
            currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt, assignments, updatedParticipants,
            createdAt, updatedAt, kickedUserUuids);
    }

    public FlipbookRoomState withParticipantsAndKickedUserUuids(List<FlipbookRoomParticipant> updatedParticipants,
        List<String> updatedKickedUserUuids, LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants,
            currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt, assignments, updatedParticipants,
            createdAt, updatedAt, updatedKickedUserUuids);
    }

    public FlipbookRoomState withParticipantsHostAndStatus(List<FlipbookRoomParticipant> updatedParticipants,
        String updatedHostUserUuid, FlipbookRoomStatus updatedStatus, LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, updatedStatus, updatedHostUserUuid, timeLimitSeconds, minParticipants,
            maxParticipants, currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt, assignments,
            updatedParticipants, createdAt, updatedAt, kickedUserUuids);
    }

    // 나머지 값은 그대로, updatedAt만 현재 시각으로 변경 (record라 기존의 객체 값을 변경할 수 없음)
    public FlipbookRoomState withTimeLimitSeconds(int updatedTimeLimitSeconds, LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, status, hostUserUuid, updatedTimeLimitSeconds, minParticipants,
            maxParticipants, currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt, assignments,
            participants, createdAt, updatedAt, kickedUserUuids);
    }

    public FlipbookRoomState withAssignments(List<FlipbookFrameAssignment> updatedAssignments,
        LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants,
            currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt, updatedAssignments, participants,
            createdAt, updatedAt, kickedUserUuids);
    }

    /**
     * 현재 라운드를 다음 라운드로 넘기고 새 마감 시각을 계산합니다.
     */
    public FlipbookRoomState startNextRound(int nextRound, List<FlipbookFrameAssignment> updatedAssignments,
        LocalDateTime startedAt) {
        return new FlipbookRoomState(roomCode, FlipbookRoomStatus.PLAYING, hostUserUuid, timeLimitSeconds,
            minParticipants, maxParticipants, nextRound, totalRounds, startedAt,
            startedAt.plusSeconds(timeLimitSeconds), gameStartedAt, updatedAssignments, participants, createdAt,
            startedAt, kickedUserUuids);
    }

    /**
     * 모든 라운드 제출이 끝난 방을 결과 조회 가능한 종료 상태로 전환합니다.
     */
    public FlipbookRoomState finishGame(List<FlipbookFrameAssignment> updatedAssignments, LocalDateTime finishedAt) {
        return new FlipbookRoomState(roomCode, FlipbookRoomStatus.FINISHED, hostUserUuid, timeLimitSeconds,
            minParticipants, maxParticipants, currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt,
            updatedAssignments, participants, createdAt, finishedAt, kickedUserUuids);
    }

    /**
     * 결과물/갤러리/임시 파일은 그대로 두고 방 상태만 닫힘 상태로 전환합니다.
     */
    public FlipbookRoomState close(LocalDateTime closedAt) {
        return new FlipbookRoomState(roomCode, FlipbookRoomStatus.CLOSED, hostUserUuid, timeLimitSeconds,
            minParticipants, maxParticipants, currentRound, totalRounds, roundStartedAt, roundDeadlineAt, gameStartedAt,
            assignments, participants, createdAt, closedAt, kickedUserUuids);
    }

    /**
     * 첫 라운드를 시작하고 현재 라운드 마감 시각을 계산합니다.
     */
    public FlipbookRoomState startGame(int resolvedTotalRounds, LocalDateTime startedAt) {
        return startGame(resolvedTotalRounds, List.of(), startedAt);
    }

    /**
     * 첫 라운드와 전체 프레임 배정표를 함께 시작 상태로 저장합니다.
     */
    public FlipbookRoomState startGame(int resolvedTotalRounds, List<FlipbookFrameAssignment> generatedAssignments,
        LocalDateTime startedAt) {
        return new FlipbookRoomState(roomCode, FlipbookRoomStatus.PLAYING, hostUserUuid, timeLimitSeconds,
            minParticipants, maxParticipants, 1, resolvedTotalRounds, startedAt,
            startedAt.plusSeconds(timeLimitSeconds), startedAt, generatedAssignments, participants, createdAt,
            startedAt, kickedUserUuids);
    }

}
