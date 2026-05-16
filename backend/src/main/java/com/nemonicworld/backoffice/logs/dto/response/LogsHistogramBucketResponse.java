package com.nemonicworld.backoffice.logs.dto.response;

import java.util.Map;

public record LogsHistogramBucketResponse(String ts, long total, Map<String, Long> byLevel) {
}
