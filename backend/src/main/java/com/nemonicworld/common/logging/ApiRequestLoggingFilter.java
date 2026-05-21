package com.nemonicworld.common.logging;

import com.nemonicworld.common.header.AnonymousUserHeaders;
import com.nemonicworld.common.jwt.AdminPrincipal;
import com.nemonicworld.global.logging.StructuredEventLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String X_TRACE_ID = "X-Trace-Id";
    private static final String X_REQUEST_ID = "X-Request-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !resolvePath(request).startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            if (!request.isAsyncStarted()) {
                logRequest(request, response, startedAt, failure);
            }
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startedAt,
        Throwable failure) {
        int status = response.getStatus();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        Map<String, Object> metadata = StructuredEventLogger.metadata("path", resolvePath(request), "method",
            request.getMethod(), "status", status, "duration_ms", durationMs, "result",
            failure == null && status < 400 ? "success" : "failed", "reason_code",
            failure == null ? "status_%d".formatted(status) : failure.getClass().getSimpleName());
        safeUserUuid(request, metadata);
        safeAdminId(metadata);

        if (failure == null && status < 400) {
            StructuredEventLogger.apiSystem("api_request_completed", "api request completed", resolveTraceId(request),
                metadata);
            return;
        }

        StructuredEventLogger.apiWarn("api_request_failed", "api request failed", resolveTraceId(request), metadata,
            failure);
    }

    private String resolvePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }

        return requestUri;
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(X_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(X_REQUEST_ID);
        }

        return traceId;
    }

    private void safeUserUuid(HttpServletRequest request, Map<String, Object> metadata) {
        String userUuid = request.getHeader(AnonymousUserHeaders.ANONYMOUS_USER_UUID);
        if (userUuid == null || userUuid.isBlank()) {
            return;
        }

        try {
            metadata.put("uuid", UUID.fromString(userUuid.trim()).toString());
        } catch (IllegalArgumentException ignored) {
            metadata.put("uuid_present", true);
        }
    }

    private void safeAdminId(Map<String, Object> metadata) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal adminPrincipal)) {
            return;
        }

        metadata.put("admin_id", adminPrincipal.id().toString());
    }
}
