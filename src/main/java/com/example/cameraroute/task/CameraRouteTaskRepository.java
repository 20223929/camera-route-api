package com.example.cameraroute.task;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CameraRouteTaskRepository {

    private final JdbcTemplate jdbc;

    public void create(String taskId, String routeId) {
        String sql = "INSERT INTO public.camera_route_task "
                + "(task_id, route_id, status, progress, created_at, updated_at) "
                + "VALUES (?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        jdbc.update(sql, taskId, routeId);
    }

    public void markRunning(String taskId) {
        String sql = "UPDATE public.camera_route_task "
                + "SET status = 'RUNNING', progress = 10, updated_at = CURRENT_TIMESTAMP "
                + "WHERE task_id = ?";

        jdbc.update(sql, taskId);
    }

    public void complete(String taskId, String resultJson, int total) {
        String sql = "UPDATE public.camera_route_task "
                + "SET status = 'COMPLETED', progress = 100, total_count = ?, "
                + "result_json = ?::jsonb, updated_at = CURRENT_TIMESTAMP "
                + "WHERE task_id = ?";

        jdbc.update(sql, total, resultJson, taskId);
    }

    public void fail(String taskId, String message) {
        String sql = "UPDATE public.camera_route_task "
                + "SET status = 'FAILED', error_message = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE task_id = ?";

        jdbc.update(sql, message, taskId);
    }

    public Map<String, Object> find(String taskId) {
        String sql = "SELECT task_id, route_id, status, progress, total_count, "
                + "error_message, created_at, updated_at "
                + "FROM public.camera_route_task WHERE task_id = ?";

        return jdbc.queryForMap(sql, taskId);
    }

    public String findResultJson(String taskId) {
        String sql = "SELECT result_json::text "
                + "FROM public.camera_route_task "
                + "WHERE task_id = ? AND status = 'COMPLETED'";

        return jdbc.query(
                sql,
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                taskId
        );
    }
}
