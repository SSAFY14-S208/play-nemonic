package com.nemonicworld.user.service.user;

import com.nemonicworld.global.logging.StructuredEventLogger;
import com.nemonicworld.user.dto.response.AnonymousUserResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnonymousUserCreateUseCase {

    private static final String UNKNOWN_USER_AGENT = "unknown";

    private final UserRepository userRepository;

    public AnonymousUserCreateUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AnonymousUserResponse createAnonymousUser(String userAgent) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        AppUser appUser = AppUser.createAnonymous(UUID.randomUUID(), normalizeUserAgent(userAgent), now);
        AppUser savedUser = userRepository.save(appUser);
        StructuredEventLogger.apiBusiness("anonymous_user_created", "user", savedUser.getId().toString(),
            StructuredEventLogger.metadata("result", "success", "user_agent_present", StringUtils.hasText(userAgent),
                "created_at", savedUser.getCreatedAt()));

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
