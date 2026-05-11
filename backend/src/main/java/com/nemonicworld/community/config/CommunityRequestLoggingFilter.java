package com.nemonicworld.community.config;

import com.nemonicworld.global.logging.StructuredEventLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 커뮤니티 사용자/관리자 API의 느린 요청을 구조화 로그로 남깁니다.
 */
@Component
public class CommunityRequestLoggingFilter extends OncePerRequestFilter {

    private static final String COMMUNITY_API_PREFIX = "/api/v1/community/";
    private static final String ADMIN_COMMUNITY_API_PREFIX = "/api/v1/admin/community/";
    private static final String ANONYMOUS_USER_UUID_HEADER = "Anonymous-User-UUID";
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1_000L;
    private static final ConcurrentMap<String, AtomicInteger> INVALID_UUID_ATTEMPTS = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return !path.startsWith(COMMUNITY_API_PREFIX) && !path.startsWith(ADMIN_COMMUNITY_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Exception failure = null;
        try {
            logMissingRequiredAnonymousHeaderIfNeeded(request);
            logInvalidAnonymousUuidIfNeeded(request);
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            if (durationMs > SLOW_REQUEST_THRESHOLD_MS) {
                StructuredEventLogger.apiWarn("community_api_slow_request", "community api slow request",
                    StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(),
                        "status", failure == null ? response.getStatus() : 500, "duration_ms", durationMs,
                        "threshold_ms", SLOW_REQUEST_THRESHOLD_MS),
                    failure);
            }
        }
    }

    private void logMissingRequiredAnonymousHeaderIfNeeded(HttpServletRequest request) {
        if (!isAnonymousUserRequiredEndpoint(request) || hasText(request.getHeader(ANONYMOUS_USER_UUID_HEADER))) {
            return;
        }

        StructuredEventLogger.apiWarn("community_missing_user_header", "community missing anonymous user header",
            StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(),
                "reason_code", "missing_anonymous_user_uuid", "header_name", ANONYMOUS_USER_UUID_HEADER),
            null);
    }

    private boolean isAnonymousUserRequiredEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!path.startsWith(COMMUNITY_API_PREFIX)) {
            return false;
        }

        if ("POST".equals(method) && "/api/v1/community/memos".equals(path)) {
            return true;
        }

        if ("PATCH".equals(method) || "DELETE".equals(method)) {
            return path.startsWith("/api/v1/community/memos/");
        }

        return "POST".equals(method) && path.startsWith("/api/v1/community/memos/") && path.endsWith("/reports");
    }

    private void logInvalidAnonymousUuidIfNeeded(HttpServletRequest request) {
        String anonymousUserUuid = request.getHeader(ANONYMOUS_USER_UUID_HEADER);
        if (!hasText(anonymousUserUuid) || !request.getRequestURI().startsWith(COMMUNITY_API_PREFIX)) {
            return;
        }

        try {
            UUID.fromString(anonymousUserUuid.trim());
        } catch (IllegalArgumentException e) {
            int attemptCount = INVALID_UUID_ATTEMPTS
                .computeIfAbsent(request.getRemoteAddr(), key -> new AtomicInteger()).incrementAndGet();
            StructuredEventLogger.apiWarn("community_invalid_uuid_repeated", "community invalid anonymous UUID header",
                StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(),
                    "reason_code", "invalid_anonymous_user_uuid", "header_name", ANONYMOUS_USER_UUID_HEADER,
                    "attempt_count", attemptCount, "repeated", attemptCount > 1),
                e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
