package com.nemonicworld.user.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.user.dto.request.AnonymousUserNicknameRequest;
import com.nemonicworld.user.dto.request.AnonymousUserVerifyRequest;
import com.nemonicworld.user.dto.response.AnonymousUserNicknameResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.dto.response.AnonymousUserVerifyResponse;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
/**
 * 익명 사용자 유스케이스를 처리하는 서비스입니다.
 *
 * UUID 발급, 기존 UUID 검증, 닉네임 설정/수정 흐름을 담당합니다.
 */
public class UserService {

    // User-Agent는 선택 헤더이므로 수집하지 못한 경우 명시적인 기본값으로 저장합니다.
    private static final String UNKNOWN_USER_AGENT = "unknown";
    private static final String INVALID_UUID_MESSAGE = "유효하지 않은 UUID 형식입니다.";
    private static final String USER_NOT_FOUND_MESSAGE = "존재하지 않는 사용자입니다.";
    private static final String INVALID_NICKNAME_MESSAGE = "닉네임은 1자 이상 10자 이하로 입력해주세요.";
    private static final int MAX_NICKNAME_CODE_POINTS = 10;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 매 호출마다 새로운 익명 사용자를 생성하고 저장합니다.
     *
     * 클라이언트 UUID를 입력받지 않고 서버가 UUID를 직접 발급합니다.
     */
    @Transactional
    public AnonymousUserResponse createAnonymousUser(String userAgent) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(UUID.randomUUID(), normalizeUserAgent(userAgent), now);
        AppUser savedUser = userRepository.save(appUser);

        return new AnonymousUserResponse(savedUser.getId().toString(), savedUser.getNickname(),
            savedUser.getCreatedAt());
    }

    /**
     * 클라이언트가 보관 중인 익명 사용자 UUID를 검증하고 재방문 정보를 갱신합니다.
     */
    @Transactional
    public AnonymousUserVerifyResponse verifyAnonymousUser(AnonymousUserVerifyRequest request, String userAgent) {
        UUID userUuid = parseUserUuid(request == null ? null : request.userUuid());
        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        appUser.updateLastSeen(normalizeUserAgent(userAgent), now);

        return new AnonymousUserVerifyResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getLastSeenAt());
    }

    /**
     * 서버에 등록된 익명 사용자의 닉네임을 설정하거나 수정합니다.
     */
    @Transactional
    public AnonymousUserNicknameResponse updateAnonymousUserNickname(AnonymousUserNicknameRequest request) {
        UUID userUuid = parseUserUuid(request == null ? null : request.userUuid());
        // 닉네임 안의 공백은 허용하므로 저장 전 trim하지 않고 원문을 유지합니다.
        String nickname = request == null ? null : request.nickname();

        validateNickname(nickname);

        AppUser appUser = userRepository.findById(userUuid)
            .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        appUser.updateNickname(nickname, now);

        return new AnonymousUserNicknameResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getUpdatedAt());
    }

    private UUID parseUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }

        try {
            return UUID.fromString(userUuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    private void validateNickname(String nickname) {
        if (!StringUtils.hasText(nickname) || isNicknameTooLong(nickname)) {
            throw new BadRequestException(INVALID_NICKNAME_MESSAGE);
        }
    }

    // 이모지를 Java char 2개로 과계산하지 않도록 사용자 기준에 더 가까운 code point 개수로 길이를 봅니다.
    private boolean isNicknameTooLong(String nickname) {
        return nickname.codePointCount(0, nickname.length()) > MAX_NICKNAME_CODE_POINTS;
    }

    /**
     * User-Agent가 없거나 공백이면 DB의 NOT NULL 제약을 만족하도록 unknown으로 정규화합니다.
     */
    private String normalizeUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            return userAgent;
        }

        return UNKNOWN_USER_AGENT;
    }
}
