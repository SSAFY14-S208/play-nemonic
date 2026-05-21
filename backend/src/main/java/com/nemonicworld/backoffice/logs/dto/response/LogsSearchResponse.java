package com.nemonicworld.backoffice.logs.dto.response;

import java.util.List;

public record LogsSearchResponse(long total, long tookMs, List<LogsSearchHitResponse> hits,
    List<Object> nextSearchAfter) {
}
