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
- 所有分段通过一次数据库查询流程完成计数和分页；按 `sbbm` 全局去重，保留与路线最近的距离。
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
| `pageNum` | Integer | 1 | URL 查询参数，页码从 1 开始 |
| `pageSize` | Integer | 500 | URL 查询参数，每页数量最大 1000 |

短路线和长路线使用同一个接口，同步等待查询完成后返回分页结果。响应示例：

```json
{
  "code": 200,
  "message": "success",
  "total": 1280,
  "data": {
    "total": 1280,
    "pageNum": 1,
    "pageSize": 500,
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
}
```

`total` 是全路线去重后的摄像头总数。结果按摄像头到路线的最短距离升序排列，同距离时按 `sbbm` 排序。

### 4.2 参数限制

- `pageNum` 必须大于等于 1。
- `pageSize` 必须在 1 至 1000 之间。
- `bufferDistance` 必须在 0 至最大缓冲距离之间。
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

## 6. 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/camera-route-api.jar
```

## 7. 验证说明

项目包含坐标转换、路线分段和同步查询服务的单元测试。部署前先执行摄像头空间索引脚本，并使用实际路线执行 `EXPLAIN (ANALYZE, BUFFERS)` 验证执行计划。真实数据库端到端测试和长路线性能压测需连接实际 PostGIS 摄像头数据后完成。
