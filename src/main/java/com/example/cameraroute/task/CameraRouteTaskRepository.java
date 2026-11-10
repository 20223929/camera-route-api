package com.example.cameraroute.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class CameraRouteTaskRepository {
    private final JdbcTemplate jdbc;
    public CameraRouteTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(String taskId, String routeId) {
        jdbc.update("INSERT INTO public.camera_route_task(task_id, route_id, status, progress, created_at, updated_at) " +
                "VALUES (?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", taskId, routeId);
    }
    public void markRunning(String taskId) {
        jdbc.update("UPDATE public.camera_route_task SET status='RUNNING', progress=10, updated_at=CURRENT_TIMESTAMP WHERE task_id=?", taskId);
    }
    public void complete(String taskId, String resultJson, int total) {
        jdbc.update("UPDATE public.camera_route_task SET status='COMPLETED', progress=100, total_count=?, result_json=?::jsonb, " +
                "updated_at=CURRENT_TIMESTAMP WHERE task_id=?", total, resultJson, taskId);
    }
    public void fail(String taskId, String message) {
        jdbc.update("UPDATE public.camera_route_task SET status='FAILED', error_message=?, updated_at=CURRENT_TIMESTAMP WHERE task_id=?", message, taskId);
    }
    public Map<String, Object> find(String taskId) {
        return jdbc.queryForMap("SELECT task_id, route_id, status, progress, total_count, error_message, created_at, updated_at " +
                "FROM public.camera_route_task WHERE task_id=?", taskId);
    }
    public String findResultJson(String taskId) {
        return jdbc.query("SELECT result_json::text FROM public.camera_route_task WHERE task_id=? AND status='COMPLETED'",
                rs -> rs.next() ? rs.getString(1) : null, taskId);
    }
}
