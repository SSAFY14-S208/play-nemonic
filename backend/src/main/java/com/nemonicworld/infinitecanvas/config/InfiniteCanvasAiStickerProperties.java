package com.nemonicworld.infinitecanvas.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "nemonic.infinite-canvas.ai-sticker")
public record InfiniteCanvasAiStickerProperties(Boolean enabled, Integer maxPromptLength, Integer defaultImageSize,
    Integer maxImageSize, Set<String> allowedStyles, Gms gms) {

    private static final int DEFAULT_MAX_PROMPT_LENGTH = 200;
    private static final int DEFAULT_IMAGE_SIZE = 512;
    private static final int DEFAULT_MAX_IMAGE_SIZE = 1024;
    private static final Set<String> DEFAULT_ALLOWED_STYLES = Set.of("sticker", "cartoon", "pixel", "clay_3d",
        "watercolor", "flat_icon", "hand_drawn");

    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }

    public int resolvedMaxPromptLength() {
        return maxPromptLength == null ? DEFAULT_MAX_PROMPT_LENGTH : maxPromptLength;
    }

    public int resolvedDefaultImageSize() {
        return defaultImageSize == null ? DEFAULT_IMAGE_SIZE : defaultImageSize;
    }

    public int resolvedMaxImageSize() {
        return maxImageSize == null ? DEFAULT_MAX_IMAGE_SIZE : maxImageSize;
    }

    public Set<String> resolvedAllowedStyles() {
        return allowedStyles == null || allowedStyles.isEmpty() ? DEFAULT_ALLOWED_STYLES : allowedStyles;
    }

    public Gms resolvedGms() {
        return gms == null ? new Gms(null, null, null, null, null) : gms;
    }

    public record Gms(String apiKey, String model, String baseUrl, Long connectTimeoutMs, Long readTimeoutMs) {

        private static final String DEFAULT_MODEL = "gpt-image-1";
        private static final String DEFAULT_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/images/generations";
        private static final long DEFAULT_CONNECT_TIMEOUT_MS = 3000L;
        private static final long DEFAULT_READ_TIMEOUT_MS = 60000L;

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
}
