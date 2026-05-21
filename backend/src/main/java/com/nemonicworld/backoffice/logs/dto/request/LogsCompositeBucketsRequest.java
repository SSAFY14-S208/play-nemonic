package com.nemonicworld.backoffice.logs.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record LogsCompositeBucketsRequest(String index, String query, List<LogsFilter> filters, LogsTimeRange timeRange,
    List<String> sources, Integer size, JsonNode after, List<LogsMetricSpec> subAggs) {
}
