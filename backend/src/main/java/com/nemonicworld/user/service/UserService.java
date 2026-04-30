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
public class UserService {

    private static final String UNKNOWN_USER_AGENT = "unknown";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AnonymousUserResponse createAnonymousUser(String userAgent) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(UUID.randomUUID(), normalizeUserAgent(userAgent), now);
        AppUser savedUser = userRepository.save(appUser);

        return new AnonymousUserResponse(savedUser.getId().toString(), savedUser.getNickname(),
            savedUser.getCreatedAt());
    }

    private String normalizeUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            return userAgent;
        }

        return UNKNOWN_USER_AGENT;
    }
}
