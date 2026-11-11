package com.example.cameraroute.controller;

import com.example.cameraroute.dto.ApiResponse;
import com.example.cameraroute.dto.CameraPage;
import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.service.CameraRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/camera")
@Validated
@RequiredArgsConstructor
public class CameraRouteController {

    private final CameraRouteService service;

    @PostMapping("/route/list")
    public ApiResponse<List<CameraResponse>> getRouteCameras(
            @Valid @RequestBody RouteCameraRequest request,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "500") int pageSize
    ) {
        CameraPage page = service.findCameras(request, pageNum, pageSize);
        return ApiResponse.success(page.getData(), page.getTotal(), pageNum, pageSize);
    }
}
