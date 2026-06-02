package com.nemonicworld.user.service.user;

import com.nemonicworld.user.dto.response.AnonymousUserProfileResponse;
import com.nemonicworld.user.entity.AppUser;
import com.nemonicworld.user.service.AnonymousUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymousUserProfileUseCase {

    private final AnonymousUserResolver anonymousUserResolver;

    public AnonymousUserProfileUseCase(AnonymousUserResolver anonymousUserResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
    }

    @Transactional(readOnly = true)
    public AnonymousUserProfileResponse getAnonymousUserProfile(String userUuidValue) {
        AppUser appUser = anonymousUserResolver.resolve(userUuidValue);

        return new AnonymousUserProfileResponse(appUser.getId().toString(), appUser.getNickname(),
            appUser.getBirthday(), appUser.getBirthtime(), appUser.getIsLunar(), appUser.getCreatedAt(),
            appUser.getUpdatedAt(), appUser.getLastSeenAt());
    }
}
