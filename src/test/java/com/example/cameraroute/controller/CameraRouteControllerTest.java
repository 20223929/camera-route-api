package com.example.cameraroute.controller;

import com.example.cameraroute.dto.PageResult;
import com.example.cameraroute.dto.Resp;
import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.service.CameraRouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CameraRouteControllerTest {
    private CameraRouteService service;
    private CameraRouteController controller;

    @BeforeEach
    void setUp() {
        service = mock(CameraRouteService.class);
        controller = new CameraRouteController(service);
    }

    @Test
    void returnsRespContainingPageResult() {
        RouteCameraRequest request = new RouteCameraRequest();
        List<CameraResponse> data = Collections.nCopies(280, mock(CameraResponse.class));
        PageResult<CameraResponse> page = PageResult.of(1280L, 3, 500, data);
        when(service.findCameras(request, 3, 500)).thenReturn(page);

        Resp<PageResult<CameraResponse>> response =
                controller.getRouteCameras(request, 3, 500);

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMsg());
        assertSame(page, response.getData());
        assertEquals(1280L, response.getData().getTotal());
        assertEquals(3, response.getData().getPageNum());
        assertEquals(500, response.getData().getPageSize());
        assertEquals(280, response.getData().getCurrentCount());
        assertEquals(3L, response.getData().getTotalPages());
        verify(service).findCameras(request, 3, 500);
    }

    @Test
    void calculatesFullPageCount() {
        List<CameraResponse> data = Collections.nCopies(500, mock(CameraResponse.class));
        PageResult<CameraResponse> response = PageResult.of(1280L, 1, 500, data);
        assertEquals(500, response.getCurrentCount());
        assertEquals(500, response.getPageSize());
        assertEquals(3L, response.getTotalPages());
    }

    @Test
    void reportsZeroCurrentCountAndTotalPagesWhenThereAreNoResults() {
        PageResult<CameraResponse> response = PageResult.empty(1, 500);
        assertEquals(0L, response.getTotal());
        assertEquals(1, response.getPageNum());
        assertEquals(500, response.getPageSize());
        assertEquals(0, response.getCurrentCount());
        assertEquals(0L, response.getTotalPages());
    }
}
