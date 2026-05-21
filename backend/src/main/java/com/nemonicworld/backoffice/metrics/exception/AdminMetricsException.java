package com.nemonicworld.backoffice.metrics.exception;

import org.springframework.http.HttpStatus;

public class AdminMetricsException extends RuntimeException {

    public static final String UNKNOWN_TEMPLATE = "ADMIN_METRICS_UNKNOWN_TEMPLATE";
    public static final String INVALID_PARAM = "ADMIN_METRICS_INVALID_PARAM";
    public static final String INVALID_TIME_RANGE = "ADMIN_METRICS_INVALID_TIME_RANGE";
    public static final String INVALID_QUERY = "ADMIN_METRICS_INVALID_QUERY";
    public static final String UNAUTHORIZED = "ADMIN_METRICS_UNAUTHORIZED";
    public static final String FORBIDDEN = "ADMIN_METRICS_FORBIDDEN";
    public static final String PROMETHEUS_TIMEOUT = "PROMETHEUS_TIMEOUT";
    public static final String PROMETHEUS_UPSTREAM_ERROR = "PROMETHEUS_UPSTREAM_ERROR";

    private final String code;
    private final HttpStatus status;

    private AdminMetricsException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    private AdminMetricsException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public static AdminMetricsException unknownTemplate() {
        return new AdminMetricsException(UNKNOWN_TEMPLATE, HttpStatus.BAD_REQUEST, "허용되지 않은 메트릭 템플릿입니다.");
    }

    public static AdminMetricsException invalidParam() {
        return new AdminMetricsException(INVALID_PARAM, HttpStatus.BAD_REQUEST, "유효하지 않은 메트릭 파라미터입니다.");
    }

    public static AdminMetricsException invalidTimeRange() {
        return new AdminMetricsException(INVALID_TIME_RANGE, HttpStatus.BAD_REQUEST, "유효하지 않은 메트릭 시간 범위입니다.");
    }

    public static AdminMetricsException invalidQuery() {
        return new AdminMetricsException(INVALID_QUERY, HttpStatus.BAD_REQUEST, "유효하지 않은 메트릭 요청입니다.");
    }

    public static AdminMetricsException unauthorized() {
        return new AdminMetricsException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "관리자 인증이 필요합니다.");
    }

    public static AdminMetricsException forbidden() {
        return new AdminMetricsException(FORBIDDEN, HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
    }

    public static AdminMetricsException timeout(Throwable cause) {
        return new AdminMetricsException(PROMETHEUS_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "메트릭 업스트림 응답 시간이 초과되었습니다.",
            cause);
    }

    public static AdminMetricsException upstream(Throwable cause) {
        return new AdminMetricsException(PROMETHEUS_UPSTREAM_ERROR, HttpStatus.BAD_GATEWAY, "메트릭 업스트림에 연결할 수 없습니다.",
            cause);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
