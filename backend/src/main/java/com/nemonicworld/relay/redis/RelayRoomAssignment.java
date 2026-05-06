package com.nemonicworld.relay.redis;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import java.time.LocalDateTime;

/**
 * Redis 릴레이 방 상태에 저장되는 캔버스별 파트 배정 정보입니다.
 */
public record RelayRoomAssignment(int canvasIndex, RelayDrawingPart part, String assignedUserUuid,
    RelayAssignmentStatus status, String fileId, String objectKey, String hintObjectKey, boolean empty,
    boolean autoSubmitted, LocalDateTime submittedAt) {
}
