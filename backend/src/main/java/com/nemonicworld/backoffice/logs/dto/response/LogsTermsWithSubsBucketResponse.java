package com.nemonicworld.backoffice.logs.dto.response;

import java.util.Map;

public record LogsTermsWithSubsBucketResponse(String value, long total, Map<String, Long> sub) {
}
