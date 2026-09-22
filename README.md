# 短视频 Feed（Java 版）

对标 Go 版「短视频 Feed 后端」的 Java 复刻：同样的五个核心设计点，用 Java 主流技术栈实现，前后端 + 中间件一键可跑。

- 后端：**Spring Boot 3.3 + MyBatis-Plus + MySQL 8 + Redis 7 + RocketMQ 5.3**（JDK 21）
- 前端：**Vue 3 + Vite + Element Plus + Pinia**
- 基础设施：`docker-compose.yml` 一键起 MySQL / Redis / RocketMQ（含 Dashboard 管理界面）

---

## 一、五个核心设计点（与原项目逐条对应）

### 1. 双令牌登录态（Access + Refresh）
- Access 短令牌（默认 5 分钟，方便演示）走 `Authorization: Bearer`，`JwtInterceptor` 统一鉴权；
- Refresh 长令牌（7 天）**只存 SHA-256 哈希**到 Redis（`auth:refresh:{uid}`），泄露也无法伪造；
- 刷新用 **Lua 脚本原子完成「校验旧哈希 + 写入新哈希」**：并发携带同一 Refresh 刷新时只有一个 CAS 成功；
- **重放检测**：已被轮换的旧 Refresh 再度出现 → 判定泄露，直接吊销整个会话强制重新登录；
- 登出：Access 的 `jti` 进黑名单（剩余有效期为 TTL），Refresh 哈希删除；
- 前端配合：axios 拦截器捕获 401 → **single-flight 静默续期**（并发 401 只刷新一次）→ 重放原请求，全程无感。

> 代码：`server/.../auth/`（`AuthService` 含 Lua 脚本）、`web/src/api/request.js`

### 2. 点赞一致性
同一本地事务完成三件事（`LikeService.like`）：
1. `INSERT user_like` —— `(user_id, video_id)` 唯一索引兜底，**并发重复点赞只有一个成功**（重复返回 409）；
2. `UPDATE video SET like_count = like_count + 1` —— 原子自增，不读不改无竞态；
3. 写 Outbox 事件（见第 5 点）。

### 3. Feed 流游标分页
- 所有列表（公共 Feed / 关注 Feed / 评论）统一 **雪花 ID 倒序 + cursor 分页**：`WHERE id < cursor ORDER BY id DESC LIMIT n+1`，**多查一条判断 hasMore**，无深分页 OFFSET；
- 雪花 ID 手写实现（`SnowflakeIdGenerator`）：41bit 时间戳 + 5bit worker + 12bit 序列，**趋势递增天然适合做 cursor**；含时钟回拨处理（小回拨等待追上，大回拨拒绝发号）。

### 4. 视频详情缓存三件套（Cache-Aside）
`VideoCacheService` + 手写 `Singleflight`：
- **随机 TTL**（30min + 0~10min 抖动）→ 防雪崩；
- **空值缓存**（不存在的 id 写 `__NULL__`，短 TTL）→ 防穿透；
- **Singleflight**（ConcurrentHashMap + CompletableFuture，缓存失效瞬间 N 个并发 MISS 只有一个查库回填）→ 防击穿，Go singleflight 的 Java 手写版；
- 一致性：点赞/评论在**事务提交后**才删缓存（`TxUtils.afterCommit`），避免并发读把旧值写回缓存。

### 5. 热门榜与异步更新（Transactional Outbox + RocketMQ）
- 热度 = 点赞×3 + 评论×5 + 浏览×1，存 Redis **ZSet**（`hot:rank`）；
- 互动事件与业务同事务写 `outbox_event` 表 → `OutboxRelay` 每 2s 轮询投递 RocketMQ：
  - `SELECT ... FOR UPDATE SKIP LOCKED`（MySQL 8，多实例安全）
  - `syncSend` 同步等 Broker ACK，SEND_OK 才标 SENT
  - 失败**指数退避重试**（2^n 秒，上限 60s，5 次后标 FAILED）
- 消费者 `ZINCRBY` 增量更新热度；**每 60s 定时全量重建**从 DB 重算覆盖（增量丢失/Redis 清空都能校准，最终一致）；
- 高频浏览量不走 Outbox：旁路线程池异步落库 + 直接 ZINCRBY（允许少量丢失，全量重建兜底）——工程上的取舍。

---

## 二、快速启动

前提：JDK 21、Node 18+、Docker。端口均避开本机常用端口（见文末端口表）。

