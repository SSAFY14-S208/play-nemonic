package com.nemonicworld.backoffice.metrics.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MetricsSampleResponse(String ts, Double value) {
}
