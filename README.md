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

摄像头表由现有业务系统维护。本项目不会创建、重命名或迁移该业务表。表名固定写在 `CameraRepository` 中，不放入 `application.yml`、环境变量或请求参数。

业务字段映射如下：

| 字段 | 含义 |
|---|---|
| `sbbm` | 设备编码（去重键） |
| `sbmc` | 设备名称 |
| `sbcs` | 设备厂商 |
| `x` / `y` | 经度 / 纬度 |
| `xzqbm` / `xzqmc` | 行政区编码 / 名称 |
| `pcsbm` / `pcsmc` | 派出所编码 / 名称 |
| `geom` | PostGIS `geometry(Point, 4326)` 空间字段 |

数据库中的 `geom` 保持 geometry 类型，不需要迁移成 geography。查询时通过 `geom::geography` 转为米制距离计算对象。

## 2. 数据库初始化

### 2.1 摄像头空间索引

部署前请确认 `public.ga_yjyd_camera` 已存在，并且 `geom` 为 SRID 4326 的 POINT 几何字段。执行 `src/main/resources/db/schema.sql` 创建空间索引：

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE INDEX IF NOT EXISTS idx_ga_yjyd_camera_geom_geog
    ON public.ga_yjyd_camera USING GIST ((geom::geography))
    WHERE geom IS NOT NULL;

ANALYZE public.ga_yjyd_camera;
```

该脚本只创建 PostGIS 扩展（如尚未安装）和摄像头空间索引，不会创建或修改摄像头业务表。

### 2.2 异步任务表

异步查询任务使用独立的 `public.camera_route_task` 表。首次部署时单独执行 `src/main/resources/db/camera_route_task.sql`。该表仅用于保存任务状态和结果，与摄像头业务表 `public.ga_yjyd_camera` 不同。

## 3. 坐标系与路线处理

- `type` 支持 `WGS84` 和 `GCJ-02`；不传时默认 `WGS84`。
- 兼容 `GCJ_02` 和 `GCJ02` 两种 GCJ-02 写法。
- GCJ-02 坐标由服务端转换为 WGS84 后再查询；不会修改摄像头表的 `geom`。
- 路线按累计地表距离切分为约 10 公里的 LINESTRING，相邻分段共享端点。
- 所有分段在一次数据库往返中查询，并按 `sbbm` 全局去重，保留最短距离。
- 默认路线总长超过 500 公里时转为异步任务；单条路线最大 5000 公里，最多 100000 个输入坐标点，分段数最多 500。

分段长度、搜索缓冲距离和异步阈值在 `application.yml` 的 `camera.search` 下配置，不包含摄像头表名：

```yaml
camera:
  search:
    default-buffer-meters: 100
    max-buffer-meters: 10000
    segment-length-meters: 10000
    async-threshold-meters: 500000
```

## 4. API 接口

### 4.1 沿路线查询

`POST /api/camera/route/list`

请求示例：

```json
{
  "routeId": "route_001",
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
| `routeId` | String | 无 | 调用方路线编号，可选 |
| `type` | String | `WGS84` | 坐标系：`WGS84` / `GCJ-02` |
| `bufferDistance` | Integer | 100 | 路线两侧搜索距离，单位米，最大 10000 |
| `coordinates` | Array | 必填 | 至少两个坐标点，经度在前、纬度在后 |

同步响应中的 `data` 为摄像头数组，包含 `sbbm`、`sbmc`、`sbcs`、`x`、`y`、`xzqbm`、`xzqmc`、`pcsbm`、`pcsmc` 和 `distance`。其中 `distance` 是摄像头到整条路线的最短距离，单位米。

### 4.2 异步任务

路线长度超过异步阈值时，接口立即返回任务编号。任务状态接口：

`GET /api/camera/route/tasks/{taskId}`

状态包括 `QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`。任务完成后分页获取结果：

`GET /api/camera/route/tasks/{taskId}/result?pageNum=1&pageSize=500`

- `pageNum` 从 1 开始。
- `pageSize` 默认 500，最大 1000。
- 任务状态必须为 `COMPLETED` 才能读取结果。

## 5. 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/camera-route-api.jar
```

数据库连接通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置，服务端口通过 `SERVER_PORT` 配置。默认数据库连接为 `jdbc:postgresql://127.0.0.1:5432/gis`。

## 6. 验证说明

项目包含坐标转换和路线分段单元测试。部署前应先执行任务表 DDL、创建摄像头空间索引，并使用实际路线执行 `EXPLAIN (ANALYZE, BUFFERS)` 验证执行计划。真实数据库端到端测试和性能压测需连接实际 PostGIS 摄像头数据后完成。
