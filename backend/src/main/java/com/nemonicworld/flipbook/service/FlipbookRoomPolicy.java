package com.nemonicworld.flipbook.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.user.entity.AppUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 플립북 방 유스케이스들이 공유하는 기본 정책입니다.
 */
@Component
public class FlipbookRoomPolicy {

    static final int DEFAULT_TIME_LIMIT_SECONDS = 45;
    static final int MIN_PARTICIPANTS = 2;
    static final int MAX_PARTICIPANTS = 6;
    static final int HOST_JOIN_ORDER = 0;

    private static final String NICKNAME_REQUIRED_MESSAGE = "닉네임을 먼저 설정해주세요.";

    /**
     * 기본 닉네임인 '익명' 상태로는 협동 방을 만들 수 없도록 검증합니다.
     */
    void validateNicknameRegistered(AppUser appUser) {
        if (!StringUtils.hasText(appUser.getNickname()) || AppUser.ANONYMOUS_NICKNAME.equals(appUser.getNickname())) {
            throw new BadRequestException(NICKNAME_REQUIRED_MESSAGE);
        }
    }
}
