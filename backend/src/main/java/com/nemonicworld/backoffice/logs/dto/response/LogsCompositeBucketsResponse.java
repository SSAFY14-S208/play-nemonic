package com.nemonicworld.backoffice.logs.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogsCompositeBucketsResponse(List<LogsCompositeBucketResponse> buckets, Map<String, Object> afterKey) {
}
