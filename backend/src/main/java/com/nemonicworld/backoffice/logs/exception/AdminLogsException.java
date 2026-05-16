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
        return new AdminLogsException(INVALID_INDEX, HttpStatus.BAD_REQUEST, "유효하지 않은 로그 인덱스입니다.");
    }

    public static AdminLogsException invalidQuery() {
        return new AdminLogsException(INVALID_QUERY, HttpStatus.BAD_REQUEST, "유효하지 않은 로그 질의입니다.");
    }

    public static AdminLogsException invalidTimeRange() {
        return new AdminLogsException(INVALID_TIME_RANGE, HttpStatus.BAD_REQUEST, "유효하지 않은 로그 시간 범위입니다.");
    }

    public static AdminLogsException invalidField() {
        return new AdminLogsException(INVALID_FIELD, HttpStatus.BAD_REQUEST, "유효하지 않은 로그 필드입니다.");
    }

    public static AdminLogsException unauthorized() {
        return new AdminLogsException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "관리자 인증이 필요합니다.");
    }

    public static AdminLogsException forbidden() {
        return new AdminLogsException(FORBIDDEN, HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }

    public static AdminLogsException timeout(Throwable cause) {
        return new AdminLogsException(OPENSEARCH_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "로그 검색 업스트림 응답 시간이 초과되었습니다.",
            cause);
    }

    public static AdminLogsException upstream(Throwable cause) {
        return new AdminLogsException(OPENSEARCH_UPSTREAM_ERROR, HttpStatus.BAD_GATEWAY, "로그 검색 업스트림에 연결할 수 없습니다.",
            cause);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
