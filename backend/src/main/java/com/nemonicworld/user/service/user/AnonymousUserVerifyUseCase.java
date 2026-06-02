package com.nemonicworld.user.service.user;

import com.nemonicworld.user.dto.response.AnonymousUserVerifyResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnonymousUserVerifyUseCase {

    private static final String UNKNOWN_USER_AGENT = "unknown";

    private final AnonymousUserResolver anonymousUserResolver;

    public AnonymousUserVerifyUseCase(AnonymousUserResolver anonymousUserResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
    }

    @Transactional
    public AnonymousUserVerifyResponse verifyAnonymousUser(String userUuidValue, String userAgent) {
        AppUser appUser = anonymousUserResolver.resolve(userUuidValue);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        appUser.updateLastSeen(normalizeUserAgent(userAgent), now);

        return new AnonymousUserVerifyResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getLastSeenAt());
    }

    private String normalizeUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            return userAgent;
        }

        return UNKNOWN_USER_AGENT;
    }
}
