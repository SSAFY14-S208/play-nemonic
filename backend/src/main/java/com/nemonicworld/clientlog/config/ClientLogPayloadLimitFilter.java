package com.nemonicworld.clientlog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class ClientLogPayloadLimitFilter extends OncePerRequestFilter {

    private static final String CLIENT_LOG_PATH = "/api/logs/client";
    private static final String PAYLOAD_TOO_LARGE_MESSAGE = "로그 요청 본문은 1MB 이하로 전송해 주세요.";

    private final ClientLogProperties properties;
    private final ObjectMapper objectMapper;

    public ClientLogPayloadLimitFilter(ClientLogProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !CLIENT_LOG_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > properties.getMaxPayloadBytes()) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(PAYLOAD_TOO_LARGE_MESSAGE, null));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
