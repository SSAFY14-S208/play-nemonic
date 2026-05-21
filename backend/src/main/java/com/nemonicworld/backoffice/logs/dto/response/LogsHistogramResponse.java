package com.nemonicworld.backoffice.logs.dto.response;

import java.util.List;

public record LogsHistogramResponse(String interval, List<LogsHistogramBucketResponse> buckets) {
}
