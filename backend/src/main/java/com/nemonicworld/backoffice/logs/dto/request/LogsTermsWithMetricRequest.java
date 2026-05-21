package com.nemonicworld.backoffice.logs.dto.request;

import java.util.List;

public record LogsTermsWithMetricRequest(String index, String query, List<LogsFilter> filters, LogsTimeRange timeRange,
    String groupBy, Integer size, List<LogsMetricSpec> metrics) {
}
