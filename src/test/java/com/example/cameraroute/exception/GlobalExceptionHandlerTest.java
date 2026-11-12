package com.example.cameraroute.exception;

import com.example.cameraroute.dto.Resp;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        ResponseEntity<Resp<Void>> response =
                new GlobalExceptionHandler().handleBadRequest(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertEquals("请求参数 pageNum 格式不正确", response.getBody().getMsg());
        assertNull(response.getBody().getData());
    }

    @SuppressWarnings("unused")
    private void endpoint(@RequestParam int pageNum) {
    }
}
