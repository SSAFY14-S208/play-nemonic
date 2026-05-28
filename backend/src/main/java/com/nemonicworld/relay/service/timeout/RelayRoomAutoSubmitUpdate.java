package com.nemonicworld.relay.service.timeout;

import com.nemonicworld.relay.redis.RelayRoomAssignment;
import java.util.List;

public record RelayRoomAutoSubmitUpdate(List<RelayRoomAssignment> assignments,
    List<RelayRoomAutoSubmissionResult> autoSubmissions) {

    public RelayRoomAutoSubmitUpdate {
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        autoSubmissions = autoSubmissions == null ? List.of() : List.copyOf(autoSubmissions);
    }
}
