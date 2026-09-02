package com.model.exception;

/**
 * 业务规则异常：携带可直接返回给前端的中文提示，
 * 由 com.model.web.GlobalExceptionHandler 统一转为 Result.error(message)。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
