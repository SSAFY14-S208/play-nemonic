package com.nemonicworld.backoffice.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "nemonic.admin.metrics")
public record AdminMetricsProperties(String prometheusBaseUrl, Long timeoutMs, Long cacheTtlSeconds) {

    private static final String DEFAULT_PROMETHEUS_BASE_URL = "http://prometheus:9090";
    private static final long DEFAULT_TIMEOUT_MS = 10_000L;
    private static final long DEFAULT_CACHE_TTL_SECONDS = 15L;

    public String resolvedPrometheusBaseUrl() {
        return StringUtils.hasText(prometheusBaseUrl) ? prometheusBaseUrl : DEFAULT_PROMETHEUS_BASE_URL;
    }

    public long resolvedTimeoutMs() {
        return timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }

    public long resolvedCacheTtlSeconds() {
        return cacheTtlSeconds == null ? DEFAULT_CACHE_TTL_SECONDS : cacheTtlSeconds;
    }
}
