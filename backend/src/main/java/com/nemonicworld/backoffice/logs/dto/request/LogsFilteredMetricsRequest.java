package com.nemonicworld.backoffice.logs.dto.request;

import java.util.List;

public record LogsFilteredMetricsRequest(String index, String query, List<LogsFilter> filters, LogsTimeRange timeRange,
    List<LogsFilteredMetricGroup> groups) {
}
