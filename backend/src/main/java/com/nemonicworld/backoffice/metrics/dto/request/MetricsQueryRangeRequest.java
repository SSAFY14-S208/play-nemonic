package com.nemonicworld.backoffice.metrics.dto.request;

import java.util.Map;

public record MetricsQueryRangeRequest(String template, Map<String, String> params, MetricsTimeRange timeRange,
    String step) {
}
