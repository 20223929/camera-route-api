-- 示例结构，仅在尚未存在摄像头业务表时参考；不要覆盖现有业务表。
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS public.camera_info (
    sbbm   VARCHAR(64),
    sbmc   VARCHAR(200),
    sbcs   VARCHAR(200),
    x      DOUBLE PRECISION,
    y      DOUBLE PRECISION,
    xzqbm  VARCHAR(64),
    xzqmc  VARCHAR(200),
    pcsbm  VARCHAR(64),
    pcsmc  VARCHAR(200),
    geom   geometry(Point, 4326)
);

-- 查询条件使用 geom::geography，因此建立相同表达式的 GiST 索引。
CREATE INDEX IF NOT EXISTS idx_camera_info_geom_geog
ON public.camera_info USING GIST ((geom::geography))
WHERE geom IS NOT NULL;

-- 查看现有数据的 SRID、几何类型和数量：
-- SELECT ST_SRID(geom), GeometryType(geom), COUNT(*)
-- FROM public.camera_info WHERE geom IS NOT NULL
-- GROUP BY ST_SRID(geom), GeometryType(geom);

-- Long-route asynchronous task table. Execute once in the application database.
CREATE TABLE IF NOT EXISTS public.camera_route_task (
    task_id       varchar(64) PRIMARY KEY,
    route_id      varchar(128),
    status        varchar(16) NOT NULL,
    progress      integer NOT NULL DEFAULT 0,
    total_count   integer NOT NULL DEFAULT 0,
    result_json   jsonb,
    error_message varchar(1000),
    created_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_camera_route_task_created_at
    ON public.camera_route_task (created_at DESC);
