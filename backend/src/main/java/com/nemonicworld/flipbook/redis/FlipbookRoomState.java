package com.nemonicworld.flipbook.redis;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis에 JSON 문자열로 저장되는 플립북 방 상태입니다.
 */
public record FlipbookRoomState(String roomCode, FlipbookRoomStatus status, String hostUserUuid, int timeLimitSeconds,
    int minParticipants, int maxParticipants, List<FlipbookRoomParticipant> participants, LocalDateTime createdAt,
    LocalDateTime updatedAt, List<String> kickedUserUuids) {

    public FlipbookRoomState {
        // Redis에서 복원한 뒤에도 외부 코드가 참여자 목록을 직접 바꾸지 못하도록 불변 복사합니다.
        participants = participants == null ? List.of() : List.copyOf(participants);
        kickedUserUuids = kickedUserUuids == null ? List.of() : List.copyOf(kickedUserUuids);
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
            updatedParticipants, createdAt, updatedAt, kickedUserUuids);
    }

    public FlipbookRoomState withParticipantsAndKickedUserUuids(List<FlipbookRoomParticipant> updatedParticipants,
        List<String> updatedKickedUserUuids, LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, status, hostUserUuid, timeLimitSeconds, minParticipants, maxParticipants,
            updatedParticipants, createdAt, updatedAt, updatedKickedUserUuids);
    }

    // 나머지 값은 그대로, updatedAt만 현재 시각으로 변경 (record라 기존의 객체 값을 변경할 수 없음)
    public FlipbookRoomState withTimeLimitSeconds(int updatedTimeLimitSeconds, LocalDateTime updatedAt) {
        return new FlipbookRoomState(roomCode, status, hostUserUuid, updatedTimeLimitSeconds, minParticipants,
            maxParticipants, participants, createdAt, updatedAt, kickedUserUuids);
    }

}
