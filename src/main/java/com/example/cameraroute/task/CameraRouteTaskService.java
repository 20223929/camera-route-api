package com.example.cameraroute.task;

import com.example.cameraroute.dto.CameraResponse;
import com.example.cameraroute.dto.RouteCameraRequest;
import com.example.cameraroute.service.CameraRouteService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CameraRouteTaskService {
    private final CameraRouteTaskRepository taskRepository;
    private final CameraRouteService routeService;

    public TaskSubmission submit(RouteCameraRequest request) {
        String requestHash = routeService.getRequestHash(request);
        String candidateTaskId = UUID.randomUUID().toString().replace("-", "");
        CameraRouteTaskRepository.TaskRecord task = taskRepository.acquire(candidateTaskId, requestHash);
        return new TaskSubmission(task.getTaskId(), task.getStatus(), task.isNewlyAcquired());
    }

    @Async("routeTaskExecutor")
    public void execute(String taskId, RouteCameraRequest request) {
        runTask(taskId, request);
    }

    public void runTask(String taskId, RouteCameraRequest request) {
        if (taskRepository.markRunning(taskId) == 0) {
            return;
        }
        try {
            List<CameraResponse> cameras = routeService.findCameras(request);
            taskRepository.complete(taskId, cameras);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "路线查询任务执行失败" : ex.getMessage();
            if (message.length() > 1000) message = message.substring(0, 1000);
            taskRepository.fail(taskId, message);
        }
    }

    public Map<String, Object> getStatus(String taskId) {
        Map<String, Object> status = taskRepository.find(taskId);
        status.remove("created_at");
        status.remove("updated_at");
        status.remove("request_hash");
        return status;
    }

    public PageResult getResult(String taskId, int pageNum, int pageSize) {
        if (pageNum < 1) throw new IllegalArgumentException("pageNum 必须大于等于 1");
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("pageSize 必须在 1 至 1000 之间");
        }

        Map<String, Object> status = taskRepository.find(taskId);
        if (!"COMPLETED".equals(String.valueOf(status.get("status")))) {
            throw new IllegalArgumentException("任务尚未完成，当前状态：" + status.get("status"));
        }

        long total = taskRepository.countResults(taskId);
        long offset = ((long) pageNum - 1L) * (long) pageSize;
        List<CameraResponse> data = taskRepository.findResultPage(taskId, pageSize, offset);
        return new PageResult(total, data);
    }

    @Scheduled(fixedDelayString = "${camera.task.cleanup-interval-ms:3600000}")
    public void cleanupExpiredTasks() {
        taskRepository.cleanupExpiredTasks();
    }

    @Getter
    @AllArgsConstructor
    public static final class TaskSubmission {
        private final String taskId;
        private final String status;
        private final boolean newOrRetry;
    }

    @Getter
    @AllArgsConstructor
    public static final class PageResult {
        private final long total;
        private final List<CameraResponse> data;
    }
}
