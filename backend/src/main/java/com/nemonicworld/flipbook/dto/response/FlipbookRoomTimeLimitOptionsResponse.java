package com.nemonicworld.flipbook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemonicworld.flipbook.service.support.FlipbookRoomTimeLimitSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "플립북 방 제한 시간 선택지")
public record FlipbookRoomTimeLimitOptionsResponse(
    @JsonProperty("default") @Schema(description = "기본 제한 시간(초)", example = "60") int defaultSeconds,
    @JsonProperty("allowed") @Schema(description = "허용 제한 시간(초)") List<Integer> allowedSeconds) {

    public static FlipbookRoomTimeLimitOptionsResponse from(FlipbookRoomTimeLimitSettings settings) {
        List<Integer> sortedAllowedSeconds = settings.allowedSeconds().stream().sorted(Comparator.naturalOrder())
            .toList();

        return new FlipbookRoomTimeLimitOptionsResponse(settings.defaultSeconds(), sortedAllowedSeconds);
    }
}
