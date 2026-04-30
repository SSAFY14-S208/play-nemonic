package com.nemonicworld.user.service;

import com.nemonicworld.user.domain.AppUser;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
/**
 * 사용자 생성 유스케이스를 처리하는 서비스입니다.
 *
 * <p>현재는 앱 첫 진입 시 필요한 익명 사용자 UUID 발급 흐름을 담당합니다.
 */
public class UserService {

    // User-Agent는 선택 헤더이므로 수집하지 못한 경우 명시적인 기본값으로 저장합니다.
    private static final String UNKNOWN_USER_AGENT = "unknown";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 매 호출마다 새로운 익명 사용자를 생성하고 저장합니다.
     *
     * <p>클라이언트 UUID를 입력받지 않고 서버가 UUID를 직접 발급합니다.
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
     * User-Agent가 없거나 공백이면 DB의 NOT NULL 제약을 만족하도록 unknown으로 정규화합니다.
     */
    private String normalizeUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            return userAgent;
        }

        return UNKNOWN_USER_AGENT;
    }
}
