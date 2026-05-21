package com.nemonicworld.backoffice.metrics.dto.request;

import java.util.Map;

public record MetricsQueryRequest(String template, Map<String, String> params) {
}
