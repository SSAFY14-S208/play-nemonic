package com.nemonicworld.backoffice.logs.dto.request;

import java.util.List;

public record LogsDistinctCountRequest(String index, String query, List<LogsFilter> filters, LogsTimeRange timeRange,
    String field, Integer precisionThreshold) {
}
