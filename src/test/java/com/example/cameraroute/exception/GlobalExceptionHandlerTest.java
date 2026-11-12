package com.example.cameraroute.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.core.MethodParameter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void mapsRequestParameterTypeMismatchToBadRequest() throws Exception {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("endpoint", int.class), 0
        );
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException(
                        "not-a-number", Integer.class, "pageNum", parameter,
                        new NumberFormatException("not a number")
                );

        ResponseEntity<Map<String, String>> response =
                new GlobalExceptionHandler().handleBadRequest(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("请求参数 pageNum 格式不正确", response.getBody().get("message"));
    }

    @SuppressWarnings("unused")
    private void endpoint(@RequestParam int pageNum) {
    }
}
