package com.nemonicworld.backoffice.logs.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogsHistogramBucketResponse(String ts, long total, Map<String, Long> byLevel, Map<String, Long> byField) {

    public LogsHistogramBucketResponse(String ts, long total, Map<String, Long> byLevel) {
        this(ts, total, byLevel, null);
    }
}
