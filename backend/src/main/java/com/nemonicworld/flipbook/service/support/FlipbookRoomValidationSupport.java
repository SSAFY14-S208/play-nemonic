package com.nemonicworld.flipbook.service.support;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.util.RoomCodeGenerator;
import com.nemonicworld.flipbook.dto.request.FlipbookRoomSettingsRequest;
import com.nemonicworld.user.entity.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FlipbookRoomValidationSupport {

    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";
    private static final String INVALID_ROOM_CODE_MESSAGE = "유효하지 않은 방코드입니다.";
    private static final String INVALID_TIME_LIMIT_SECONDS_MESSAGE = "제한 시간은 30초, 45초, 60초 중 하나여야 합니다.";

    private final RoomCodeGenerator roomCodeGenerator;
    private final FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider;

    public FlipbookRoomValidationSupport(RoomCodeGenerator roomCodeGenerator,
        FlipbookRuntimeSettingsProvider flipbookRuntimeSettingsProvider) {
        this.roomCodeGenerator = roomCodeGenerator;
        this.flipbookRuntimeSettingsProvider = flipbookRuntimeSettingsProvider;
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

    public int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request) {
        return resolveTimeLimitSeconds(request, flipbookRuntimeSettingsProvider.currentRoomTimeLimitSettings());
    }

    public int resolveTimeLimitSeconds(FlipbookRoomSettingsRequest request, FlipbookRoomTimeLimitSettings settings) {
        if (request == null || request.timeLimitSeconds() == null || !settings.allows(request.timeLimitSeconds())) {
            throw new BadRequestException(INVALID_TIME_LIMIT_SECONDS_MESSAGE);
        }

        return request.timeLimitSeconds();
    }

    public int resolveDefaultTotalRounds() {
        return resolveDefaultTotalRounds(flipbookRuntimeSettingsProvider.currentMinFramesPerFlipbook());
    }

    public int resolveDefaultTotalRounds(int minFramesPerFlipbook) {
        return minFramesPerFlipbook;
    }
}
