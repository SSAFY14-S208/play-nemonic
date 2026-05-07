package com.nemonicworld.user.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 익명 사용자 UUID 헤더 값을 검증하고 기존 사용자로 해석하는 공통 컴포넌트
 */
@Component
public class AnonymousUserResolver {

    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";

    private final UserRepository userRepository;

    public AnonymousUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 헤더 문자열을 UUID로 변환하되, 사용자 존재 여부는 확인하지 않습니다.
     */
    public UUID parseUuid(String userUuidValue) {
        if (!StringUtils.hasText(userUuidValue)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(userUuidValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    /**
     * 헤더 문자열을 기존 익명 사용자로 해석
     */
    public AppUser resolve(String userUuidValue) {
        return resolve(parseUuid(userUuidValue));
    }

    /**
     * UUID에 해당하는 기존 익명 사용자를 조회
     */
    public AppUser resolve(UUID userUuid) {
        if (userUuid == null) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        return userRepository.findById(userUuid).orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
    }
}
