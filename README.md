# 摄像头沿规划路线检索 API

基于 Java 8、Spring Boot 2.7.18、Maven、PostgreSQL 和 PostGIS 实现的路线周边摄像头检索服务。

## 1. 摄像头业务表

本项目固定查询现有摄像头业务表：**`public.ga_yjyd_camera`**。

| 项目 | 定义 |
|---|---|
| Schema | `public` |
| 表名 | `ga_yjyd_camera` |
| 主业务去重字段 | `sbbm` |
| 空间字段 | `geom` |
| 空间字段类型 | `geometry(Point, 4326)` |
| 坐标字段 | `x` 为经度，`y` 为纬度 |

摄像头表由现有业务系统维护。本项目不会创建、重命名或迁移该业务表。表名固定写在 `CameraRepository` 中，不放入配置文件或请求参数。

业务字段包括 `sbbm`、`sbmc`、`sbcs`、`x`、`y`、`xzqbm`、`xzqmc`、`pcsbm`、`pcsmc` 和 `geom`。数据库中的 `geom` 保持 geometry 类型，查询时通过 `geom::geography` 计算米制距离。

## 2. 数据库初始化

### 2.1 摄像头空间索引

部署前确认 `public.ga_yjyd_camera` 已存在，且 `geom` 为 SRID 4326 的 POINT 几何字段。执行 `src/main/resources/db/schema.sql` 创建空间索引。该脚本不会创建或修改摄像头业务表。

### 2.2 异步任务表及升级

首次部署或从旧版本升级时，执行 `src/main/resources/db/camera_route_task.sql`。

- `public.camera_route_task`：仅保存任务状态、请求指纹、计数和过期时间。
- `public.camera_route_task_result`：逐条保存任务结果，并通过外键级联删除。
- 升级脚本会删除旧版任务数据，因为旧记录没有请求指纹，无法安全参与复用；并移除旧的 `route_id`、`result_json` 字段。

## 3. 坐标系与路线处理

- `type` 支持 `WGS84` 和 `GCJ-02`；不传时默认为 `WGS84`。
- 兼容 `GCJ_02` 和 `GCJ02` 两种 GCJ-02 写法。
- GCJ-02 坐标由服务端转换为 WGS84 后再查询，不修改摄像头表的 `geom`。
- 路线按累计地表距离切分为约 10 公里的 LINESTRING，相邻分段共享端点。
- 所有分段在一次数据库往返中查询，并按 `sbbm` 全局去重，保留最短距离。
- 默认路线总长超过 500 公里时转为异步任务；单条路线最大 5000 公里，最多 100000 个输入坐标点，分段数最多 500。

## 4. 异步任务复用策略

异步任务使用规范化请求内容生成 SHA-256 `request_hash`，不依赖调用方提供的路线编号。指纹包含：

- 转换为 WGS84 后的有序路线坐标；
- 实际生效的 `bufferDistance`；
- 路线分段长度和指纹算法版本。

因此，同一条路线即使分别以 WGS84 和 GCJ-02 输入，只要转换后的路线和查询参数相同，也可以复用同一个任务。

提交相同请求时：

| 已有任务状态 | 处理方式 |
|---|---|
| `QUEUED` / `RUNNING` | 返回原任务 ID，不重复执行 |
| `COMPLETED` 且未过期 | 返回原任务 ID，复用结果 |
| `FAILED` | 使用新任务 ID 重试 |
| 已过期 | 创建新任务 |

成功任务从完成时间起缓存 24 小时；失败任务保留 1 小时。超过 30 分钟没有更新的排队或运行任务视为异常遗留任务，可由相同请求触发重新执行。服务每小时清理过期任务，结果记录通过外键级联删除。

结果通过数据库 `LIMIT/OFFSET` 分页读取，不会把全部结果 JSON 加载到 Java 内存。

## 5. API 接口

### 5.1 沿路线查询

`POST /api/camera/route/list`

请求示例：

```json
{
  "type": "WGS84",
  "bufferDistance": 100,
  "coordinates": [
    {"longitude": 113.264385, "latitude": 23.129112},
    {"longitude": 113.270000, "latitude": 23.132000}
  ]
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | String | `WGS84` | 坐标系：`WGS84` / `GCJ-02` |
| `bufferDistance` | Integer | 100 | 路线两侧搜索距离，单位米，最大 10000 |
| `coordinates` | Array | 必填 | 至少两个坐标点，经度在前、纬度在后 |

请求不再包含 `routeId`。短路线同步返回摄像头数组；超过异步阈值时返回任务信息：

```json
{
  "async": true,
  "taskId": "任务编号",
  "status": "QUEUED",
  "reused": false,
  "routeLengthMeters": 520000.0,
  "statusUrl": "/api/camera/route/tasks/任务编号",
  "resultUrl": "/api/camera/route/tasks/任务编号/result"
}
```

其中 `reused=true` 表示复用了已有任务，不会再次启动后台查询。

### 5.2 查询任务状态

`GET /api/camera/route/tasks/{taskId}`

状态包括 `QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`。

### 5.3 分页获取任务结果

`GET /api/camera/route/tasks/{taskId}/result?pageNum=1&pageSize=500`

- `pageNum` 从 1 开始。
- `pageSize` 默认 500，最大 1000。
- 任务状态必须为 `COMPLETED` 才能读取结果。
- 响应总数为任务结果总数，数据由数据库直接分页。

## 6. 配置

分段长度、搜索缓冲距离和异步阈值在 `application.yml` 的 `camera.search` 下配置，不包含摄像头表名：

```yaml
camera:
  search:
    default-buffer-meters: 100
    max-buffer-meters: 10000
    segment-length-meters: 10000
    async-threshold-meters: 500000
  task:
    cleanup-interval-ms: 3600000
```

## 7. 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/camera-route-api.jar
```

数据库连接通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置，服务端口通过 `SERVER_PORT` 配置。默认数据库连接为 `jdbc:postgresql://127.0.0.1:5432/gis`。

## 8. 验证说明

项目包含坐标转换和路线分段单元测试。部署前先执行任务表 DDL、创建摄像头空间索引，并使用实际路线执行 `EXPLAIN (ANALYZE, BUFFERS)` 验证执行计划。真实数据库端到端测试和性能压测需连接实际 PostGIS 摄像头数据后完成。
