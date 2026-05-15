package com.nemonicworld.backoffice.logs.exception;

import org.springframework.http.HttpStatus;

public class AdminLogsException extends RuntimeException {

    public static final String INVALID_INDEX = "ADMIN_LOGS_INVALID_INDEX";
    public static final String INVALID_QUERY = "ADMIN_LOGS_INVALID_QUERY";
    public static final String INVALID_TIME_RANGE = "ADMIN_LOGS_INVALID_TIME_RANGE";
    public static final String INVALID_FIELD = "ADMIN_LOGS_INVALID_FIELD";
    public static final String UNAUTHORIZED = "ADMIN_LOGS_UNAUTHORIZED";
    public static final String FORBIDDEN = "ADMIN_LOGS_FORBIDDEN";
    public static final String OPENSEARCH_TIMEOUT = "OPENSEARCH_TIMEOUT";
    public static final String OPENSEARCH_UPSTREAM_ERROR = "OPENSEARCH_UPSTREAM_ERROR";

    private final String code;
    private final HttpStatus status;

    private AdminLogsException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    private AdminLogsException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public static AdminLogsException invalidIndex() {
        return new AdminLogsException(INVALID_INDEX, HttpStatus.BAD_REQUEST, "Invalid logs index.");
    }

    public static AdminLogsException invalidQuery() {
        return new AdminLogsException(INVALID_QUERY, HttpStatus.BAD_REQUEST, "Invalid logs query.");
    }

    public static AdminLogsException invalidTimeRange() {
        return new AdminLogsException(INVALID_TIME_RANGE, HttpStatus.BAD_REQUEST, "Invalid logs time range.");
    }

    public static AdminLogsException invalidField() {
        return new AdminLogsException(INVALID_FIELD, HttpStatus.BAD_REQUEST, "Invalid logs field.");
    }

    public static AdminLogsException unauthorized() {
        return new AdminLogsException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Admin authentication is required.");
    }

    public static AdminLogsException forbidden() {
        return new AdminLogsException(FORBIDDEN, HttpStatus.FORBIDDEN, "Admin permission is required.");
    }

    public static AdminLogsException timeout(Throwable cause) {
        return new AdminLogsException(OPENSEARCH_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "Log search upstream timed out.",
            cause);
    }

    public static AdminLogsException upstream(Throwable cause) {
        return new AdminLogsException(OPENSEARCH_UPSTREAM_ERROR, HttpStatus.BAD_GATEWAY,
            "Log search upstream is unavailable.", cause);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
