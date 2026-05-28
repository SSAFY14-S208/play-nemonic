package com.nemonicworld.relay.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.relay.dto.request.RelayRoomSettingsRequest;
import com.nemonicworld.user.entity.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RelayRoomValidationSupport {

    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "허용되지 않는 릴레이 제한 시간입니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final RelayRuntimeSettingsProvider relayRuntimeSettingsProvider;

    public RelayRoomValidationSupport(RoomCodeGenerator roomCodeGenerator,
        RelayRuntimeSettingsProvider relayRuntimeSettingsProvider) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.relayRuntimeSettingsProvider = relayRuntimeSettingsProvider;
    }

    public void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }

    public void validateRoomCode(String roomCodeValue) {
        if (!roomCodeGenerator.isValid(roomCodeValue)) {
            throw new BadRequestException(INVALID_ROOM_CODE_MESSAGE);
        }
    }

    public int resolveTimeLimitSeconds(RelayRoomSettingsRequest request) {
        return resolveTimeLimitSeconds(request, relayRuntimeSettingsProvider.currentRoomTimeLimitSettings());
    }

    public int resolveTimeLimitSeconds(RelayRoomSettingsRequest request, RelayRoomTimeLimitSettings settings) {
        if (request == null || request.timeLimitSeconds() == null || !settings.allows(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }
}
