# 运行保障与已知边界

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 前置阅读：[events.md](events.md)
> 相关：[order-lifecycle.md](order-lifecycle.md) · [getting-started.md](getting-started.md)

## 测试

```bash
mvn test
```

仓库**共 10 个测试类**——数量少是刻意的：测试集中守几处「靠约定维持、重构时容易被静默破坏」的不变量，
这类东西功能测试测不出来，只能专门守住。

| 模块 | 测试 | 守住什么 |
| ---- | ---- | ---- |
| `mall-common` | `IdentityContextTest` | 身份写入 ThreadLocal 后**必须在请求结束被清除**。漏掉 `remove()` 时，线程池复用会让下一个请求继承上一个请求的身份，表现为随机、极难复现的越权 |
| `mall-common` | `OutboxConfigTest` | `OutboxConfig` 确实导出 outbox 的两个 bean，且 `OutboxService` **是事务代理**——否则 `enqueueNewTx` 的 `REQUIRES_NEW` 不生效，扣减失败回执会随业务事务回滚 |
| `mall-common` | `OutboxConfirmInstallerTest` | 发布确认回调按 `id` 正确路由到 `markDelivered` / `markRejected` / `markUnroutable`；**退回回调必须靠 `messageId` 反查行号**（`ReturnsCallback` 拿不到 `CorrelationData`），改用它就会静默失效；反查不到行号时不得抛异常（回调跑在 broker 连接线程上） |
| `mall-service-user` | `CouponServiceImplTest` | 优惠券抵扣计算是**全仓唯一会算错钱**的地方，且错法都很安静：未达门槛仍抵扣、满减面额超过商品金额导致**倒找钱**、折扣的无限小数不 `setScale` 攒出分位差、范围限定把不参与的商品也算进门槛 |
| `mall-service-user` | `CouponPreviewRequestValidationTest` | `CouponPreviewRequest` 被两个契约不同的接口共用：给它加上 `@NotNull` 会让「结算页拉可用券」整体失效，而前端吞掉异常后**只表现为券卡片永不出现**；同时守 `lines` 上的 `@Valid` 不能漏（漏了嵌套约束全是摆设） |
| `mall-service-order` | `DiscountAllocatorTest` | 整单优惠按行占比分摊后 **Σ分摊恰好等于整单优惠**——各自四舍五入会攒出几分钱差，表现为「明细加起来 ≠ 订单总额」，结算与退款会各自差一笔且都不抛异常；另守单行分摊不超过该行小计（否则该行实付为负 = 倒找钱） |
| `mall-service-inventory` | `InventoryMapperConcurrencyTest` | 并发扣库存**绝不超卖**（`where available_stock>=qty` 条件 UPDATE 的原子性），以及释放锁定不能凭空造出库存 |
| `mall-common` | `OutboxServiceImplTest` | 管理端重投的两个不变量：**只碰 `status=3` 的行**（碰到 `status=0` 会把在途事件回炉成重复投递）、**重复调用幂等**（条件一旦从 `status=3` 放宽，运维每点一次按钮就重投一批）。另守 `limit` 有界、列表不带 payload |
| `mall-service-order` | `OrderMapperRegistrationTest` | 三个 Mapper 的注解 SQL（含 `<script>` 批量查询）能被解析。`<script>` 里的字符串会被当 **XML** 解析，所以在那里面写 `<>`（在普通 `@Select` 里完全合法）会抛 SAX 错误——而它**只在启动期暴露**：编译过、别的测试也过，直到服务起不来。本测试把该错误提前到构建期，且不需要数据库/Docker（`DataSource` 是 mock 的） |
| `mall-service-product` | `ProductCacheSerializerTest` | 缓存值序列化的往返：读回来的**运行时类型**必须是 `Product`/`PageBean`/`Category` 而非 `LinkedHashMap`。这是全仓唯一「写成功、读才炸」的失败模式，且**只在第二次请求**（读命中的那次）现形，所以只 curl 一次的验证发现不了；另守空值缓存生效、三个缓存区都注册、缓存键的归一化（`page=null` 与 `page=1` 必须同键，否则最热接口大面积未命中） |

