package com.example.cameraroute.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
        String message = "请求参数格式或内容不正确";
        if (ex instanceof IllegalArgumentException) {
            message = ex.getMessage();
        } else if (ex instanceof BindException) {
            BindException bindException = (BindException) ex;
            if (!bindException.getFieldErrors().isEmpty()) {
                message = bindException.getFieldErrors().get(0).getDefaultMessage();
            }
        } else if (ex instanceof ConstraintViolationException) {
            ConstraintViolationException violationException =
                    (ConstraintViolationException) ex;
            if (!violationException.getConstraintViolations().isEmpty()) {
                message = violationException.getConstraintViolations()
                        .iterator().next().getMessage();
            }
        } else if (ex instanceof MissingServletRequestParameterException) {
            message = ex.getMessage();
        }
        log.warn("event=request.validation.failed exceptionType={} message={}",
                ex.getClass().getSimpleName(), message);
        return ResponseEntity.badRequest().body(error(message));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex
    ) {
        log.warn("event=request.method_not_allowed method={}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                error("不支持当前请求方法")
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex
    ) {
        log.warn("event=request.media_type_unsupported contentType={}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                error("不支持当前请求 Content-Type")
        );
    }

    @ExceptionHandler({
            TransientDataAccessException.class,
            DataAccessResourceFailureException.class
    })
    public ResponseEntity<Map<String, String>> handleTemporaryDatabaseFailure(
            DataAccessException ex
    ) {
        log.error("event=request.database.unavailable exceptionType={}",
                ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                error("数据库暂时不可用，请稍后重试")
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseFailure(DataAccessException ex) {
        log.error("event=request.database.failed exceptionType={}",
                ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                error("数据库操作失败，请检查服务日志")
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
