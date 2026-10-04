package com.lynx.ad.common.api;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.exception.DomainException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ApiResponse<Void> domain(DomainException ex) {
        return ApiResponse.fail(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> biz(BizException ex) {
        return ApiResponse.fail(ex.code(), ex.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    public ApiResponse<Void> invalid(Exception ex) {
        return ApiResponse.fail(1001, firstMessage(ex));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> unknown(Exception ex) {
        log.error("unhandled", ex);
        return ApiResponse.fail(9999, "系统繁忙");
    }

    private static String firstMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manve && manve.getBindingResult().getFieldError() != null) {
            return manve.getBindingResult().getFieldError().getDefaultMessage();
        }
        if (ex instanceof BindException be && be.getBindingResult().getFieldError() != null) {
            return be.getBindingResult().getFieldError().getDefaultMessage();
        }
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? "参数不合法" : msg;
    }
}
