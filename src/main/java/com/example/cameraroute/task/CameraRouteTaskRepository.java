package com.example.cameraroute.task;

import com.example.cameraroute.dto.CameraResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CameraRouteTaskRepository {

    private final JdbcTemplate jdbc;

    /**
     * Atomically creates a task or reuses the valid task for the same request.
     * Failed, expired, or abandoned queued tasks are reset in place, keeping the task id stable.
     */
    public TaskRecord acquire(String taskId, String requestHash) {
        String insert = "INSERT INTO public.camera_route_task "
                + "(task_id, request_hash, status, progress, total_count, created_at, updated_at, expires_at) "
                + "VALUES (?, ?, 'QUEUED', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP + INTERVAL '24 hours') "
                + "ON CONFLICT (request_hash) DO UPDATE SET "
                + "status = 'QUEUED', progress = 0, total_count = 0, error_message = NULL, "
                + "created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, "
                + "expires_at = CURRENT_TIMESTAMP + INTERVAL '24 hours' "
                + "WHERE public.camera_route_task.status = 'FAILED' "
                + "OR public.camera_route_task.expires_at <= CURRENT_TIMESTAMP "
                + "OR (public.camera_route_task.status = 'QUEUED' "
                + "AND public.camera_route_task.updated_at < CURRENT_TIMESTAMP - INTERVAL '30 minutes') "
                + "RETURNING task_id, status";

        List<TaskRecord> acquired = jdbc.query(
                insert,
                (rs, rowNum) -> new TaskRecord(
                        rs.getString("task_id"),
                        rs.getString("status"),
                        true
                ),
                taskId,
                requestHash
        );

        if (!acquired.isEmpty()) {
            return acquired.get(0);
        }

        String select = "SELECT task_id, status FROM public.camera_route_task "
                + "WHERE request_hash = ? AND expires_at > CURRENT_TIMESTAMP";
        return jdbc.queryForObject(
                select,
                (rs, rowNum) -> new TaskRecord(
                        rs.getString("task_id"),
                        rs.getString("status"),
                        false
                ),
                requestHash
        );
    }

    public int markRunning(String taskId) {
        return jdbc.update(
                "UPDATE public.camera_route_task "
                        + "SET status = 'RUNNING', progress = 10, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE task_id = ? AND status = 'QUEUED'",
                taskId
        );
    }

    @Transactional
    public void complete(String taskId, List<CameraResponse> cameras) {
        jdbc.update(
                "DELETE FROM public.camera_route_task_result WHERE task_id = ?",
                taskId
        );

        String insert = "INSERT INTO public.camera_route_task_result "
                + "(task_id, sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, pcsbm, pcsmc, distance) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbc.batchUpdate(
                insert,
                cameras,
                500,
                (PreparedStatement ps, CameraResponse camera) -> {
                    ps.setString(1, taskId);
                    ps.setString(2, camera.getSbbm());
                    ps.setString(3, camera.getSbmc());
                    ps.setString(4, camera.getSbcs());

                    if (camera.getX() == null) {
                        ps.setNull(5, Types.DOUBLE);
                    } else {
                        ps.setDouble(5, camera.getX());
                    }

                    if (camera.getY() == null) {
                        ps.setNull(6, Types.DOUBLE);
                    } else {
                        ps.setDouble(6, camera.getY());
                    }

                    ps.setString(7, camera.getXzqbm());
                    ps.setString(8, camera.getXzqmc());
                    ps.setString(9, camera.getPcsbm());
                    ps.setString(10, camera.getPcsmc());
                    ps.setDouble(11, camera.getDistance());
                }
        );

        jdbc.update(
                "UPDATE public.camera_route_task "
                        + "SET status = 'COMPLETED', progress = 100, total_count = ?, "
                        + "error_message = NULL, updated_at = CURRENT_TIMESTAMP, "
                        + "expires_at = CURRENT_TIMESTAMP + INTERVAL '24 hours' WHERE task_id = ?",
                cameras.size(),
                taskId
        );
    }

    public void fail(String taskId, String message) {
        jdbc.update(
                "UPDATE public.camera_route_task "
                        + "SET status = 'FAILED', error_message = ?, updated_at = CURRENT_TIMESTAMP, "
                        + "expires_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE task_id = ?",
                message,
                taskId
        );
    }

    public Map<String, Object> find(String taskId) {
        return jdbc.queryForMap(
                "SELECT task_id, request_hash, status, progress, total_count, "
                        + "error_message, created_at, updated_at, expires_at "
                        + "FROM public.camera_route_task WHERE task_id = ?",
                taskId
        );
    }

    public long countResults(String taskId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.camera_route_task_result WHERE task_id = ?",
                Long.class,
                taskId
        );
        return count == null ? 0L : count;
    }

    public List<CameraResponse> findResultPage(String taskId, int limit, long offset) {
        String sql = "SELECT sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, pcsbm, pcsmc, distance "
                + "FROM public.camera_route_task_result WHERE task_id = ? "
                + "ORDER BY distance ASC, sbbm ASC LIMIT ? OFFSET ?";

        return jdbc.query(
                sql,
                (rs, rowNum) -> new CameraResponse(
                        rs.getString("sbbm"),
                        rs.getString("sbmc"),
                        rs.getString("sbcs"),
                        rs.getObject("x") == null ? null : rs.getDouble("x"),
                        rs.getObject("y") == null ? null : rs.getDouble("y"),
                        rs.getString("xzqbm"),
                        rs.getString("xzqmc"),
                        rs.getString("pcsbm"),
                        rs.getString("pcsmc"),
                        rs.getDouble("distance")
                ),
                taskId,
                limit,
                offset
        );
    }

    public int cleanupExpiredTasks() {
        return jdbc.update(
                "DELETE FROM public.camera_route_task WHERE expires_at <= CURRENT_TIMESTAMP"
        );
    }

    @lombok.Value
    public static class TaskRecord {

        String taskId;
        String status;
        boolean newlyAcquired;
    }
}
