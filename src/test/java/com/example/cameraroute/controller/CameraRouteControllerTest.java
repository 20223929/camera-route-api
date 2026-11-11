package com.example.cameraroute.controller;

import com.example.cameraroute.dto.ApiResponse;
import com.example.cameraroute.dto.CameraPage;
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
    void returnsPaginationMetadataAlongsideCameraData() {
        RouteCameraRequest request = new RouteCameraRequest();
        CameraPage page = new CameraPage(1280L, Collections.<CameraResponse>emptyList());
        when(service.findCameras(request, 2, 500)).thenReturn(page);

        ApiResponse<List<CameraResponse>> response =
                controller.getRouteCameras(request, 2, 500);

        assertEquals(200, response.getCode());
        assertEquals(1280L, response.getTotal());
        assertEquals(2, response.getPageNum());
        assertEquals(500, response.getPageSize());
        assertEquals(3L, response.getTotalPages());
        assertSame(page.getData(), response.getData());
        verify(service).findCameras(request, 2, 500);
    }

    @Test
    void reportsZeroTotalPagesWhenThereAreNoResults() {
        ApiResponse<List<CameraResponse>> response = ApiResponse.success(
                Collections.<CameraResponse>emptyList(), 0L, 1, 500
        );

        assertEquals(0L, response.getTotal());
        assertEquals(1, response.getPageNum());
        assertEquals(500, response.getPageSize());
        assertEquals(0L, response.getTotalPages());
    }
}
