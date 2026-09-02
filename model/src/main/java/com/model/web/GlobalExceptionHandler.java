package com.model.web;

import com.model.bean.Result;
import com.model.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 全站统一参数校验/兜底异常处理，返回统一的 Result 结构。
 * 由各服务启动类显式 @Import(GlobalExceptionHandler.class) 注册
 * （服务默认组件扫描范围不含 com.model，故不依赖包扫描）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //@RequestBody + @Validated 校验失败（如 update 的 DTO）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(sr -> sr.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(msg);
    }

    //@RequestParam 上的 @Pattern 等校验失败（如 register/login 的用户名格式）
    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(msg);
    }

    //Spring 6.1+ 对方法参数（@RequestParam 等）的校验失败
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result handleMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(sr -> sr.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(msg);
    }

    //业务规则异常：message 即友好提示，直接返回给前端
    @ExceptionHandler(BusinessException.class)
    public Result handleBusiness(BusinessException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e){
        //只记录完整堆栈到日志，向前端返回通用提示，避免泄露SQL等内部信息
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }
}
