package com.ace.im.exception;

import com.ace.im.common.BaseResponse;
import com.ace.im.common.ErrorCode;
import com.ace.im.common.ResultUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
//这个类的异常处理方法作用于 Controller，并把返回值写入 HTTP 响应体
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理请求体等对象参数通过 @Valid / @Validated 校验失败时抛出的异常。
     * register(@Valid @RequestBody RegisterRequest request)，对象里的 phone 不符合规则
     * 取第一条校验错误信息，返回统一的参数错误响应。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    /**
     * 处理 Jakarta Bean Validation 抛出的约束违反异常。
     * 取第一条违反约束的信息，返回统一的参数错误响应。
     * @Validated 的 Service 方法接到空的 @NotBlank String phone
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public BaseResponse<?> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).findFirst().orElse("请求参数校验失败");
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }
}
