package com.example.cameraroute.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        String message = ex instanceof IllegalArgumentException
                ? ex.getMessage() : "请求参数格式或内容不正确";
        log.warn("event=request.validation.failed exceptionType={} message={}",
                ex.getClass().getSimpleName(), message);
        return ResponseEntity.badRequest().body(error(message));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseFailure(DataAccessException ex) {
        log.error("event=request.database.failed exceptionType={}",
                ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                error("数据库暂时不可用，请稍后重试")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        log.error("event=request.unhandled_exception exceptionType={}",
                ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                error("服务器内部错误，请检查服务日志")
        );
    }

    private Map<String, String> error(String message) {
        return Collections.singletonMap("message", message);
    }
}
