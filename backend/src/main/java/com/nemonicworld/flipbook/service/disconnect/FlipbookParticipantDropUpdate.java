package com.nemonicworld.flipbook.service.disconnect;

import com.nemonicworld.flipbook.redis.FlipbookRoomParticipant;
import java.util.List;

public record FlipbookParticipantDropUpdate(List<FlipbookRoomParticipant> participants, boolean changed,
    String hostUserUuid, List<FlipbookDroppedParticipantResult> droppedParticipants,
    FlipbookHostChangeResult hostChange) {
}
