package com.nemonicworld.community.config;

import com.nemonicworld.global.logging.StructuredEventLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 커뮤니티 사용자/관리자 API의 느린 요청을 구조화 로그로 남깁니다.
 */
@Component
public class CommunityRequestLoggingFilter extends OncePerRequestFilter {

    private static final String COMMUNITY_API_PREFIX = "/api/v1/community/";
    private static final String ADMIN_COMMUNITY_API_PREFIX = "/api/v1/admin/community/";
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1_000L;

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
}
