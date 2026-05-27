package com.nemonicworld.backoffice.setting.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemonicworld.backoffice.setting.service.SystemParameterSettingKeys;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.flipbook.service.support.FlipbookMinFramesPerFlipbookSettings;
import com.nemonicworld.flipbook.service.support.FlipbookReconnectGraceSettings;
import com.nemonicworld.flipbook.service.support.FlipbookRoomParticipantLimit;
import com.nemonicworld.flipbook.service.support.FlipbookRoomTimeLimitSettings;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookMinFramesPerFlipbookSettingsException;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookReconnectGraceSettingsException;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookRoomParticipantLimitException;
import com.nemonicworld.flipbook.service.support.InvalidFlipbookRoomTimeLimitSettingsException;
import com.nemonicworld.infinitecanvas.service.support.InfiniteCanvasParticipantLimit;
import com.nemonicworld.infinitecanvas.service.support.InvalidInfiniteCanvasParticipantLimitException;
import com.nemonicworld.relay.service.support.InvalidRelayReconnectGraceSettingsException;
import com.nemonicworld.relay.service.support.InvalidRelayRoomParticipantLimitException;
import com.nemonicworld.relay.service.support.InvalidRelayRoomTimeLimitSettingsException;
import com.nemonicworld.relay.service.support.RelayReconnectGraceSettings;
import com.nemonicworld.relay.service.support.RelayRoomParticipantLimit;
import com.nemonicworld.relay.service.support.RelayRoomTimeLimitSettings;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class SystemParameterValidationSupport {

    private static final String INVALID_RELAY_PARTICIPANT_LIMIT_MESSAGE = "릴레이 방 참여 인원 설정이 올바르지 않습니다.";
    private static final String INVALID_RELAY_TIME_LIMIT_MESSAGE = "릴레이 방 그리기 제한 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_RELAY_RECONNECT_GRACE_MESSAGE = "릴레이 재연결 유예 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_PARTICIPANT_LIMIT_MESSAGE = "플립북 방 참여 인원 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_TIME_LIMIT_MESSAGE = "플립북 방 제한 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_MIN_FRAMES_MESSAGE = "플립북 최소 프레임 수 설정이 올바르지 않습니다.";
    private static final String INVALID_FLIPBOOK_RECONNECT_GRACE_MESSAGE = "플립북 재연결 유예 시간 설정이 올바르지 않습니다.";
    private static final String INVALID_INFINITE_CANVAS_PARTICIPANT_LIMIT_MESSAGE = "무한 캔버스 참여 인원 설정이 올바르지 않습니다.";
    private static final String INVALID_SYSTEM_PARAMETER_VALUE_MESSAGE = "시스템 파라미터 값이 올바르지 않습니다.";

    private final Map<String, Consumer<JsonNode>> validatorsByKey;

    public SystemParameterValidationSupport() {
        this.validatorsByKey = createValidatorsByKey();
    }

    public void validateSystemParameterValue(String key, JsonNode value) {
        Consumer<JsonNode> validator = validatorsByKey.get(key);
        if (validator != null) {
            validator.accept(value);
        }
    }

    private Map<String, Consumer<JsonNode>> createValidatorsByKey() {
        return Map.ofEntries(
            Map.entry(SystemParameterSettingKeys.RELAY_ROOM_PARTICIPANT_LIMIT, this::validateRelayParticipantLimit),
            Map.entry(SystemParameterSettingKeys.RELAY_ROOM_TIME_LIMIT_SECONDS, this::validateRelayRoomTimeLimit),
            Map.entry(SystemParameterSettingKeys.RELAY_RECONNECT_GRACE_SECONDS, this::validateRelayReconnectGrace),
            Map.entry(SystemParameterSettingKeys.FLIPBOOK_ROOM_PARTICIPANT_LIMIT,
                this::validateFlipbookParticipantLimit),
            Map.entry(SystemParameterSettingKeys.FLIPBOOK_ROOM_TIME_LIMIT_SECONDS, this::validateFlipbookRoomTimeLimit),
            Map.entry(SystemParameterSettingKeys.FLIPBOOK_MIN_FRAMES_PER_FLIPBOOK,
                this::validateFlipbookMinFramesPerFlipbook),
            Map.entry(SystemParameterSettingKeys.FLIPBOOK_RECONNECT_GRACE_SECONDS,
                this::validateFlipbookReconnectGrace),
            Map.entry(SystemParameterSettingKeys.INFINITE_CANVAS_PARTICIPANT_LIMIT,
                this::validateInfiniteCanvasParticipantLimit),
            Map.entry(SystemParameterSettingKeys.COMMUNITY_MAX_MEMO_COUNT, this::validatePositiveValue),
            Map.entry(SystemParameterSettingKeys.COMMUNITY_REPORT_HIDE_THRESHOLD, this::validatePositiveValue),
            Map.entry(SystemParameterSettingKeys.FORTUNE_DAILY_LIMIT, this::validatePositiveValue), Map.entry(
                SystemParameterSettingKeys.CS_INQUIRY_UNRESOLVED_ALERT_THRESHOLD_HOURS, this::validatePositiveValue));
    }

    private void validateRelayParticipantLimit(JsonNode value) {
        try {
            RelayRoomParticipantLimit.fromJson(value);
        } catch (InvalidRelayRoomParticipantLimitException e) {
            throw new BadRequestException(INVALID_RELAY_PARTICIPANT_LIMIT_MESSAGE);
        }
    }

    private void validateRelayRoomTimeLimit(JsonNode value) {
        try {
            RelayRoomTimeLimitSettings.fromJson(value);
        } catch (InvalidRelayRoomTimeLimitSettingsException e) {
            throw new BadRequestException(INVALID_RELAY_TIME_LIMIT_MESSAGE);
        }
    }

    private void validateRelayReconnectGrace(JsonNode value) {
        try {
            RelayReconnectGraceSettings.fromJson(value);
        } catch (InvalidRelayReconnectGraceSettingsException e) {
            throw new BadRequestException(INVALID_RELAY_RECONNECT_GRACE_MESSAGE);
        }
    }

    private void validateFlipbookParticipantLimit(JsonNode value) {
        try {
            FlipbookRoomParticipantLimit.fromJson(value);
        } catch (InvalidFlipbookRoomParticipantLimitException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_PARTICIPANT_LIMIT_MESSAGE);
        }
    }

    private void validateFlipbookRoomTimeLimit(JsonNode value) {
        try {
            FlipbookRoomTimeLimitSettings.fromJson(value);
        } catch (InvalidFlipbookRoomTimeLimitSettingsException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_TIME_LIMIT_MESSAGE);
        }
    }

    private void validateFlipbookMinFramesPerFlipbook(JsonNode value) {
        try {
            FlipbookMinFramesPerFlipbookSettings.fromJson(value);
        } catch (InvalidFlipbookMinFramesPerFlipbookSettingsException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_MIN_FRAMES_MESSAGE);
        }
    }

    private void validateFlipbookReconnectGrace(JsonNode value) {
        try {
            FlipbookReconnectGraceSettings.fromJson(value);
        } catch (InvalidFlipbookReconnectGraceSettingsException e) {
            throw new BadRequestException(INVALID_FLIPBOOK_RECONNECT_GRACE_MESSAGE);
        }
    }

    private void validateInfiniteCanvasParticipantLimit(JsonNode value) {
        try {
            InfiniteCanvasParticipantLimit.fromJson(value);
        } catch (InvalidInfiniteCanvasParticipantLimitException e) {
            throw new BadRequestException(INVALID_INFINITE_CANVAS_PARTICIPANT_LIMIT_MESSAGE);
        }
    }

    private void validatePositiveValue(JsonNode value) {
        ensureObject(value);
        requirePositiveIntegerField(value, "value");
    }

    private void ensureObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new BadRequestException(INVALID_SYSTEM_PARAMETER_VALUE_MESSAGE);
        }
    }

    private int requirePositiveIntegerField(JsonNode value, String fieldName) {
        return requirePositiveIntegerValue(value.get(fieldName));
    }

    private int requirePositiveIntegerValue(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
            throw new BadRequestException(INVALID_SYSTEM_PARAMETER_VALUE_MESSAGE);
        }

        return value.asInt();
    }
}
