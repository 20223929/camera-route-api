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
import java.util.Collections;
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
     * 先用 geography 缓冲区生成 geometry 包围盒，借助 geom 上已有的 GiST 索引
     * 缩小候选集；随后仍使用 geography ST_DWithin 做精确的米制距离判断。
     */
    private static final String ROUTE_CTE =
            "WITH route_geometries AS ( "
                    + "SELECT ST_GeomFromText(wkt, 4326) AS geom "
                    + "FROM unnest(?::text[]) AS routes(wkt) "
                    + "), route_segments AS ( "
                    + "SELECT geom, geom::geography AS geog, "
                    + "ST_Envelope(ST_Buffer(geom::geography, ?)::geometry) AS search_bbox "
                    + "FROM route_geometries "
                    + "), candidates AS ( "
                    + "SELECT c.sbbm, c.sbmc, c.sbcs, c.x, c.y, c.xzqbm, c.xzqmc, "
                    + "c.pcsbm, c.pcsmc, "
                    + "ST_Distance(c.geom::geography, r.geog) AS distance_m "
                    + "FROM " + CAMERA_TABLE + " c "
                    + "JOIN route_segments r "
                    + "ON c.geom && r.search_bbox "
                    + "AND ST_DWithin(c.geom::geography, r.geog, ?) "
                    + "WHERE c.sbbm IS NOT NULL "
                    + "AND c.geom IS NOT NULL "
                    + "AND NOT ST_IsEmpty(c.geom) "
                    + "AND ST_SRID(c.geom) = 4326 "
                    + "AND GeometryType(c.geom) = 'POINT' "
                    + "), deduplicated AS ( "
                    + "SELECT DISTINCT ON (sbbm) "
                    + "sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, "
                    + "pcsbm, pcsmc, distance_m "
                    + "FROM candidates "
                    + "ORDER BY sbbm, distance_m "
                    + "), total AS ( "
                    + "SELECT COUNT(*) AS total_count FROM deduplicated "
                    + "), paged AS ( "
                    + "SELECT * FROM deduplicated "
                    + "ORDER BY distance_m ASC, sbbm ASC LIMIT ? OFFSET ? "
                    + ") ";

    private static final String PAGE_SQL =
            ROUTE_CTE
                    + "SELECT p.sbbm, p.sbmc, p.sbcs, p.x, p.y, p.xzqbm, p.xzqmc, "
                    + "p.pcsbm, p.pcsmc, "
                    + "ROUND(p.distance_m::numeric, 2) AS distance, "
                    + "t.total_count "
                    + "FROM total t LEFT JOIN paged p ON TRUE "
                    + "ORDER BY p.distance_m ASC NULLS LAST, p.sbbm ASC NULLS LAST";

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
                        + "param3_bufferMeters={} param4_pageSize={} param5_offset={} queryTimeoutSeconds={}",
                routeWkts, bufferMeters, bufferMeters, pageSize, offset, queryTimeoutSeconds
        );

        ResultSetExtractor<PageResult<CameraResponse>> pageExtractor =
                resultSet -> extractPage(resultSet, pageNum, pageSize);
        PageResult<CameraResponse> page = jdbcTemplate.query(
                connection -> createStatement(
                        connection, PAGE_SQL, routeWkts, bufferMeters, pageSize, offset
                ),
                pageExtractor
        );

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
            Long offset
    ) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        Array segments = connection.createArrayOf(
                "text", routeWkts.toArray(new String[0])
        );
        statement.setArray(1, segments);
        statement.setInt(2, bufferMeters);
        statement.setInt(3, bufferMeters);
        statement.setQueryTimeout(queryTimeoutSeconds);

        if (pageSize != null && offset != null) {
            statement.setInt(4, pageSize);
            statement.setLong(5, offset);
        }

        return statement;
    }
}
