package com.nemonicworld.flipbook.redis;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import java.time.LocalDateTime;

/**
 * Redis 플립북 방 상태에 저장되는 프레임 배정 정보입니다.
 */
public record FlipbookFrameAssignment(int flipbookIndex, int frameIndex, int round, String assignedUserUuid,
    FlipbookFrameAssignmentStatus status, String fileId, String objectKey, boolean empty, boolean autoSubmitted,
    LocalDateTime submittedAt) {
}
