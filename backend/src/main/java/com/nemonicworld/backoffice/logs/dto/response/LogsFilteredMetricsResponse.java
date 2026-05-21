package com.nemonicworld.backoffice.logs.dto.response;

import java.util.Map;

public record LogsFilteredMetricsResponse(Map<String, LogsFilteredMetricsGroupResponse> groups) {
}
