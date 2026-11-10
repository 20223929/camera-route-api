package com.example.cameraroute.controller;

import com.example.cameraroute.dto.ApiResponse;
import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.service.CameraRouteService;
import com.example.cameraroute.task.CameraRouteTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/camera")
@Validated
@RequiredArgsConstructor
public class CameraRouteController {

    private final CameraRouteService service;
    private final CameraRouteTaskService taskService;

    @Value("${camera.search.async-threshold-meters:500000}")
    private double asyncThresholdMeters;

    @PostMapping("/route/list")
    public ApiResponse<Object> getRouteCameras(
            @Valid @RequestBody RouteCameraRequest request
    ) {
        double routeLength = service.getRouteLengthMeters(request);

        if (routeLength > asyncThresholdMeters) {
            CameraRouteTaskService.TaskSubmission submission = taskService.submit(request);
            if (submission.isNewOrRetry()) {
                taskService.execute(submission.getTaskId(), request);
            }

            Map<String, Object> accepted = new LinkedHashMap<String, Object>();
            accepted.put("async", true);
            accepted.put("taskId", submission.getTaskId());
            accepted.put("status", submission.getStatus());
            accepted.put("reused", !submission.isNewOrRetry());
            accepted.put("routeLengthMeters", Math.round(routeLength * 100.0) / 100.0);
            accepted.put("statusUrl", "/api/camera/route/tasks/" + submission.getTaskId());
            accepted.put("resultUrl", "/api/camera/route/tasks/" + submission.getTaskId() + "/result");

            return ApiResponse.success(accepted, 0);
        }

        List<CameraResponse> cameras = service.findCameras(request);
        return ApiResponse.success((Object) cameras, cameras.size());
    }

    @GetMapping("/route/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getTaskStatus(
            @PathVariable String taskId
    ) {
        Map<String, Object> status = taskService.getStatus(taskId);
        return ApiResponse.success(status, 1);
    }

    @GetMapping("/route/tasks/{taskId}/result")
    public ApiResponse<List<CameraResponse>> getTaskResult(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "500") int pageSize
    ) {
        CameraRouteTaskService.PageResult page =
                taskService.getResult(taskId, pageNum, pageSize);
        return ApiResponse.success(page.getData(), page.getTotal());
    }
}
