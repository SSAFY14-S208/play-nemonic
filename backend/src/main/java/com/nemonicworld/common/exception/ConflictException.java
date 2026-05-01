package com.nemonicworld.common.exception;

public class ConflictException extends RuntimeException {
    public ConflictException() {
        super("이미 처리된 요청입니다.");
    }

    public ConflictException(String message) {
        super(message);
    }
}
