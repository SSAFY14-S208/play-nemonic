package com.nemonicworld.backoffice.metrics.dto.response;

import java.util.List;

public record MetricsQueryRangeResponse(String resultType, String interval, List<MetricsSeriesResponse> series) {
}
