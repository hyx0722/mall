# 运行保障与已知边界

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 前置阅读：[events.md](events.md)
> 相关：[order-lifecycle.md](order-lifecycle.md) · [getting-started.md](getting-started.md)

## 测试

```bash
mvn test
```

仓库**共 7 个测试**——数量少是刻意的：测试集中守几处「靠约定维持、重构时容易被静默破坏」的不变量，
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

- `IdentityContextTest` 是纯 JUnit 5 + `MockHttpServletRequest`，**不需要 Spring 上下文，也不需要数据库**。
- `OutboxConfigTest` 只起一个最小 Spring 上下文（桩 `DataSource`/`RabbitTemplate`/`ObjectMapper`），
  **不连库也不连 broker**，因此任何机器上都能跑。
- `InventoryMapperConcurrencyTest` 用 Testcontainers 起**真实 MySQL**——该不变量与数据库语义强相关，
  换内存库验证没有意义。它**需要本机 Docker 守护进程处于运行状态**，首次执行会拉取 `mysql:8.4` 镜像。
  其断言是确定性的：总库存 100、每次锁 5，32 线程共 320 次抢锁后成功次数必须**恰好** 20。

> 其余模块没有测试。`mall-service-product/src/test/` 是个空目录。
> 加集成测试时注意 Boot 4 的三个坑，见 [architecture.md](architecture.md#技术栈全量版本)。

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
| `GET /actuator/metrics` | 指标列表，含下面两条自定义业务指标 |
| `GET /actuator/metrics/mall.outbox.pending` | **outbox 待投递事件数**（仅 order / payment / inventory） |
| `GET /actuator/metrics/mall.outbox.unroutable` | **无法路由而被退回的 outbox 消息数**（Counter，同上三个服务）——持续增长说明路由键与队列绑定不匹配 |

### `mall.outbox.pending`

定义在 `mall-common` 的 `OutboxMetrics`（`Gauge`，内部执行 `select count(*) from outbox where status=0`），
由 `OutboxMetricsConfig` 注册。**order、payment、inventory** 三者的启动类 `@Import` 了它——
因为只有这三个服务有 `outbox` 表。

这条指标值得留意：outbox 由各服务 relay 定时投递，一旦 relay 停摆或持续投递失败，
事件会静静堆在表里而**没有任何外部表征**（订单不推进、库存不释放），只能从业务现象倒推。
有了它就能直接观测并配阈值告警。各服务的 DLQ 堆积则在 RabbitMQ 管理台看。

> 没有引入 `micrometer-registry-prometheus`，所以**没有** `/actuator/prometheus` 端点，
> 只有 JSON 格式的 `/actuator/metrics`。全仓自定义指标就上面这两条。

> ⚠️ **服务端口不要直接暴露到公网**——下游业务服务本身不做 JWT 鉴权，只信任网关注入的身份头。
> actuator 也挂在同一个端口上。

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
  - 挂在退款上的自定义指标仍未加（只有 `mall.outbox.pending` / `mall.outbox.unroutable` 两条），
    悬挂退款目前只能从 `RefundReconcileTask` 的 WARN 日志观测。

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
  - **仍未完成**：`status=3` 只有指标与日志暴露，**没有自动恢复或后台重投入口**，需人工修好路由后手工重置该行。
  - 开着确认时 relay **不再乐观置已发送**，改由回调推进；若某环境把确认配置摘了，
    relay 会自动回落到乐观标记（否则该行会被每 3s 无限重投）——这条回落在 `OutboxServiceImpl` 里靠
    `CachingConnectionFactory.isPublisherConfirms()` 判断。

## 相关文档

- 事件与 outbox 机制： [events.md](events.md)
- 订单状态机： [order-lifecycle.md](order-lifecycle.md)
- 启动与排错： [getting-started.md](getting-started.md)
