-- 摄像头业务数据表：public.ga_yjyd_camera
-- 该表由业务系统维护，本项目不负责创建或修改摄像头业务表结构。
-- 必需字段：
-- sbbm, sbmc, sbcs, x, y, xzqbm, xzqmc, pcsbm, pcsmc, geom
-- geom 应为 geometry(Point, 4326)，x 为经度，y 为纬度。
--
-- 查询使用 geom::geography 进行米制距离计算，因此创建匹配的 GiST 表达式索引。
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE INDEX IF NOT EXISTS idx_ga_yjyd_camera_geom_geog
    ON public.ga_yjyd_camera USING GIST ((geom::geography))
    WHERE geom IS NOT NULL;

ANALYZE public.ga_yjyd_camera;

-- 查看摄像头数据的 SRID、几何类型和数量：
-- SELECT ST_SRID(geom), GeometryType(geom), COUNT(*)
-- FROM public.ga_yjyd_camera
-- WHERE geom IS NOT NULL
-- GROUP BY ST_SRID(geom), GeometryType(geom);
