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

    /**
     * 모더레이션 기능 활성 여부를 반환하며, 설정이 없으면 운영 기본값인 활성 상태로 봅니다.
     */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /**
     * FastAPI 모더레이션 서버 base URL을 반환하고, 설정이 없으면 로컬 기본값을 사용합니다.
     */
    public String resolvedBaseUrl() {
        return StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost:8000";
    }

    /**
     * FastAPI 검수 endpoint path를 반환하고, 설정이 없으면 `/check`를 사용합니다.
     */
    public String resolvedCheckPath() {
        return StringUtils.hasText(checkPath) ? checkPath : "/check";
    }

    /**
     * FastAPI 연결 timeout을 밀리초 단위로 반환합니다.
     */
    public long resolvedConnectTimeoutMs() {
        return connectTimeoutMs == null ? 1000L : connectTimeoutMs;
    }

    /**
     * FastAPI 응답 대기 timeout을 밀리초 단위로 반환합니다.
     */
    public long resolvedReadTimeoutMs() {
        return readTimeoutMs == null ? 3000L : readTimeoutMs;
    }

    /**
     * 모더레이션 장애 시 게시를 막을지 여부를 반환합니다.
     */
    public boolean isFailClosed() {
        return failClosed != null && failClosed;
    }
}
