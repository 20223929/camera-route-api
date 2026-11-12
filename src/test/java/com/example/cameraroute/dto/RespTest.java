package com.example.cameraroute.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RespTest {

    @Test
    void createsSuccessfulResponseWithData() {
        Object data = new Object();

        Resp<Object> response = Resp.success(data);

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMsg());
        assertSame(data, response.getData());
    }

    @Test
    void createsFailureResponseWithoutData() {
        Resp<Void> response = Resp.failure(400, "参数错误");

        assertEquals(400, response.getCode());
        assertEquals("参数错误", response.getMsg());
        assertNull(response.getData());
    }
}
