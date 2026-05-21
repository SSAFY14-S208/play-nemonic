package com.nemonicworld.backoffice.logs.exception;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public record AdminLogsErrorResponse(String code, String message, String timestamp) {

    public static AdminLogsErrorResponse of(String code, String message) {
        return new AdminLogsErrorResponse(code, message,
            OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
