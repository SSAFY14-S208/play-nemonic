package com.nemonicworld.relay.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis에 JSON으로 저장되는 진행 중 릴레이 방 상태입니다.
 */
public record RelayRoomState(String roomCode, RelayRoomStatus status, String hostUserUuid, int timeLimitSeconds,
        int minParticipants, int maxParticipants, List<RelayRoomParticipant> participants, LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public RelayRoomState {
        // 생성 이후 외부에서 참여자 목록을 바꾸지 못하도록 불변 복사본으로 보관합니다.
        participants = List.copyOf(participants);
    }

    /**
     * Redis 저장 모델에는 중복 저장하지 않고 응답 변환 시 현재 목록 크기로 계산합니다.
     */
    public int participantCount() {
        return participants.size();
    }
}
