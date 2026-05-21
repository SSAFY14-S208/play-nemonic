package com.nemonicworld.backoffice.metrics.dto.response;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public record MetricsErrorResponse(boolean success, String code, String message, String timestamp) {

    public static MetricsErrorResponse of(String code, String message) {
        return new MetricsErrorResponse(false, code, message,
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