- `IdentityContextTest` 是纯 JUnit 5 + `MockHttpServletRequest`，**不需要 Spring 上下文，也不需要数据库**。
- `OutboxConfigTest` 只起一个最小 Spring 上下文（桩 `DataSource`/`RabbitTemplate`/`ObjectMapper`），
  **不连库也不连 broker**，因此任何机器上都能跑。
- `InventoryMapperConcurrencyTest` 用 Testcontainers 起**真实 MySQL**——该不变量与数据库语义强相关，
  换内存库验证没有意义。它**需要本机 Docker 守护进程处于运行状态**，首次执行会拉取 `mysql:8.4` 镜像。
  其断言是确定性的：总库存 100、每次锁 5，32 线程共 320 次抢锁后成功次数必须**恰好** 20。

> 其余模块没有测试。
> 加集成测试时注意 Boot 4 的三个坑，见 [architecture.md](architecture.md#技术栈全量版本)。

`OutboxServiceImplTest` 与 `InventoryMapperConcurrencyTest` 都会起**真实 MySQL**（Testcontainers），
需要本机 Docker 守护进程。两者对「没装 Docker」的处理**刻意不同**：

| 测试 | 无 Docker 时 | 为什么 |
| ---- | ---- | ---- |
| `InventoryMapperConcurrencyTest` | **失败** | 它守的是超卖这条命脉，静默跳过等于没人守 |
| `OutboxServiceImplTest` | **跳过**（`disabledWithoutDocker`） | `mall-common` 原有三个测试刻意都是零基础设施的（任何机器上都能跑），不该因为新增一个测试就把本模块的 `mvn test` 变成必须装 Docker |

CI 自带 Docker，两者都会正常执行。

### CI

`.github/workflows/ci.yml` 在 push / PR 时跑两个 job：

- **backend**：`./mvnw -B clean install`（含全部测试）。GitHub 的 ubuntu runner 自带 Docker 守护进程，
  所以依赖 Testcontainers 的 `InventoryMapperConcurrencyTest` 在 CI 上能正常跑（本机没开 Docker 时会失败，
  见下）。测试失败会把 surefire 报告作为 artifact 上传。
- **frontend**：`mall-web` / `mall-admin` 各自 `npm ci && npm run build`（`fail-fast: false`，
  一个挂了不取消另一个）。`npm ci` 严格按 lockfile 安装，能顺带发现 lockfile 与 package.json 漂移。

> 两个前端此前**没有任何构建校验**，`dist/` 也不入库——CI 是唯一会发现它们构建不出来的地方。

## 可观测性

各服务与网关均接入 actuator（**端口即服务端口**，未单独开管理端口）：

| 端点 | 说明 |
| ---- | ---- |
| `GET /actuator/health` | 健康检查 |
| `GET /actuator/metrics` | 指标列表（JSON，便于人工查看单条指标） |
| `GET /actuator/prometheus` | **Prometheus 抓取端点**（文本格式），配告警规则用这个 |
| `GET /actuator/metrics/mall.outbox.pending` | **outbox 待投递事件数**（仅 order / payment / inventory） |
| `GET /actuator/metrics/mall.outbox.unroutable` | **无法路由而被退回的 outbox 消息数**（Counter，同上三个服务）——持续增长说明路由键与队列绑定不匹配 |

Prometheus 抓取需要 `micrometer-registry-prometheus`（已加在 `mall-service/pom.xml` 与
`mall-gateway/pom.xml`），并把 `prometheus` 加进各服务 `management.endpoints.web.exposure.include`
（6 个 `application.yml` 均已改好）。

> ⚠️ 端口即服务端口，而真实端口由 Nacos `common.yaml` 决定（见
> [architecture.md](architecture.md#端口从哪来这一节能省你半小时)），**别拿仓库 yml 里的端口去配抓取**。

### 自定义业务指标

| 指标 | 类型 | 服务 | 含义与告警思路 |
| ---- | ---- | ---- | ---- |
| `mall.outbox.pending` | Gauge | order / payment / inventory | 待投递（`status=0`）条数。**涨而不降**说明 relay 投不出去 |
| `mall.outbox.abandoned.backlog` | Gauge | 同上 | 已放弃（`status=3`）条数。**> 0 即需人工介入**：修好路由绑定后调 `POST /admin/outbox/requeue` |
| `mall.outbox.delivered` | Counter | 同上 | broker 已确认接收（ack）的条数。与 `pending` 对照可判断 relay 是否真在推进 |
| `mall.outbox.nack` | Counter | 同上 | 被 broker 拒绝（nack）的次数。此类失败**无限重试**，需与 `pending` 一起看 |
| `mall.outbox.abandoned` | Counter | 同上 | 累计被放弃的条数。**出现即意味着有事件永久搁置**，是上面那个 backlog 的累计视角 |
| `mall.outbox.unroutable` | Counter | 同上 | 无法路由而被退回的次数（每次退回都计，与「最终放弃」不同） |
| `mall.pay.refund.stuck` | Gauge | payment | 停在「退款中」超过阈值的退款单数。持续 > 0 说明渠道侧一直未出款 |
| `mall.pay.refund.reconciled` | Counter | payment | 退款对账累计成功重投的笔数。长期为 0 而 `stuck` 不降 = 重投一直在失败 |

`mall.outbox.abandoned` 与 `mall.outbox.abandoned.backlog` 是**刻意分开**的：
Counter 记录「历史上一共放弃过多少」（不会因重投成功而减少，可用来发现周期性复发），
Gauge 记录「此刻还积压着多少」（重投后应归零，是运维的操作反馈）。

指标命名遵循 `mall.<域>.<对象>`，描述统一写成「这条指标能发现什么故障」，与既有三条保持同一风格。

### 指标实现的两处约定（改代码前先读）

- **Counter 与 Gauge 分属两个类**，不要合并：`OutboxMeters` 持有 Counter（需要在事件发生时主动
  `increment()`，必须拿得到句柄）；`OutboxMetrics` 是 `MeterBinder`（由 Boot 绑定，绑定后应用
  **拿不到句柄**，只能被动轮询数据库注册 Gauge）。一个类扮不了两个角色。
- **新增指标一律经 `ObjectProvider<MeterRegistry>` 取 registry**，取不到就降级为 no-op。
  原因见 `OutboxConfig` 的 javadoc：`OutboxConfigTest` 用桩依赖起最小上下文、**不起 actuator 自动配置**，
  改成直接注入 `MeterRegistry` 会让那个守门测试崩掉，指标也就反向绑死了装配的可用性。

### 缓存指标：需要显式打开统计，否则恒为 0

`cache.gets{result=hit|miss}` / `cache.puts` / `cache.removals` 由 Boot 自动注册，
但**默认全是 0**——Boot 的 `RedisCacheMetrics` 是从 `RedisCache.getStatistics()` 读数的，
而 Spring Data Redis 的 `DefaultRedisCacheWriter` 默认用 `CacheStatisticsCollector.none()`（空实现）。

这比没有指标更糟：缓存明明在工作（Redis 里键都在），指标却显示从未命中，会把人引向错误的排查方向。
所以 `ProductCacheConfig` 用 `RedisCacheWriter.create(cf, cfg -> cfg.collectStatistics())` 显式打开收集。

> 换 writer 时逐项核对过 `DefaultRedisCacheWriterConfigurer` 的默认值
> （`batchStrategy=keys`、无加锁、`immediateWrites=false`），与默认的
> `nonLockingRedisCacheWriter` 完全一致，因此这是**纯增量**，写入语义未变。
> 收集器本身是内存 Map + 计数自增，无额外 Redis 往返、无定时任务。

⚠️ 统计**按应用实例**计，不做跨实例聚合：各实例只看得见自己的命中。
对 Prometheus 是对的（各自暴露、由 Prometheus 求和），但别拿单个实例的数字推断整个集群的命中率。

TTL 该调大还是调小，看这两个指标就行，不用猜。

### `mall.outbox.pending`

定义在 `mall-common` 的 `OutboxMetrics`（`Gauge`，内部执行 `select count(*) from outbox where status=0`），
由 `OutboxMetricsConfig` 注册。**order、payment、inventory** 三者的启动类 `@Import` 了它——
因为只有这三个服务有 `outbox` 表。

这条指标值得留意：outbox 由各服务 relay 定时投递，一旦 relay 停摆或持续投递失败，
事件会静静堆在表里而**没有任何外部表征**（订单不推进、库存不释放），只能从业务现象倒推。
有了它就能直接观测并配阈值告警。各服务的 DLQ 堆积则在 RabbitMQ 管理台看。

> ⚠️ **服务端口不要直接暴露到公网**——下游业务服务本身不做 JWT 鉴权，只信任网关注入的身份头。
> actuator 也挂在同一个端口上，而 `/actuator/prometheus` **没有任何鉴权**，
> 暴露出去等于公开全部运行指标。

## 日志

六个服务各自把日志写到 **`logs/<服务名>/<服务名>.log`**（`logging.file.name` 配在各服务 yml 里）：
logs 根目录下**按服务名分目录**，一个服务的滚动文件不会和别人的混在一起。

**清理是两套机制分工**，不是一个定时任务包办：

| 机制 | 负责 | 位置 |
| --- | --- | --- |
| Logback 滚动 + 保留 | 到 10MB 滚动；最多留 3 个历史文件；单服务总量上限 100MB | 各服务 yml 的 `logging.logback.rollingpolicy` |
| 定时清扫任务 | **兜底**：每 10 分钟删掉超过保留期（默认 24h）的本服务日志文件 | `mall-common` 的 `LogCleanupTask` / 网关的 `GatewayLogCleanupTask` |

> **为什么滚动交给 Logback 而不是定时任务**：正在写入的日志文件被 Logback 持有句柄，
> **Windows 上删不掉**（抛 `FileSystemException`）。所以「定时删除当前日志」在 Windows 上做不到。
> 清扫任务靠 mtime 天然避开活动文件——正在写的文件 mtime 一直在刷新，永远不会「过期」，
> 能命中保留期的绝大多数是已关闭的滚动文件。

- 任务**只清自己的**：只扫 `logs/<本服务名>/` 这一个子目录。分目录比「按文件名前缀过滤」更硬——
  **目录边界就是归属边界**，不依赖命名约定；否则先跑的服务会把别人的日志也删了。
- **网关那份是刻意重复实现的**：网关是 WebFlux，不依赖 `mall-common`（见 architecture.md 的依赖边界），
  复用不了。**改动清理逻辑时两份都要改。**
- ⚠️ **`logs` 是相对路径，取决于进程工作目录**。IDE 里跑通常落在模块目录而非项目根，
  此时用 `LOG_DIR` 环境变量指到项目根。想本地验证清扫：
  `-Dmall.log.retention-minutes=0 -Dmall.log.cleanup-interval-ms=10000`。
- ⚠️ **`product` / `user` / `gateway` 三个启动类原本没有 `@EnableScheduling`**，本次一并加上——
  漏了不会有任何编译期信号，只表现为日志永远不被清理。

## 关键设计与已知边界

### 超卖防护

扣库存不依赖分布式锁的互斥，而是「条件 UPDATE（`available_stock>=qty`）」在数据库层保证原子；
Redisson 锁用于**串行化同一商品的竞争、降低无效 UPDATE**（锁在 DB 事务之外按商品 id 升序先取好，
避免死锁）。即锁是性能优化，正确性由 DB 条件保证。

### 事件可靠性

order / payment / inventory 的对外事件均走**事务 outbox**（与业务同库同事务入 `outbox` 表，
relay 定时投递）；支付超时改用**延迟消息**（per-message TTL + 死信回主交换机）并保留低频对账兜底；
order/inventory/payment 消费端统一**有界重试(3) + DLQ**；库存扣减改为「单事务扣库存+流水」
并以 `inventory_log`（`order_id, product_id, change_type` 唯一键）做幂等。

> inventory 的**失败回执**是个刻意的例外：它产生于事务回滚之后，用
> `enqueueNewTx`（REQUIRES_NEW）独立提交。丢它会开出超卖窗口，详见
> [events.md](events.md#例外业务注定回滚但事件必须送出)。

### 主键类型

表主键/外键为 `BIGINT`，Java 侧实体与身份信息统一使用 `Long`。

### 错误提示

业务失败抛 `BusinessException`，由 `model.GlobalExceptionHandler` 统一转为 `Result.error(友好文案)`，
避免向前端泄露 SQL 等内部信息。

> 代价是**真实原因被文案盖住**：前端看到的「系统繁忙，请稍后重试」通常意味着后端抛了异常，
> 具体堆栈只在服务控制台。排查时别看前端提示，去看服务日志。

### 支付为真实 SDK 结构 + 占位配置

`mall-service-payment` 已引入支付宝（`alipay-sdk-java`）与微信（`wechatpay-java` APIv3）**官方 SDK 结构**，
但商户号 / AppID / 证书密钥当前为**占位值**（见 payment `application.yml` 的 `payment.*` 段），
故渠道回调收不到；本地演示调 `POST /pay/mock/success` 模拟支付成功即可
（走与真实回调相同的幂等落库与 `pay.success` 事件）。

> `payment.mock.enabled` 在本仓 `application.yml` 中**默认已置 `true`**，方便开箱演示。
> 它同时开关**模拟支付**与**模拟退款打款**两处，且 `POST /pay/mock/success` 只校验支付单归属、
> 不校验真实资金——**部署到任何非本地环境前必须改回 `false`**（或用 Nacos `common.yaml` 覆盖）。
>
> 同理，user 服务的默认管理员口令 `admin/admin123` 也只为本地演示存在。

### 支付超时自动取消

主路径为下单时入箱的超时延迟消息（`order.pay-timeout-minutes` 默认 30 分钟，per-message TTL 到点死信触发），
order 消费后经统一取消漏斗条件 0→4 并同事务 outbox 发 `order.canceled`
（inventory 释放锁定、payment 关闭未付支付单）；另保留每 5 分钟的对账扫表兜底，防延迟消息丢失/宕机窗口。
取消与支付同为 `order_status=0` 条件更新，谁先提交谁生效。

### 发货 / 收货并发

`/seller/ship` 与 `/receive` 事务内**第一条语句**对订单行 `select ... for update`，
串行化同一订单的并发操作。下单时锁库存、支付、取消等已处理，故发货按「卖家是否已全部发货」
聚合整单推进；混单（多卖家）必须各自都发货后整单才 `1 → 2待收货`，买家确认整单收货后 `→ 3已完成`。

### 退款的已知边界

- 只支持**整单全额**退款，不支持按明细部分退款（`refund_amount` 恒等于订单总额）；
- 未接渠道的**退款结果异步通知**，微信 `PROCESSING`（已受理未到账）在演示中直接视为成功并置终态；
- 渠道持续失败时退款单停在「退款中」、订单停在 `5退款中`。**已有退款对账补偿**：
  payment 的 `RefundReconcileTask` 每 5 分钟捞出「停在退款中超过 `payment.refund-reconcile-minutes`（默认 10 分钟）」
  的单，重投与消息消费**完全相同**的 APPROVE 指令。
  这件事成立的前提是渠道按 `refund_no`（= `out_request_no`/`out_refund_no`）幂等——**重复调用不会退两次钱**；
  少了这条性质，该任务就是危险的而非补偿。
  - 仍**未覆盖**的是「渠道已受理但迟迟不到账」的异步结果通知；演示中微信 `PROCESSING` 直接视为成功。
  - 悬挂退款**已可观测**：`mall.pay.refund.stuck`（此刻还悬挂着几笔）与
    `mall.pay.refund.reconciled`（对账累计重投成功几笔）两条指标。
    两者要一起看——`stuck` 不降且 `reconciled` 恒为 0，说明重投一直在失败（渠道持续拒付），
    而不是对账任务没跑。此前这条链路只能从 `RefundReconcileTask` 的 WARN 日志推断，
    而日志无法区分这两种情况。

### 商品缓存的已知边界

仅 **product 服务**启用缓存（`spring-boot-starter-cache` 只加在该模块），
缓存区三个：`product`（详情，60s）、`productPage`（列表，60s）、`category`（分类树/子分类，1h）。

**能安全缓存的前提**（写在这里是因为它们看起来像可优化的地方，其实不是）：

- **`product` 表没有库存列**。库存在独立的 `mall_service_inventory` 库，由 inventory 服务经条件 UPDATE 维护，
  product 服务从不写它。所以缓存商品行**不可能**缓存出陈旧库存——这是敢缓存详情的基础。
  若日后把库存挪进 `product` 表，必须重新评估这个缓存。
- **`Product.userId` 不可变**（所有 update 语句都不含 `user_id`），故缓存行不会呈现过期的归属信息。
  user 服务的 `CouponServiceImpl.assertProductOwnedBy` 靠它做发券归属校验（fail-closed），
  该不变量一旦被破坏，缓存就从性能优化变成**跨租户越权漏洞**。
- **`product` 无删除路径、id 为自增不复用**，所以缓存「不存在的 id → null」是安全的。
  不要以「防缓存穿透」为由关掉空值缓存——那会让下单路径（每行商品一次 `findProductById`）
  对不存在 id 反复打库。

**降级是有意设计的**：`ProductCacheConfig` 用 `LoggingCacheErrorHandler`，缓存故障（Redis 连不上等）
只记 WARN 并**视作未命中继续走库**，接口仍返回 200。Spring 默认的 `SimpleCacheErrorHandler` 会**重抛**，
那样公开的 `/product/list`、`/category/tree` 会直接 500。
本仓恰好埋着触发它的雷——docker-compose 把 Redis 映射到宿主机 **6380**，而各服务的
`application-datasource.yml` 都写 **6379**，真实值在 Nacos。**改 Redis 地址后务必确认缓存仍命中**
（看 `cache.gets{result=hit}`）。

**两处如实记录的陈旧窗口**（都是 Spring Cache 的标准取舍，不是 bug）：

- **价格**：下单路径 `OrderServiceImpl.createOrder` 会同步快照 `findProductById` 的价格。
  商家改价靠 `updateProduct` 的 `@CacheEvict` 立即失效（失效落在共享 Redis 上，对所有实例同时生效），
  TTL 60s 只是兜底。**调大 by-id 的 TTL 前请先重读 `createOrder`**。
- **星级**：`avgRating`/`reviewCount` 来自跨库子查询（评价由 order 服务写入），product 服务没有任何
  事件能感知新评价，故最多滞后 60 秒。没有为此引入 RabbitMQ 消费者——那需要新依赖 + 队列声明 + DLQ +
  幂等/重放考量，成本远超收益。
- 另外 `@CacheEvict` 在方法返回时执行，**可能早于**外层 `@Transactional` 提交；这个缝隙里的并发读
  会用提交前的行回填缓存，直到 TTL 过期才纠正。要做到无缝隙需改为注册
  `TransactionSynchronization.afterCommit` 的失效器。

> 缓存**没有**修好 `ProductMapper.findProductList` 的跨库依赖问题：mapper 抛出的异常发生在任何东西
> 被缓存**之前**，冷缓存照样 500。它买到的是「已缓存页面免受订单库**瞬时**不可用的影响」，仅此而已。

### 已知未完成

- **真正可用的分布式事务尚未实现**。Seata 依赖已移除，`@GlobalTransactional` 已清理；
  `undo_log` 建表语句也已从全部 5 个 `*.sql` 中删除（存量库可自行 `DROP TABLE undo_log`）。
  **但清理并不彻底**：`mall-service/*/src/main/resources/` 下仍留有 **5 个 `file.conf`**，
  内容是 Seata 客户端配置（`default.grouplist=127.0.0.1:8091`），**无任何代码或配置引用它们**，
  是纯残留文件，可安全删除。
- **发布侧已开 publisher-confirms**（`publisher-confirm-type: correlated` + `publisher-returns` +
  `template.mandatory`，三个服务各自的 `application.yml`），无法路由的消息不再静默丢失：
  经 `ReturnsCallback` 反查回 outbox 行、计入 `mall.outbox.unroutable` 并打 ERROR。
  回调装配见 `mall-common` 的 `OutboxConfirmInstaller`（**附加**到 Boot 自动配置的 RabbitTemplate 上，不替换它）。
  - 两种失败**刻意区别对待**：发送抛异常 / nack 只累加计数、保持待发送（**无限重试**——
    outbox 的意义就是「broker 迟早会回来」，加上限会让一次 broker 重启就永久丢事件）；
    而「消息被退回」是路由配置错误、重试永远不会成功，故超过 20 次置 `status=3 已放弃`。
  - **已有重投入口**（`status=3` 的恢复路径）：
    `GET /{order|pay|inventory}/admin/outbox/abandoned` 列出被放弃的事件，
    `POST /{order|pay|inventory}/admin/outbox/requeue` 把它们翻回待发送。
    两个接口均**仅管理员**（`Auths.requireAdmin()`）、**幂等**（条件 `status=3`，重复调用返回 0）、
    **有界**（`limit` 钳制在 1~200，默认 50 = relay 批大小，一次调用不会把整个积压翻过来冲垮 broker）、
    且**碰不到 `status=0/1` 的活跃行**。列表刻意不带 `payload`（事件体是内部细节且可能很大）。
    实现在 `mall-common` 的 `OutboxAdminController`，由 `OutboxConfig` 以 `@Bean` 注册——
    于是「有 outbox 表 ⇒ 有管理端」自动成立，三个服务一行 `@Import` 都不用加。
    <br>**运维流程**：先核对列表里的 `exchange`/`routingKey` 与队列绑定（那才是根因，不修就重投只会再攒满上限被放弃一次）
    → 调 `requeue` → 盯 `mall.outbox.abandoned.backlog` 是否归零。

  > ⚠️ **已知缺陷：`status=3` 目前实际不可达，上面这条链路是空转的。**
  >
  > 实测（本机 RabbitMQ + order 服务，往 outbox 插一条路由键无绑定的行）：
  > broker 对一条 mandatory 命中且无队列可路由的消息会**同时**发出 `basic.return` 和 `basic.ack`——
  > 前者表示「路由不到」，后者表示「broker 已接收」。于是两个回调都会跑：
  > `markUnroutable` 把 `retry_count` 加到 1，紧接着 `markDelivered` 执行
  > `update outbox set status=1 where id=? and status=0`，**把行置成了「已发送」**。
  > 而 relay 只捞 `status=0`，该行此后再不会被领取，`retry_count` 永远停在 1，永远到不了 20。
  >
  > 后果：这类消息被**静默标成投递成功**（既不重试也不告警），
  > `mall.outbox.abandoned` / `abandoned.backlog` 恒为 0，`/admin/outbox/abandoned` 永远返回空列表。
  > 同时 `mall.outbox.delivered` 会被这类消息虚增——它统计的是 ack 数，不等于「真的进了队列」。
  >
  > 这是**既有缺陷**，与后来新增的指标/重投入口无关（原来的 CASE UPDATE 有完全相同的竞态），
  > 只是新增的 `delivered` 与 `abandoned` 对照才让它显形。
  > 修法大致是让确认回调记住「本次投递已被退回」的行号，对这类 ack 不再置已发送。
  > **在修好之前，不要把 `mall.outbox.abandoned.backlog` 当作路由故障的告警依据**——
  > 它不会响；要发现路由问题只能看 `mall.outbox.unroutable` 及其 ERROR 日志。
  - 开着确认时 relay **不再乐观置已发送**，改由回调推进；若某环境把确认配置摘了，
    relay 会自动回落到乐观标记（否则该行会被每 3s 无限重投）——这条回落在 `OutboxServiceImpl` 里靠
    `CachingConnectionFactory.isPublisherConfirms()` 判断。

## 相关文档

- 事件与 outbox 机制： [events.md](events.md)
- 订单状态机： [order-lifecycle.md](order-lifecycle.md)
- 启动与排错： [getting-started.md](getting-started.md)
