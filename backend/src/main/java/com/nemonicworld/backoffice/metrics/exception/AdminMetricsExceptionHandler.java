package com.nemonicworld.backoffice.metrics.exception;

import com.nemonicworld.backoffice.metrics.controller.AdminMetricsController;
import com.nemonicworld.backoffice.metrics.dto.response.MetricsErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminMetricsController.class)
public class AdminMetricsExceptionHandler {

    @ExceptionHandler(AdminMetricsException.class)
    public ResponseEntity<MetricsErrorResponse> handleAdminMetrics(AdminMetricsException e) {
        return ResponseEntity.status(e.status()).body(MetricsErrorResponse.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MetricsErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        AdminMetricsException exception = AdminMetricsException.invalidQuery();

        return ResponseEntity.status(exception.status())
            .body(MetricsErrorResponse.of(exception.code(), exception.getMessage()));
    }
}
