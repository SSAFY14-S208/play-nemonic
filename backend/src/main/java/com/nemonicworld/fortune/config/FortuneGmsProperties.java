package com.nemonicworld.fortune.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "nemonic.fortune.gms")
public record FortuneGmsProperties(String apiKey, String model, String baseUrl, Long connectTimeoutMs,
    Long readTimeoutMs) {

    private static final String DEFAULT_MODEL = "gpt-5.2";
    private static final String DEFAULT_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions";
    private static final long DEFAULT_CONNECT_TIMEOUT_MS = 3000L;
    private static final long DEFAULT_READ_TIMEOUT_MS = 30000L;

    public String resolvedModel() {
        return StringUtils.hasText(model) ? model : DEFAULT_MODEL;
    }

    public String resolvedBaseUrl() {
        return StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
    }

    public long resolvedConnectTimeoutMs() {
        return connectTimeoutMs == null ? DEFAULT_CONNECT_TIMEOUT_MS : connectTimeoutMs;
    }

    public long resolvedReadTimeoutMs() {
        return readTimeoutMs == null ? DEFAULT_READ_TIMEOUT_MS : readTimeoutMs;
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
