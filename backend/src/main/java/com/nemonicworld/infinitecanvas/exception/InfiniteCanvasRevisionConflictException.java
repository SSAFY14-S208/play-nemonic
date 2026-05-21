package com.nemonicworld.infinitecanvas.exception;

import com.nemonicworld.common.exception.ConflictException;
import com.nemonicworld.infinitecanvas.dto.response.InfiniteCanvasRevisionConflictResponse;

public class InfiniteCanvasRevisionConflictException extends ConflictException {

    private final InfiniteCanvasRevisionConflictResponse response;

    public InfiniteCanvasRevisionConflictException(String message, InfiniteCanvasRevisionConflictResponse response) {
        super(message);
        this.response = response;
    }

    public InfiniteCanvasRevisionConflictResponse response() {
        return response;
    }
}
