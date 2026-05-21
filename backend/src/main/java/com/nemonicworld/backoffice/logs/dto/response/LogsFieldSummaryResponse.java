package com.nemonicworld.backoffice.logs.dto.response;

import java.util.List;
import java.util.Map;

public record LogsFieldSummaryResponse(Map<String, List<LogsFieldSummaryItemResponse>> fields) {
}
