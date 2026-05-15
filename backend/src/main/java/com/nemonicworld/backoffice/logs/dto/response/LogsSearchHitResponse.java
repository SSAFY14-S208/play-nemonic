package com.nemonicworld.backoffice.logs.dto.response;

import java.util.Map;

public record LogsSearchHitResponse(String id, String index, Map<String, Object> source) {
}
