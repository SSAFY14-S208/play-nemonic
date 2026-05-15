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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다.";
    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin";
    private static final String ADMIN_LOGS_PATH_PREFIX = "/api/v1/admin/logs";
    private static final String BACKOFFICE_PATH_PREFIX = "/api/v1/backoffice";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException authenticationException) throws IOException {
        if (isAdminOrBackofficePath(request)) {
            StructuredEventLogger.auditWarn("admin_access_denied", "admin access denied", resolveTraceId(request),
                StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(),
                    "reason_code", authenticationException.getClass().getSimpleName()));
        }
        StructuredEventLogger.apiWarn("api_unauthorized", "api unauthorized", resolveTraceId(request),
            StructuredEventLogger.metadata("path", request.getRequestURI(), "method", request.getMethod(), "status",
                HttpStatus.UNAUTHORIZED.value(), "result", "failed", "reason_code",
                authenticationException.getClass().getSimpleName()),
            authenticationException);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (isAdminLogsPath(request)) {
            objectMapper.writeValue(response.getWriter(),
                Map.of("code", "ADMIN_LOGS_UNAUTHORIZED", "message", UNAUTHORIZED_MESSAGE, "timestamp",
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
            return;
        }

        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(UNAUTHORIZED_MESSAGE, null));
    }

    private boolean isAdminOrBackofficePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();

        return requestUri.startsWith(ADMIN_PATH_PREFIX) || requestUri.startsWith(BACKOFFICE_PATH_PREFIX);
    }

    private boolean isAdminLogsPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ADMIN_LOGS_PATH_PREFIX);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-Id");
        }

        return traceId;
    }
}
