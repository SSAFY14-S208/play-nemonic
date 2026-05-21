package com.nemonicworld.fortune.service.gms;

public class FortuneGmsException extends RuntimeException {

    public FortuneGmsException(String message) {
        super(message);
    }

    public FortuneGmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
