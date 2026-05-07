package com.nemonicworld.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "nemonic.community.moderation")
public record CommunityModerationProperties(Boolean enabled, String baseUrl, String checkPath, Long connectTimeoutMs,
    Long readTimeoutMs) {

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public String resolvedBaseUrl() {
        return StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost:8000";
    }

    public String resolvedCheckPath() {
        return StringUtils.hasText(checkPath) ? checkPath : "/check";
    }

    public long resolvedConnectTimeoutMs() {
        return connectTimeoutMs == null ? 1000L : connectTimeoutMs;
    }

    public long resolvedReadTimeoutMs() {
        return readTimeoutMs == null ? 3000L : readTimeoutMs;
    }
}
