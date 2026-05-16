package com.nemonicworld.backoffice.metrics.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricsSeriesResponse(Map<String, String> labels, Double value, List<MetricsSampleResponse> samples) {
}
