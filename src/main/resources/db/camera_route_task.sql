-- Execute this script once in the same PostgreSQL database used by the API.
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
