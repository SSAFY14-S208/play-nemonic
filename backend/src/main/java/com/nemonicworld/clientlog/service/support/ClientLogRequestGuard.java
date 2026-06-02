package com.nemonicworld.clientlog.service.support;

import com.nemonicworld.clientlog.config.ClientLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientLogRequestGuard {

    private static final Pattern BOT_USER_AGENT_PATTERN = Pattern
        .compile("(?i).*(bot|crawler|spider|preview|facebookexternalhit|slackbot|discordbot).*");
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private final ClientLogProperties properties;

    public ClientLogRequestGuard(ClientLogProperties properties) {
        this.properties = properties;
    }

    public boolean isBot(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return StringUtils.hasText(userAgent) && BOT_USER_AGENT_PATTERN.matcher(userAgent).matches();
    }

    public boolean isAllowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin)) {
            return properties.getAllowedOrigins().contains(normalizeOrigin(origin));
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (StringUtils.hasText(referer)) {
            return properties.getAllowedOrigins().contains(normalizeOrigin(referer));
        }

        return false;
    }

    public String resolveIpAddress(HttpServletRequest request) {
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

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return "";
            }

            StringBuilder origin = new StringBuilder(uri.getScheme().toLowerCase(Locale.ROOT)).append("://")
                .append(uri.getHost().toLowerCase(Locale.ROOT));
            if (uri.getPort() >= 0) {
                origin.append(":").append(uri.getPort());
            }

            return origin.toString();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
