package com.nemonicworld.clientlog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientLogSanitizerTest {

    private final ClientLogSanitizer sanitizer = new ClientLogSanitizer();

    @Test
    void sanitizerMasksEmailBearerJwtAndSensitiveQueryValues() {
        String sanitized = sanitizer.sanitizeString(
            "/callback?token=secret-token&code=1234 user=user@example.com Authorization: Bearer abc.def.ghi");

        assertThat(sanitized).contains("token=[REDACTED]", "code=[REDACTED]", "[REDACTED_EMAIL]",
            "Bearer [REDACTED_TOKEN]");
        assertThat(sanitized).doesNotContain("secret-token", "1234", "user@example.com", "abc.def.ghi");
    }

    @Test
    void sanitizerMasksNestedMapValues() {
        Object sanitized = sanitizer.sanitize(
            Map.of("path", "/pay?password=raw-password", "error", Map.of("message", "failed for user@example.com")));

        assertThat(sanitized.toString()).contains("password=[REDACTED]", "[REDACTED_EMAIL]")
            .doesNotContain("raw-password", "user@example.com");
    }
}
