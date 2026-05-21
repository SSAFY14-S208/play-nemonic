package com.nemonicworld.common.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.logging.StructuredEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final String FORBIDDEN_MESSAGE = "접근 권한이 없습니다.";
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";
    private static final String ADMIN_LOGS_PATH_PREFIX = "/api/v1/admin/logs";
    private static final String ADMIN_METRICS_PATH_PREFIX = "/api/v1/admin/metrics";
    private static final String BACKOFFICE_PATH_PREFIX = "/api/v1/backoffice";

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
        AccessDeniedException accessDeniedException) throws IOException {
        if (isAdminOrBackofficePath(request)) {
            StructuredEventLogger.auditWarn("admin_forbidden", "admin forbidden", resolveTraceId(request),
                StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(),
                    "reason_code", accessDeniedException.getClass().getSimpleName()));
        }
        StructuredEventLogger.apiWarn("api_forbidden", "api forbidden", resolveTraceId(request),
            StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(), "status",
                HttpStatus.FORBIDDEN.value(), "result", "failed", "reason_code",
                accessDeniedException.getClass().getSimpleName()),
            accessDeniedException);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (isAdminLogsPath(request)) {
            objectMapper.writeValue(response.getWriter(), Map.of("code", "ADMIN_LOGS_FORBIDDEN", "message",
                FORBIDDEN_MESSAGE, "timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            return;
        }
        if (isAdminMetricsPath(request)) {
            objectMapper.writeValue(response.getWriter(),
                Map.of("success", false, "code", "ADMIN_METRICS_FORBIDDEN", "message", FORBIDDEN_MESSAGE, "timestamp",
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            return;
        }

        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(FORBIDDEN_MESSAGE, null));
    }

    private boolean isAdminOrBackofficePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();

        return requestUri.startsWith(ADMIN_PATH_PREFIX) || requestUri.startsWith(BACKOFFICE_PATH_PREFIX);
    }

    private boolean isAdminLogsPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ADMIN_LOGS_PATH_PREFIX);
    }

    private boolean isAdminMetricsPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ADMIN_METRICS_PATH_PREFIX);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-Id");
        }

        return traceId;
    }
}
