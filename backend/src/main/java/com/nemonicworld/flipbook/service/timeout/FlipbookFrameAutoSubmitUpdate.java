package com.nemonicworld.flipbook.service.timeout;

import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import java.util.List;

public record FlipbookFrameAutoSubmitUpdate(List<FlipbookFrameAssignment> assignments,
    List<FlipbookFrameAutoSubmissionResult> autoSubmissions) {

    public FlipbookFrameAutoSubmitUpdate {
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        autoSubmissions = autoSubmissions == null ? List.of() : List.copyOf(autoSubmissions);
    }
}
