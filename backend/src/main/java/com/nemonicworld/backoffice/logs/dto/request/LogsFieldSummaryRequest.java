package com.nemonicworld.backoffice.logs.dto.request;

import java.util.List;

public record LogsFieldSummaryRequest(String index, String query, List<LogsFilter> filters, LogsTimeRange timeRange,
    List<String> fields) {
}
