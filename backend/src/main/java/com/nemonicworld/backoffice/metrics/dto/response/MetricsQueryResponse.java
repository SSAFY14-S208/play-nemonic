package com.nemonicworld.backoffice.metrics.dto.response;

import java.util.List;

public record MetricsQueryResponse(String resultType, List<MetricsSeriesResponse> series) {
}
