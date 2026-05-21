package com.nemonicworld.backoffice.logs.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogsFilteredMetricsGroupResponse(long count, Double metric) {
}
