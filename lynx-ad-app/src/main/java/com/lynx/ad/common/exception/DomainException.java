package com.lynx.ad.common.exception;

public class DomainException extends RuntimeException {

    private final int code;

    public DomainException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
