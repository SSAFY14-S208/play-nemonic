package com.nemonicworld.common.exception;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.global.logging.StructuredEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "서버 오류가 발생했습니다.";
    private static final String X_TRACE_ID = "X-Trace-Id";
    private static final String X_REQUEST_ID = "X-Request-Id";

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException e, HttpServletRequest request) {
        logApiFailure("api_validation_failed", "api validation failed", HttpStatus.BAD_REQUEST, request, e);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
        logApiFailure("api_unauthorized", "api unauthorized", HttpStatus.UNAUTHORIZED, request, e);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e, HttpServletRequest request) {
        logApiFailure("api_forbidden", "api forbidden", HttpStatus.FORBIDDEN, request, e);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ApiResponse<Void>> handleGone(GoneException e) {
        return ResponseEntity.status(HttpStatus.GONE).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayloadTooLarge(PayloadTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException e,
        HttpServletRequest request) {
        logApiFailure("api_rate_limited", "api rate limited", HttpStatus.TOO_MANY_REQUESTS, request, e);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException e,
        HttpServletRequest request) {
        logApiFailure("api_request_failed", "api request failed", HttpStatus.SERVICE_UNAVAILABLE, request, e);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorage(FileStorageException e, HttpServletRequest request) {
        logApiFailure("api_request_failed", "api request failed", HttpStatus.INTERNAL_SERVER_ERROR, request, e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailDelivery(EmailDeliveryException e, HttpServletRequest request) {
        logApiFailure("api_request_failed", "api request failed", HttpStatus.SERVICE_UNAVAILABLE, request, e);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail(e.getMessage(), null));
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalServer(InternalServerException e,
        HttpServletRequest request) {
        logApiFailure("api_request_failed", "api request failed", HttpStatus.INTERNAL_SERVER_ERROR, request, e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(INTERNAL_SERVER_ERROR_MESSAGE, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e,
        HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        logApiFailure("api_validation_failed", "api validation failed", HttpStatus.BAD_REQUEST, request, e,
            StructuredEventLogger.metadata("field_error_count", errors.size()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("유효성 검사 실패", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
        HttpServletRequest request) {
        logApiFailure("api_validation_failed", "api validation failed", HttpStatus.BAD_REQUEST, request, e);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("요청 본문 형식이 올바르지 않습니다.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        logApiFailure("api_request_failed", "api request failed", HttpStatus.INTERNAL_SERVER_ERROR, request, e);
        logCommunityInfrastructureFailure(e, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(INTERNAL_SERVER_ERROR_MESSAGE, null));
    }

    private void logCommunityInfrastructureFailure(Exception e, HttpServletRequest request) {
        if (!containsDataAccessException(e) || request == null) {
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/community/")) {
            StructuredEventLogger.apiWarn("community_admin_query_failed", "community admin query failed",
                resolveTraceId(request), StructuredEventLogger.metadata("path", path, "method", request.getMethod()),
                e);
            return;
        }

        if (path.startsWith("/api/v1/community/")) {
            StructuredEventLogger.apiWarn("community_repository_query_failed", "community repository query failed",
                resolveTraceId(request), StructuredEventLogger.metadata("path", path, "method", request.getMethod()),
                e);
        }
    }

    private void logApiFailure(String eventName, String message, HttpStatus status, HttpServletRequest request,
        Throwable error) {
        logApiFailure(eventName, message, status, request, error, Map.of());
    }

    private void logApiFailure(String eventName, String message, HttpStatus status, HttpServletRequest request,
        Throwable error, Map<String, Object> extraMetadata) {
        if (request == null || !request.getRequestURI().startsWith("/api/")) {
            return;
        }

        Map<String, Object> metadata = StructuredEventLogger.metadata("path", request.getRequestURI(), "method",
            request.getMethod(), "status", status.value(), "result", "failed", "reason_code",
            error.getClass().getSimpleName());
        metadata.putAll(extraMetadata == null ? Map.of() : extraMetadata);
        StructuredEventLogger.apiWarn(eventName, message, resolveTraceId(request), metadata, error);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(X_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(X_REQUEST_ID);
        }

        return traceId;
    }

    private boolean containsDataAccessException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DataAccessException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }
}