```bash
# 1. 中间件（首次启动自动执行 init.sql 建表）
docker compose up -d

# 2. 后端（端口 8080，首次启动自动灌入演示数据）
cd server
mvn -s ../settings.xml spring-boot:run        # 或 mvn package 后 java -jar target/*.jar

# 3. 前端（端口 5173，API 走 Vite 代理，无需 CORS）
cd web
npm install
npm run dev
```

打开 http://localhost:5173 ，演示账号 **demo / 123456**（另有 alice、bob，密码同）。

## 三、全链路自测

```bash
node scripts/smoke-test.mjs    # 27 项断言：认证/分页/缓存/点赞唯一性/关注/热榜/令牌轮换/登出黑名单
```

## 四、演示剧本（给面试官/老师）

| 步骤 | 操作 | 证明什么 |
|---|---|---|
| 1 | 登录 demo → 首页无限滚动 | 游标分页 + 批量点赞状态 |
| 2 | 详情页点赞，再点一次 | 第二次被唯一索引拦截（提示已点赞） |
| 3 | 点赞后立刻看热榜，几秒后刷新 | Outbox→RocketMQ 异步更新、最终一致 |
| 4 | 控制台执行 `localStorage.setItem('vf.accessToken', 'x')` 后刷新页面 | 前端 401 静默续期无感恢复 |
| 5 | 退出登录后回退首页 | Access 黑名单 + 路由守卫 |
| 6 | 打开 http://localhost:8183 （guest/guest）看 topic `video-event` | MQ 链路真实可查 |
| 7 | `docker exec video-feed-redis redis-cli ZREVRANGE hot:rank 0 5 WITHSCORES` | 热榜 ZSet 原始数据 |
| 8 | `docker exec video-feed-mysql mysql -uroot -p123456 -e "SELECT status,COUNT(*) FROM video_feed.outbox_event GROUP BY status"` | Outbox 投递状态 |

## 五、API 一览

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（自动登录返回双令牌） |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新令牌（Lua 原子轮换） |
| POST | `/api/auth/logout` | 登出（jti 黑名单 + 删 Refresh） |
| POST | `/api/videos` | 发布视频（URL 引用） |
| GET | `/api/videos/{id}` | 视频详情（缓存三件套） |
| POST/DELETE | `/api/videos/{id}/like` | 点赞 / 取消（唯一索引 + 原子计数） |
| GET/POST | `/api/videos/{id}/comments` | 评论列表 / 发评论 |
| GET | `/api/feed` | 公共 Feed（游标分页） |
| GET | `/api/feed/following` | 关注 Feed |
| GET | `/api/feed/hot` | 热门榜（ZSet） |
| POST/DELETE | `/api/users/{id}/follow` | 关注 / 取关 |
| GET | `/api/users/me`、`/api/users/{id}` | 我的信息 / 用户主页 |

统一响应 `{code, message, data}`；code=0 成功，401 未登录/令牌失效，409 重复操作。

## 六、目录结构

```
video-feed/
├── docker-compose.yml        # MySQL/Redis/RocketMQ(+Dashboard)
├── init.sql                  # 建表（6 张表 + 索引）
├── rocketmq/broker.conf      # brokerIP1=127.0.0.1 供宿主机客户端直连
├── scripts/smoke-test.mjs    # 27 项全链路冒烟测试
├── server/                   # Spring Boot 后端
│   └── src/main/java/com/videofeed/
│       ├── auth/             # JWT 双令牌 + Lua 轮换 + 拦截器
│       ├── id/               # 手写 Snowflake（含时钟回拨处理）
│       ├── cache/            # Singleflight + Cache-Aside
│       ├── mq/               # OutboxRelay + 热榜消费者
│       ├── service/          # Video/Like/Comment/Follow/HotRank/Outbox
│       ├── controller/ common/ config/ entity/ mapper/ dto/
│       └── bootstrap/        # 启动种子数据（3用户/12视频）
└── web/                      # Vue 3 前端
    └── src/
        ├── api/request.js    # axios + 401 静默续期（前端 single-flight）
        ├── stores/auth.js    # 双令牌持久化
        └── views/            # 登录/首页Feed/关注/热榜/视频详情
```

## 七、端口表（本机多项目共存，全部避开常用端口）

| 服务 | 端口 |
|---|---|
| MySQL | 3320 |
| Redis | 6390 |
| RocketMQ NameServer | 9881 |
| RocketMQ Broker | 13911（VIP 13909 / HA 13912） |
| RocketMQ Dashboard | 8183（guest/guest） |
| 后端 | 8080 |
| 前端 | 5173 |
