package com.example.cameraroute.repository;

import com.example.cameraroute.dto.CameraResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.sql.Array;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class CameraRepository {
    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private final JdbcTemplate jdbcTemplate;

    @Value("${camera.table-name:public.camera_info}")
    private String tableName;

    public CameraRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @PostConstruct
    public void validateTableName() {
        if (tableName == null || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalStateException("camera.table-name 不是合法的表名");
        }
    }

    public List<CameraResponse> findCamerasNearRoute(List<String> routeWkts, int bufferMeters) {
        if (routeWkts == null || routeWkts.isEmpty()) return java.util.Collections.emptyList();
        String sql =
            "WITH route_segments AS ( " +
            "  SELECT ST_GeomFromText(wkt, 4326)::geography AS geog " +
            "  FROM unnest(?::text[]) AS routes(wkt) " +
            "), candidates AS ( " +
            "  SELECT c.sbbm, c.sbmc, c.sbcs, c.x, c.y, c.xzqbm, c.xzqmc, " +
            "         c.pcsbm, c.pcsmc, " +
            "         ST_Distance(c.geom::geography, r.geog) AS distance_m " +
            "  FROM " + tableName + " c " +
            "  JOIN route_segments r ON ST_DWithin(c.geom::geography, r.geog, ?) " +
            "  WHERE c.sbbm IS NOT NULL AND c.geom IS NOT NULL " +
            "    AND NOT ST_IsEmpty(c.geom) AND ST_SRID(c.geom) = 4326 " +
            "    AND GeometryType(c.geom) = 'POINT' " +
            "), deduplicated AS ( " +
            "  SELECT DISTINCT ON (sbbm) sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, " +
            "         pcsbm, pcsmc, distance_m " +
            "  FROM candidates ORDER BY sbbm, distance_m " +
            ") " +
            "SELECT sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, pcsbm, pcsmc, " +
            "       ROUND(distance_m::numeric, 2) AS distance " +
            "FROM deduplicated ORDER BY distance_m ASC, sbbm ASC";

        return jdbcTemplate.query(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(sql);
            Array segments = connection.createArrayOf("text", routeWkts.toArray(new String[0]));
            ps.setArray(1, segments);
            ps.setInt(2, bufferMeters);
            return ps;
        }, (rs, rowNum) -> new CameraResponse(
            rs.getString("sbbm"), rs.getString("sbmc"), rs.getString("sbcs"),
            rs.getObject("x") == null ? null : rs.getDouble("x"),
            rs.getObject("y") == null ? null : rs.getDouble("y"),
            rs.getString("xzqbm"), rs.getString("xzqmc"), rs.getString("pcsbm"),
            rs.getString("pcsmc"), rs.getDouble("distance")
        ));
    }
}