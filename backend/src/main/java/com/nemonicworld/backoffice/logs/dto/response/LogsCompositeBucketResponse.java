package com.nemonicworld.backoffice.logs.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogsCompositeBucketResponse(Map<String, Object> keys, long count, Map<String, Double> sub) {
}
