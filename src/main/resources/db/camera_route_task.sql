-- 异步任务表升级脚本。请在 API 使用的 PostgreSQL 数据库中执行。
-- 旧版任务依赖 route_id 且将结果存入 result_json，无法安全映射到请求指纹；
-- 因此升级时清理旧任务，再切换到请求指纹和独立结果表。

CREATE TABLE IF NOT EXISTS public.camera_route_task (
    task_id       varchar(64) PRIMARY KEY,
    request_hash  varchar(64) NOT NULL,
    status        varchar(16) NOT NULL,
    progress      integer NOT NULL DEFAULT 0,
    total_count   integer NOT NULL DEFAULT 0,
    error_message varchar(1000),
    created_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    timestamp without time zone NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours')
);

-- 兼容从旧版任务表升级。
ALTER TABLE public.camera_route_task
    ADD COLUMN IF NOT EXISTS request_hash varchar(64);
ALTER TABLE public.camera_route_task
    ADD COLUMN IF NOT EXISTS expires_at timestamp without time zone
    NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours');

-- 旧任务没有 request_hash，不能参与请求复用，升级时清理。
DELETE FROM public.camera_route_task WHERE request_hash IS NULL;

ALTER TABLE public.camera_route_task
    ALTER COLUMN request_hash SET NOT NULL;

ALTER TABLE public.camera_route_task
    DROP COLUMN IF EXISTS route_id,
    DROP COLUMN IF EXISTS result_json;

CREATE UNIQUE INDEX IF NOT EXISTS uk_camera_route_task_request_hash
    ON public.camera_route_task (request_hash);

CREATE INDEX IF NOT EXISTS idx_camera_route_task_expires_at
    ON public.camera_route_task (expires_at);

CREATE TABLE IF NOT EXISTS public.camera_route_task_result (
    id         bigserial PRIMARY KEY,
    task_id    varchar(64) NOT NULL
               REFERENCES public.camera_route_task (task_id) ON DELETE CASCADE,
    sbbm       varchar(128),
    sbmc       text,
    sbcs       text,
    x          double precision,
    y          double precision,
    xzqbm      varchar(64),
    xzqmc      varchar(255),
    pcsbm      varchar(64),
    pcsmc      varchar(255),
    distance   numeric(14, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_camera_route_task_result_page
    ON public.camera_route_task_result (task_id, distance, sbbm);
