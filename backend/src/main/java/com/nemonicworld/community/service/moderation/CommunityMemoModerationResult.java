package com.nemonicworld.community.service.moderation;

import com.fasterxml.jackson.databind.JsonNode;

public record CommunityMemoModerationResult(boolean allowed, String ocrText, JsonNode categories) {

    public static CommunityMemoModerationResult allowedResult() {
        return new CommunityMemoModerationResult(true, null, null);
    }
}
