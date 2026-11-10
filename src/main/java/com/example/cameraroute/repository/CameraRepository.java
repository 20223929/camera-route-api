package com.example.cameraroute.repository;

import com.example.cameraroute.dto.CameraPage;
import com.example.cameraroute.dto.CameraResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CameraRepository {

    /**
     * 摄像头业务数据表。表名固定，不从配置文件或外部参数读取。
     */
    private static final String CAMERA_TABLE = "public.ga_yjyd_camera";

    private static final String ROUTE_CTE =
            "WITH route_segments AS ( "
                    + "SELECT ST_GeomFromText(wkt, 4326)::geography AS geog "
                    + "FROM unnest(?::text[]) AS routes(wkt) "
                    + "), candidates AS ( "
                    + "SELECT c.sbbm, c.sbmc, c.sbcs, c.x, c.y, c.xzqbm, c.xzqmc, "
                    + "c.pcsbm, c.pcsmc, "
                    + "ST_Distance(c.geom::geography, r.geog) AS distance_m "
                    + "FROM " + CAMERA_TABLE + " c "
                    + "JOIN route_segments r "
                    + "ON ST_DWithin(c.geom::geography, r.geog, ?) "
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
                    + ") ";

    private static final String COUNT_SQL =
            ROUTE_CTE + "SELECT COUNT(*) FROM deduplicated";

    private static final String PAGE_SQL =
            ROUTE_CTE
                    + "SELECT sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, "
                    + "pcsbm, pcsmc, "
                    + "ROUND(distance_m::numeric, 2) AS distance "
                    + "FROM deduplicated "
                    + "ORDER BY distance_m ASC, sbbm ASC LIMIT ? OFFSET ?";

    private final JdbcTemplate jdbcTemplate;

    @Value("${camera.search.query-timeout-seconds:60}")
    private int queryTimeoutSeconds;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CameraPage findCamerasNearRoute(
            List<String> routeWkts,
            int bufferMeters,
            int pageNum,
            int pageSize
    ) {
        if (routeWkts == null || routeWkts.isEmpty()) {
            return new CameraPage(0, pageNum, pageSize, Collections.<CameraResponse>emptyList());
        }

        Long count = jdbcTemplate.query(
                connection -> createStatement(
                        connection, COUNT_SQL, routeWkts, bufferMeters, null, null
                ),
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L
        );

        long offset = ((long) pageNum - 1L) * (long) pageSize;
        List<CameraResponse> data = jdbcTemplate.query(
                connection -> createStatement(
                        connection, PAGE_SQL, routeWkts, bufferMeters, pageSize, offset
                ),
                (resultSet, rowNum) -> new CameraResponse(
                        resultSet.getString("sbbm"),
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
                )
        );

        return new CameraPage(count == null ? 0L : count, pageNum, pageSize, data);
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
        statement.setQueryTimeout(queryTimeoutSeconds);

        if (pageSize != null && offset != null) {
            statement.setInt(3, pageSize);
            statement.setLong(4, offset);
        }

        return statement;
    }
}
