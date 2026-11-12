-- 从同步查询版本移除旧版异步任务机制。
-- 先删除结果表，再删除任务表；不创建任何新表。
DROP TABLE IF EXISTS public.camera_route_task_result;
DROP TABLE IF EXISTS public.camera_route_task;
