package com.nemonicworld.community.service.moderation;

public class CommunityMemoModerationException extends RuntimeException {

    public CommunityMemoModerationException(String message) {
        super(message);
    }

    public CommunityMemoModerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
