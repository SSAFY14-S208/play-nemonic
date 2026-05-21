package com.nemonicworld.backoffice.logs.dto.response;

import java.util.List;

public record LogsTermsWithMetricResponse(List<LogsTermsWithMetricBucketResponse> buckets) {
}
