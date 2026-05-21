package com.nemonicworld.infinitecanvas.exception;

import com.nemonicworld.common.response.ApiResponse;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasRevisionConflictResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InfiniteCanvasExceptionHandler {

    @ExceptionHandler(InfiniteCanvasRevisionConflictException.class)
    public ResponseEntity<ApiResponse<InfiniteCanvasRevisionConflictResponse>> handleRevisionConflict(
        InfiniteCanvasRevisionConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiResponse<>(false, e.getMessage(), e.response(), null));
    }
}
