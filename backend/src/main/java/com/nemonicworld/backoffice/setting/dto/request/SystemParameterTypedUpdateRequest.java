package com.nemonicworld.backoffice.setting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

@Schema(description = "명시적 필드 기반 시스템 파라미터 수정 요청. 요청에 포함된 필드만 수정됩니다.")
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
