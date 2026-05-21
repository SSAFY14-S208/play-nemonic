package com.nemonicworld.clientlog.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ClientLogSanitizer {

    private static final int MAX_STRING_LENGTH = 2_048;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT_PATTERN = Pattern
        .compile("\\b[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern SENSITIVE_QUERY_PATTERN = Pattern
        .compile("(?i)([?&][^=&#\\s]*(?:token|password|secret|key|code)[^=&#\\s]*=)[^&#\\s]+");

    public Object sanitize(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof String stringValue) {
            return sanitizeString(stringValue);
        }

        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                sanitized.put(String.valueOf(entry.getKey()), sanitize(entry.getValue()));
            }

            return sanitized;
        }

        if (value instanceof List<?> listValue) {
            return listValue.stream().map(this::sanitize).toList();
        }

        return sanitizeString(String.valueOf(value));
    }

    public String sanitizeString(String value) {
        String sanitized = EMAIL_PATTERN.matcher(value).replaceAll("[REDACTED_EMAIL]");
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED_TOKEN]");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
        sanitized = SENSITIVE_QUERY_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        if (sanitized.length() <= MAX_STRING_LENGTH) {
            return sanitized;
        }

        return sanitized.substring(0, MAX_STRING_LENGTH) + "...[TRUNCATED]";
    }
}
