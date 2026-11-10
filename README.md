# 摄像头沿规划路线检索 API

Java 8+、Spring Boot 2.7.18、Maven、PostgreSQL、PostGIS。

## 1. 数据表字段

摄像头数据表字段按现有业务表映射：

| 字段 | 含义 |
|---|---|
| `sbbm` | 设备编码（去重键） |
| `sbmc` | 设备名称 |
| `sbcs` | 设备厂商 |
| `x` / `y` | 经度 / 纬度（业务返回字段） |
| `xzqbm` / `xzqmc` | 行政区编码 / 名称 |
| `pcsbm` / `pcsmc` | 派出所编码 / 名称 |
| `geom` | PostGIS `geometry(Point,4326)`，空间查询字段 |

已知数据情况：SRID=4326、几何类型 POINT、约 458129 条记录。数据库字段继续保持 geometry，不需要迁移成 geography；查询时使用 `geom::geography` 做米制距离运算。

## 2. 数据库配置与索引

默认摄像头表名为 `public.camera_info`。通过 `application.yml` 的 `camera.table-name` 或环境变量 `CAMERA_TABLE` 指定实际表名，例如 `public.your_camera_table`。字段名目前按上表固定映射。

针对 SQL 中的 `geom::geography` 表达式建立匹配的 GiST 索引（表名替换为实际表名）：

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE INDEX IF NOT EXISTS idx_camera_geom_geog
ON public.camera_info USING GIST ((geom::geography))
WHERE geom IS NOT NULL;
ANALYZE public.camera_info;
```

异步任务需要额外建立任务表。请单独执行 `src/main/resources/db/camera_route_task.sql` 一次。不要为了创建任务表而执行 `schema.sql` 中的摄像头示例建表语句；摄像头表已经存在时无需重建。

## 3. 坐标系约定

接口请求字段 `type` 支持：

- `WGS84`：WGS84 经纬度，数据库 `geom` 使用的坐标基准。
- `GCJ-02`：国内互联网地图常用坐标系。服务端会先转换成 WGS84，再进行路线分段和 PostGIS 查询。

`type` 不传时默认 `WGS84`，兼容旧请求。也接受 `GCJ_02` / `GCJ02` 作为 GCJ-02 的兼容写法。当前不支持 BD-09；如果规划服务返回 BD-09，需要先转换成 GCJ-02 或扩展转换器。

转换只作用于请求路线坐标，不会修改摄像头表中的 `geom`。GCJ-02 转 WGS84 使用迭代逆转换算法；在中国境外坐标按未偏移坐标处理。地图坐标转换存在算法模型误差，若业务对米级精度有严格要求，应以路线规划服务和数据供应方的坐标基准说明为准，并用已知控制点验证。

## 4. 路线分段和异步策略

- 服务端先把输入坐标统一转换成 WGS84。
- 按累计地表距离将路线切成约 10 公里的 LINESTRING 分段；相邻段共享分段端点，避免边界漏查。
- 所有分段 WKT 通过 PostgreSQL `text[]` 一次传入，单次数据库往返完成空间查询。
- SQL 对所有分段结果统一按 `sbbm` 去重，保留距离整条路线最近的记录，再按距离升序排列。
- 默认路线总长超过 500 公里时自动创建异步任务；短路线同步返回。
- 单条路线限制 5000 公里、最多 100000 个输入坐标点；分段数限制 500。超出时需由调用方拆分路线。

分段长度、异步阈值可在 `application.yml` 配置：

```yaml
camera:
  search:
    default-buffer-meters: 100
    max-buffer-meters: 10000
    segment-length-meters: 10000
    async-threshold-meters: 500000
```

500 公里阈值是初始工程值，应根据实际数据库、并发量、摄像头密度及执行计划压测调整。

## 5. 接口

### 5.1 沿路线查询

`POST /api/camera/route/list`

请求示例（WGS84）：

```json
{
  "routeId": "route_001",
  "type": "WGS84",
  "bufferDistance": 100,
  "coordinates": [
    {"longitude": 113.264385, "latitude": 23.129112},
    {"longitude": 113.270000, "latitude": 23.132000},
    {"longitude": 113.280000, "latitude": 23.140000}
  ]
}
```

请求示例（GCJ-02，例如规划接口返回 GCJ-02 时）：

```json
{
  "routeId": "route_002",
  "type": "GCJ-02",
  "bufferDistance": 100,
  "coordinates": [
    {"longitude": 113.270000, "latitude": 23.135000},
    {"longitude": 113.280000, "latitude": 23.140000}
  ]
}
```

字段说明：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `routeId` | String | 无 | 调用方路线编号，可选 |
| `type` | String | `WGS84` | 坐标系：`WGS84` / `GCJ-02` |
| `bufferDistance` | Integer | 100 | 路线两侧搜索距离，单位米，最大 10000 |
| `coordinates` | Array | 必填 | 至少两个经纬度点，经度在前、纬度在后 |

短路线响应的 `data` 是摄像头数组，字段为 `sbbm`、`sbmc`、`sbcs`、`x`、`y`、`xzqbm`、`xzqmc`、`pcsbm`、`pcsmc`、`distance`。`distance` 是摄像头到整条路线的最短距离，单位米。

### 5.2 长路线异步响应

路线总长超过配置阈值时，`/route/list` 不等待查询完成，而是返回任务信息：

```json
{
  "code": 200,
  "message": "success",
  "total": 0,
  "data": {
    "async": true,
    "taskId": "任务编号",
    "status": "QUEUED",
    "routeLengthMeters": 620000.0,
    "statusUrl": "/api/camera/route/tasks/任务编号",
    "resultUrl": "/api/camera/route/tasks/任务编号/result"
  }
}
```

### 5.3 查询异步任务状态

`GET /api/camera/route/tasks/{taskId}`

状态包括 `QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`。完成后可查询结果；失败时查看 `error_message`。

### 5.4 分页获取异步任务结果

`GET /api/camera/route/tasks/{taskId}/result?pageNum=1&pageSize=500`

- `pageNum` 从 1 开始。
- `pageSize` 默认 500，最大 1000。
- 必须等任务状态为 `COMPLETED` 后再获取结果。

## 6. 查询逻辑

空间过滤使用 `ST_DWithin(c.geom::geography, segment::geography, bufferDistance)`，距离通过 `ST_Distance` 计算，单位为米。摄像头可能同时落在相邻分段缓冲范围内，SQL 会跨全部分段按 `sbbm` 去重，保留最小距离记录。

路线坐标与摄像头坐标必须处于同一坐标基准。仅设置 SRID=4326 并不会自动把 GCJ-02 变成 WGS84。

## 7. 构建与运行

```bash
mvn clean test
mvn clean package
java -jar target/camera-route-api.jar
```

配置数据库连接：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`SERVER_PORT`。默认连接 `jdbc:postgresql://127.0.0.1:5432/gis`。

PowerShell 请求示例：

```powershell
$body = @{
  routeId = "route_001"
  type = "GCJ-02"
  bufferDistance = 100
  coordinates = @(
    @{ longitude = 113.270000; latitude = 23.135000 },
    @{ longitude = 113.280000; latitude = 23.140000 }
  )
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/camera/route/list" -Method Post -ContentType "application/json" -Body $body
```

## 8. 当前验证边界

源码包含坐标转换、路线分段的单元测试；需要在具备 Maven 环境时运行 `mvn clean test`。当前尚未连接实际摄像头数据库验证真实执行计划和端到端性能。上线前请先执行任务表 DDL、创建 geography GiST 索引，并用真实路线执行 `EXPLAIN (ANALYZE, BUFFERS)`。
