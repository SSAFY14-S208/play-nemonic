package com.nemonicworld.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminClientInfoResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_TRACE_ID = "X-Trace-Id";
    private static final String X_REQUEST_ID = "X-Request-Id";
    private static final String UNKNOWN = "unknown";

    public AdminClientInfo resolve(HttpServletRequest request) {
        return new AdminClientInfo(resolveIpAddress(request), resolveHeader(request, HttpHeaders.USER_AGENT),
            resolveTraceId(request));
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        return StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr() : UNKNOWN;
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(X_TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }

        String requestId = request.getHeader(X_REQUEST_ID);
        return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
    }

    private String resolveHeader(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        return StringUtils.hasText(headerValue) ? headerValue : UNKNOWN;
    }
}
