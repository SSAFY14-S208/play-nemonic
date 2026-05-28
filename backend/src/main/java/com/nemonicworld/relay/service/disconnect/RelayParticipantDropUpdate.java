package com.nemonicworld.relay.service.disconnect;

import com.nemonicworld.relay.redis.RelayRoomParticipant;
import java.util.List;

public record RelayParticipantDropUpdate(List<RelayRoomParticipant> participants, boolean changed, String hostUserUuid,
    List<RelayDroppedParticipantResult> droppedParticipants, RelayHostChangeResult hostChange) {
}
