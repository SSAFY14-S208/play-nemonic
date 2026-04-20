package com.nemonicworld.common.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super("접근 권한이 없습니다.");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}
