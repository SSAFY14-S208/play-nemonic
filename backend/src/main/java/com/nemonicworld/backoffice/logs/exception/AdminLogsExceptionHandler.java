package com.nemonicworld.backoffice.logs.exception;

import com.nemonicworld.backoffice.logs.controller.AdminLogsController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminLogsController.class)
public class AdminLogsExceptionHandler {

    @ExceptionHandler(AdminLogsException.class)
    public ResponseEntity<AdminLogsErrorResponse> handleAdminLogs(AdminLogsException e) {
        return ResponseEntity.status(e.status()).body(AdminLogsErrorResponse.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AdminLogsErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        AdminLogsException exception = AdminLogsException.invalidQuery();

        return ResponseEntity.status(exception.status())
            .body(AdminLogsErrorResponse.of(exception.code(), exception.getMessage()));
    }
}
