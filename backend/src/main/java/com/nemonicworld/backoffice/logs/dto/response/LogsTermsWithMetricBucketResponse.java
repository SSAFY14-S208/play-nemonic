package com.nemonicworld.backoffice.logs.dto.response;

import java.util.Map;

public record LogsTermsWithMetricBucketResponse(String value, long total, Map<String, Double> metrics) {
}
