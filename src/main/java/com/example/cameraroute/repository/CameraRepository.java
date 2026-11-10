package com.example.cameraroute.repository;

import com.example.cameraroute.dto.CameraResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.Collections;
import java.util.List;

@Repository
public class CameraRepository {

    /**
     * 摄像头业务数据表。表名固定，不从配置文件或外部参数读取。
     */
    private static final String CAMERA_TABLE = "public.ga_yjyd_camera";

    private final JdbcTemplate jdbcTemplate;

    public CameraRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CameraResponse> findCamerasNearRoute(
            List<String> routeWkts,
            int bufferMeters
    ) {
        if (routeWkts == null || routeWkts.isEmpty()) {
            return Collections.emptyList();
        }

        String sql =
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
                        + ") "
                        + "SELECT sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, "
                        + "pcsbm, pcsmc, "
                        + "ROUND(distance_m::numeric, 2) AS distance "
                        + "FROM deduplicated "
                        + "ORDER BY distance_m ASC, sbbm ASC";

        return jdbcTemplate.query(
                connection -> {
                    java.sql.PreparedStatement statement =
                            connection.prepareStatement(sql);
                    Array segments = connection.createArrayOf(
                            "text",
                            routeWkts.toArray(new String[0])
                    );
                    statement.setArray(1, segments);
                    statement.setInt(2, bufferMeters);
                    return statement;
                },
                (resultSet, rowNum) -> new CameraResponse(
                        resultSet.getString("sbbm"),
                        resultSet.getString("sbmc"),
                        resultSet.getString("sbcs"),
                        resultSet.getObject("x") == null
                                ? null
                                : resultSet.getDouble("x"),
                        resultSet.getObject("y") == null
                                ? null
                                : resultSet.getDouble("y"),
                        resultSet.getString("xzqbm"),
                        resultSet.getString("xzqmc"),
                        resultSet.getString("pcsbm"),
                        resultSet.getString("pcsmc"),
                        resultSet.getDouble("distance")
                )
        );
    }
}
