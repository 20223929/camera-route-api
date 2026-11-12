# 摄像头沿规划路线检索 API

基于 Java 8、Spring Boot 2.7.18、Maven、PostgreSQL 和 PostGIS 实现的路线周边摄像头同步检索服务。

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

## 2. 数据库初始化与清理

### 2.1 摄像头空间索引

部署前确认 `public.ga_yjyd_camera` 已存在，且 `geom` 为 SRID 4326 的 POINT 几何字段。执行 `src/main/resources/db/schema.sql` 创建空间索引。该脚本不会创建或修改摄像头业务表。

### 2.2 清理旧版异步任务表

当前版本不使用异步任务，不需要任务表或结果表。如果数据库曾部署过旧版异步任务功能，可执行 `src/main/resources/db/remove_camera_route_task.sql`，删除：

- `public.camera_route_task_result`
- `public.camera_route_task`

该脚本只删除旧任务表，不创建任何表。确认不再需要旧任务数据后再执行。

## 3. 坐标系与路线处理

- `type` 支持 `WGS84` 和 `GCJ-02`；不传时默认为 `WGS84`。
- 兼容 `GCJ_02` 和 `GCJ02` 两种 GCJ-02 写法。
- GCJ-02 坐标由服务端转换为 WGS84 后再查询，不修改摄像头表的 `geom`。
- 所有路线统一采用同步查询，不区分长路线和短路线。
- 路线按累计地表距离切分为约 10 公里的 LINESTRING，相邻分段共享端点。
- 所有分段通过同一查询流程完成计数和分页；按 `sbbm` 全局去重，保留与路线最近的距离。
- 默认路线总长最大 5000 公里，最多 100000 个输入坐标点，分段数最多 500。超过限制时返回参数错误，调用方可拆分路线后查询。
- 查询使用数据库超时保护，默认单条 SQL 最长执行 60 秒；可根据实际数据库性能调整。
- 分页在全路线去重之后执行，不会对每个分段分别分页再合并。

## 4. API 接口

### 4.1 沿路线同步查询

`POST /api/camera/route/list?pageNum=1&pageSize=500`

请求示例：

```json
{
  "type": "WGS84",
  "bufferDistance": 100,
  "geometry": {
    "type": "LineString",
    "coordinates": [
      [113.264385, 23.129112],
      [113.270000, 23.132000]
    ]
  }
}
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | String | `WGS84` | 坐标系：`WGS84` / `GCJ-02` |
| `bufferDistance` | Integer | 100 | 路线两侧搜索距离，单位米，最大 10000 |
| `geometry` | Object | 必填 | GeoJSON Geometry，目前仅支持 `LineString` |
| `geometry.type` | String | `LineString` | 几何类型 |
| `geometry.coordinates` | Array | 必填 | 至少两个二维坐标点，每个点为 `[longitude, latitude]` |
| `pageNum` | Integer | 1 | URL 查询参数，页码从 1 开始 |
| `pageSize` | Integer | 500 | URL 查询参数，每页数量最大 1000 |

短路线和长路线使用同一个接口，同步等待查询完成后返回分页结果。响应保持统一数组结构：

```json
{
  "code": 200,
  "message": "success",
  "total": 1280,
  "pageNum": 1,
  "pageSize": 500,
  "currentCount": 1,
  "totalPages": 3,
  "data": [
    {
      "sbbm": "设备编码",
      "sbmc": "设备名称",
      "sbcs": "设备厂商",
      "x": 113.264385,
      "y": 23.129112,
      "xzqbm": "行政区编码",
      "xzqmc": "行政区名称",
      "pcsbm": "派出所编码",
      "pcsmc": "派出所名称",
      "distance": 12.35
    }
  ]
}
```

`total` 是全路线去重后的摄像头总数；`pageNum` 是当前页码；`pageSize` 是请求的每页数量上限；`currentCount` 是本次响应实际返回的记录数，等于 `data` 数组长度；`totalPages` 是总页数，按 `total / pageSize` 向上取整，`total` 为 0 时为 0。最后一页的 `currentCount` 可能小于 `pageSize`，例如总数为 1280、每页 500 条时，第三页的 `currentCount` 为 280。结果按摄像头到路线的最短距离升序排列，同距离时按 `sbbm` 排序。

### 4.2 参数限制

- `pageNum` 必须大于等于 1。
- `pageSize` 必须在 1 至 1000 之间。
- `bufferDistance` 必须在 0 至最大缓冲距离之间。
- 请求路线使用 GeoJSON `LineString`；坐标顺序为 `[longitude, latitude]`，当前仅接受二维坐标。
- 路线至少包含两个有效坐标点。
- 路线长度默认不超过 5000 公里。
- 分段长度默认约 10 公里，分段数量最多 500。

## 5. 配置

`application.yml` 中只配置查询参数，不配置摄像头表名：

```yaml
camera:
  search:
    default-buffer-meters: 100
    max-buffer-meters: 10000
    segment-length-meters: 10000
    max-route-length-meters: 5000000
    query-timeout-seconds: 60
```

数据库连接通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置，服务端口通过 `SERVER_PORT` 配置。默认数据库连接为 `jdbc:postgresql://127.0.0.1:5432/gis`。

## 6. 日志

项目使用 Spring Boot 默认的 SLF4J + Logback，不额外引入日志框架。

- 控制台和文件同时输出日志。
- 当前日志文件：`logs/camera-route-api.log`。
- 日志按日期和文件大小滚动压缩，单个滚动文件最大 100 MB。
- 默认保留 30 天，归档文件总量上限 2 GB。
- 可通过环境变量 `LOG_PATH` 修改日志目录，默认目录为项目工作目录下的 `logs`。
- 每个 HTTP 请求自动生成或接收合法的 `X-Request-Id`、`X-Trace-Id`，并在响应头中返回；服务日志自动带上这两个标识。
- 日志记录请求路径、HTTP 状态、耗时、路线坐标数量、路线长度、分段数量、查询结果数量和数据库查询耗时。
- 不记录完整路线坐标、请求体、数据库密码或其他敏感信息。
- 参数错误以 WARN 记录，未处理异常以 ERROR 记录并保留异常堆栈。

排查请求时，可使用响应头中的 `X-Request-Id` 或 `X-Trace-Id` 搜索对应日志。

## 7. 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/camera-route-api.jar
```

## 8. 验证说明

项目包含坐标转换、路线分段、同步查询服务和请求日志关联 ID 的单元测试。部署前先执行摄像头空间索引脚本，并使用实际路线执行 `EXPLAIN (ANALYZE, BUFFERS)` 验证执行计划。真实数据库端到端测试和长路线性能压测需连接实际 PostGIS 摄像头数据后完成。
