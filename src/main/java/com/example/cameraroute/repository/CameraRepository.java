package com.example.cameraroute.repository;

import com.example.cameraroute.dto.PageResult;
import com.example.cameraroute.dto.CameraResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CameraRepository {

    /**
     * 摄像头业务数据表。表名固定，不从配置文件或外部参数读取。
     */
    private static final String CAMERA_TABLE = "public.ga_yjyd_camera";

    /**
     * 使用 geom::geography 表达式匹配现有 geography GiST 索引，
     * 由 ST_DWithin 完成空间索引候选筛选和精确的米制距离判断。
     */
    private static final String PAGE_SQL = buildPageSql();

    private static String buildPageSql() {
        StringBuilder sql = new StringBuilder(1800);
        sql.append("WITH route_geometries AS ( ")
                .append("SELECT ST_GeomFromText(wkt, 4326) AS geom ")
                .append("FROM unnest(?::text[]) AS routes(wkt) ")
                .append("), route_segments AS ( ")
                .append("SELECT geom::geography AS geog ")
                .append("FROM route_geometries ")
                .append("), candidates AS ( ")
                .append("SELECT c.sbbm, c.sbmc, c.sbcs, c.x, c.y, c.xzqbm, c.xzqmc, ")
                .append("c.pcsbm, c.pcsmc, ")
                .append("ST_Distance(c.geom::geography, r.geog) AS distance_m ")
                .append("FROM ").append(CAMERA_TABLE).append(" c ")
                .append("JOIN route_segments r ")
                .append("ON ST_DWithin(c.geom::geography, r.geog, ?) ")
                .append("WHERE c.sbbm IS NOT NULL ")
                .append("AND c.geom IS NOT NULL ")
                .append("AND NOT ST_IsEmpty(c.geom) ")
                .append("AND ST_SRID(c.geom) = 4326 ")
                .append("AND GeometryType(c.geom) = 'POINT' ")
                .append("), deduplicated AS ( ")
                .append("SELECT DISTINCT ON (sbbm) ")
                .append("sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, ")
                .append("pcsbm, pcsmc, distance_m ")
                .append("FROM candidates ")
                .append("ORDER BY sbbm, distance_m ")
                .append("), total AS ( ")
                .append("SELECT COUNT(*) AS total_count FROM deduplicated ")
                .append("), paged AS ( ")
                .append("SELECT * FROM deduplicated ")
                .append("ORDER BY distance_m ASC, sbbm ASC LIMIT ? OFFSET ? ")
                .append(") ")
                .append("SELECT p.sbbm, p.sbmc, p.sbcs, p.x, p.y, p.xzqbm, p.xzqmc, ")
                .append("p.pcsbm, p.pcsmc, ")
                .append("ROUND(p.distance_m::numeric, 2) AS distance, ")
                .append("t.total_count ")
                .append("FROM total t LEFT JOIN paged p ON TRUE ")
                .append("ORDER BY p.distance_m ASC NULLS LAST, p.sbbm ASC NULLS LAST");
        return sql.toString();
    }

    private final JdbcTemplate jdbcTemplate;

    @Value("${camera.search.query-timeout-seconds:60}")
    private int queryTimeoutSeconds;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<CameraResponse> findCamerasNearRoute(
            List<String> routeWkts,
            int bufferMeters,
            int pageNum,
            int pageSize
    ) {
        if (routeWkts == null || routeWkts.isEmpty()) {
            log.warn("event=route.camera.query.skipped reason=empty_route_segments");
            return PageResult.empty(pageNum, pageSize);
        }

        long startedAt = System.nanoTime();
        long offset = ((long) pageNum - 1L) * (long) pageSize;
        log.info(
                "event=route.camera.database_query.started segmentCount={} bufferMeters={} "
                        + "pageNum={} pageSize={} queryTimeoutSeconds={}",
                routeWkts.size(), bufferMeters, pageNum, pageSize, queryTimeoutSeconds
        );

        // TEMPORARY: emit PostgreSQL-ready bind values for local SQL verification; remove after testing.
        log.debug("event=route.camera.sql.page sql={}", PAGE_SQL);
        log.debug(
                "event=route.camera.sql.page.params param1_routeWkts={} param2_bufferMeters={} "
                        + "param3_pageSize={} param4_offset={} queryTimeoutSeconds={}",
                routeWkts, bufferMeters, pageSize, offset, queryTimeoutSeconds
        );

        ResultSetExtractor<PageResult<CameraResponse>> pageExtractor =
                resultSet -> extractPage(resultSet, pageNum, pageSize);
        final Array[] sqlArray = new Array[1];
        PageResult<CameraResponse> page;
        try {
            page = jdbcTemplate.query(
                    connection -> createStatement(
                            connection, PAGE_SQL, routeWkts, bufferMeters,
                            pageSize, offset, sqlArray
                    ),
                    pageExtractor
            );
        } finally {
            freeSqlArray(sqlArray[0]);
        }

        log.info(
                "event=route.camera.database_query.completed resultCount={} total={} durationMs={}",
                page.getData().size(), page.getTotal(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return page;
    }

    private PageResult<CameraResponse> extractPage(
            ResultSet resultSet, int pageNum, int pageSize
    ) throws SQLException {
        List<CameraResponse> data = new ArrayList<CameraResponse>();
        long total = 0L;

        while (resultSet.next()) {
            total = resultSet.getLong("total_count");
            String sbbm = resultSet.getString("sbbm");
            // total LEFT JOIN paged returns one sentinel row when the page is empty.
            if (sbbm == null) {
                continue;
            }
            data.add(new CameraResponse(
                    sbbm,
                    resultSet.getString("sbmc"),
                    resultSet.getString("sbcs"),
                    resultSet.getObject("x") == null
                            ? null : resultSet.getDouble("x"),
                    resultSet.getObject("y") == null
                            ? null : resultSet.getDouble("y"),
                    resultSet.getString("xzqbm"),
                    resultSet.getString("xzqmc"),
                    resultSet.getString("pcsbm"),
                    resultSet.getString("pcsmc"),
                    resultSet.getDouble("distance")
            ));
        }

        return PageResult.of(total, pageNum, pageSize, data);
    }

    private PreparedStatement createStatement(
            Connection connection,
            String sql,
            List<String> routeWkts,
            int bufferMeters,
            Integer pageSize,
            Long offset,
            Array[] sqlArray
    ) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            sqlArray[0] = connection.createArrayOf(
                    "text", routeWkts.toArray(new String[0])
            );
            statement.setArray(1, sqlArray[0]);
            statement.setInt(2, bufferMeters);
            statement.setQueryTimeout(queryTimeoutSeconds);
            if (pageSize != null && offset != null) {
                statement.setInt(3, pageSize);
                statement.setLong(4, offset);
            }
            return statement;
        } catch (SQLException | RuntimeException ex) {
            try {
                statement.close();
            } catch (SQLException closeException) {
                ex.addSuppressed(closeException);
            }
            throw ex;
        }
    }

    private void freeSqlArray(Array sqlArray) {
        if (sqlArray == null) {
            return;
        }
        try {
            sqlArray.free();
        } catch (SQLException ex) {
            log.warn("event=route.camera.sql_array.release_failed", ex);
        }
    }
}
