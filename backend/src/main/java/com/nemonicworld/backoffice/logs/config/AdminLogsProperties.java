package com.nemonicworld.backoffice.logs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "nemonic.admin-logs")
public record AdminLogsProperties(String opensearchUrl, Integer maxSize, Long queryTimeoutMs) {

    private static final String DEFAULT_OPENSEARCH_URL = "http://opensearch:9200";
    private static final int DEFAULT_MAX_SIZE = 200;
    private static final long DEFAULT_QUERY_TIMEOUT_MS = 15_000L;

    public String resolvedOpenSearchUrl() {
        return StringUtils.hasText(opensearchUrl) ? opensearchUrl : DEFAULT_OPENSEARCH_URL;
    }

    public int resolvedMaxSize() {
        return maxSize == null ? DEFAULT_MAX_SIZE : maxSize;
    }

    public long resolvedQueryTimeoutMs() {
        return queryTimeoutMs == null ? DEFAULT_QUERY_TIMEOUT_MS : queryTimeoutMs;
    }
}
