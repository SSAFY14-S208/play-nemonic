package com.nemonicworld.backoffice.setting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

@Schema(description = "Typed system parameter update request. Only included fields are updated.")
public record SystemParameterTypedUpdateRequest(@Valid SystemParameterPositiveValueRequest communityMaxMemoCount,

    @Valid SystemParameterParticipantLimitRequest relayRoomParticipantLimit,

    @Valid SystemParameterTimeLimitRequest relayRoomTimeLimitSeconds,

    @Valid SystemParameterPositiveValueRequest relayReconnectGraceSeconds,

    @Valid SystemParameterParticipantLimitRequest flipbookRoomParticipantLimit,

    @Valid SystemParameterTimeLimitRequest flipbookRoomTimeLimitSeconds,

    @Valid SystemParameterPositiveValueRequest flipbookMinFramesPerFlipbook,

    @Valid SystemParameterPositiveValueRequest flipbookReconnectGraceSeconds,

    @Valid SystemParameterPositiveValueRequest fortuneDailyLimit,

    @Valid SystemParameterPositiveValueRequest csInquiryUnresolvedAlertThresholdHours) {
}
