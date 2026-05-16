package com.nemonicworld.backoffice.logs.dto.request;

import java.util.List;

public record LogsSearchRequest(String index, String query, List<LogsFilter> filters, LogsTimeRange timeRange,
    Integer size, List<Object> searchAfter) {
}
