package com.example.cameraroute.exception;

import com.example.cameraroute.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        log.warn("event=request.validation.failed exceptionType={} message={}",
                ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                new ApiResponse<Object>(
                        400,
                        ex.getMessage(),
                        0,
                        Collections.emptyList()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数错误"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();

        log.warn("event=request.validation.failed exceptionType={} message={}",
                ex.getClass().getSimpleName(), message);
        return ResponseEntity.badRequest().body(
                new ApiResponse<Object>(
                        400,
                        message,
                        0,
                        Collections.emptyList()
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        log.error("event=request.unhandled_exception exceptionType={}",
                ex.getClass().getName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiResponse<Object>(
                        500,
                        "服务器内部错误，请检查服务日志",
                        0,
                        Collections.emptyList()
                )
        );
    }
}
