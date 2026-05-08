package com.nemonicworld.community.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 커뮤니티 메모 게시 전 FastAPI 검수 설정입니다.
 *
 * <p>
 * 값이 비어 있으면 로컬 개발 기본값을 사용하고, failClosed는 명시적으로 켠 경우에만 차단 우선으로 동작합니다.
 */
@ConfigurationProperties(prefix = "nemonic.community.moderation")
public record CommunityModerationProperties(Boolean enabled, String baseUrl, String checkPath, Long connectTimeoutMs,
    Long readTimeoutMs, Boolean failClosed) {

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

    public boolean isFailClosed() {
        return failClosed != null && failClosed;
    }
}
