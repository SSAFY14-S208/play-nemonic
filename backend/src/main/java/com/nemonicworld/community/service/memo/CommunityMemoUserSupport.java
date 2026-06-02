package com.nemonicworld.community.service.memo;

import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class CommunityMemoUserSupport {

    private final AnonymousUserResolver anonymousUserResolver;

    CommunityMemoUserSupport(AnonymousUserResolver anonymousUserResolver) {
        this.anonymousUserResolver = anonymousUserResolver;
    }

    UUID parseUserUuid(String userUuidValue) {
        return anonymousUserResolver.parseUuid(userUuidValue);
    }

    void resolveUser(UUID userUuid) {
        anonymousUserResolver.resolve(userUuid);
    }

    UUID parseOptionalViewerUuid(String viewerUserUuidValue) {
        if (!StringUtils.hasText(viewerUserUuidValue)) {
            return null;
        }

        return anonymousUserResolver.parseUuid(viewerUserUuidValue);
    }

    UUID parseUuidQuietly(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean isOwnedByViewer(UUID userId, UUID viewerUserUuid) {
        return viewerUserUuid != null && viewerUserUuid.equals(userId);
    }
}
