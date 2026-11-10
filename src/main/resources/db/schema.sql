-- 摄像头业务表已由业务系统维护，本项目不会创建或修改该表结构。
-- 固定使用 public.ga_yjyd_camera，要求 geom 为 geometry(Point, 4326)。
-- 查询使用 geom::geography 进行米制距离计算，因此建立匹配的 GiST 表达式索引。

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE INDEX IF NOT EXISTS idx_ga_yjyd_camera_geom_geog
    ON public.ga_yjyd_camera USING GIST ((geom::geography))
    WHERE geom IS NOT NULL;

ANALYZE public.ga_yjyd_camera;

-- 核验摄像头表的 SRID、几何类型和数据量：
-- SELECT ST_SRID(geom), GeometryType(geom), COUNT(*)
-- FROM public.ga_yjyd_camera
-- WHERE geom IS NOT NULL
-- GROUP BY ST_SRID(geom), GeometryType(geom);
