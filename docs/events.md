# 消息与事件可靠性

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 前置阅读：[order-lifecycle.md](order-lifecycle.md)
> 相关：[operations.md](operations.md)（指标与 DLQ 观测）

订单、库存、支付三端通过 RabbitMQ 解耦。所有名称常量统一在 `mall-common.RabbitTopology`，
各服务 `RabbitConfig` 自动声明队列与绑定，**无需手工操作**。

## 拓扑全表

| 元素 | 名称 | 作用 |
| ---- | ---- | ---- |
| TopicExchange | `mall.order.exchange` | 下单/支付/取消事件总线（durable，order/inventory/payment 三端共用） |
| 路由键 | `order.created` | order 发布，inventory 订阅 |
| 路由键 | `order.canceled` | order 发布（支付超时 / 买家手动 / 商家整单取消），inventory 释放锁定 / payment 关闭未付支付单 |
| 路由键 | `inventory.deducted` / `inventory.deduct_failed` | inventory 回执，order 订阅 |
| 路由键 | `pay.success` | payment 发布，order 订阅（支付成功：0 待付款 → 1 待发货） |
| 路由键 | `refund.request` | order 发布，payment 订阅（事件体带 `action=APPLY/APPROVE/REJECT`：建退款单 / 打款 / 驳回置失败） |
| 路由键 | `pay.refund.success` | payment 发布，order 订阅（退款到账：5 退款中 → 6 已退款） |
| 路由键 | `order.refunded` | order 发布，inventory 订阅（退货入库，回补可用库存，写 `change_type=6`） |
| Queue | `q.pay.refund.request` | 支付侧消费退款指令（单队列，按 `action` 分派） |
| Queue | `q.order.refund.success` | 订单侧消费退款到账回执 |
| Queue | `q.inventory.order.refunded` | 库存侧消费退货入库事件 |
| Queue | `q.user.order.canceled` | 用户侧消费订单取消（退券 + 站内通知） |
| Queue | `q.user.order.refunded` | 用户侧消费订单已退款（退券 + 站内通知） |
| Queue | `q.user.dlq` | 用户侧死信落点 |
| 路由键 | `order.completed` | order 发布，**order 自身**消费（买家确认收货后生成商家结算明细）+ user 消费（站内通知） |
| Queue | `q.order.completed` | 订单侧消费订单已完成（结算） |
| 路由键 | `order.shipped` | order 发布，user 消费（给买家写「已发货」站内通知） |
| Queue | `q.user.order.created` | 用户侧消费下单事件（站内通知） |
| Queue | `q.user.pay.success` | 用户侧消费支付成功（站内通知） |
| Queue | `q.user.order.shipped` | 用户侧消费订单已发货（站内通知） |
| Queue | `q.user.order.completed` | 用户侧消费订单已完成（站内通知） |
| Queue | `q.inventory.order.created` | 库存侧消费下单事件 |
| Queue | `q.inventory.order.canceled` | 库存侧消费订单取消事件（释放锁定库存） |
| Queue | `q.pay.order.canceled` | 支付侧消费订单取消事件（关闭未付支付单） |
| Queue | `q.order.deducted` / `q.order.deduct.failed` | 订单侧消费扣减回执 |
| Queue | `q.order.pay.success` | 订单侧消费支付成功回执 |
| TopicExchange | `mall.order.delay.exchange` | 支付超时延迟（order 侧），per-message TTL 到点死信回主交换机 |
| 路由键 | `delay.order.timeout` / `order.timeout` | 延迟标记发往持有队列的路由键 / TTL 到点死信回主交换机的路由键 |
| Queue | `q.delay.order.timeout` | 无消费者持有队列（TTL 到期死信到主交换机触发超时取消） |
| Queue | `q.order.timeout` | order 消费延迟超时标记（走统一取消漏斗） |
| TopicExchange | `mall.order.dlx` | 统一死信交换机（order/inventory/payment 各声明同名） |
| Queue | `q.order.dlq` / `q.inventory.dlq` / `q.pay.dlq` | 各服务消费失败重试耗尽后的死信落点（绑定 DLX/`#`） |

## 事件可靠性设计

### 事务 outbox

order 的 `order.created / order.canceled`、payment 的 `pay.success`、inventory 的
`inventory.deducted / deduct_failed` **不再**用 `TransactionSynchronization.afterCommit`
或「事务外立即发」，而是与业务状态变更**同一本地事务**写入 `outbox` 表
（order / payment / inventory 库各一张），由各自 `@Scheduled(3s)` 的 relay
领取（`for update skip locked`）并投递，成功后置 `status=1`。

实现是**一份**，放在 `mall-common` 的 `com.mall.common.outbox`（原先三个服务各抄一份），
三个服务在启动类上 `@Import(OutboxConfig.class)` 复用。两个实现细节值得知道：

- 它用 **JdbcTemplate 而非 MyBatis Mapper**。因为放在 mall-common 里的 `@Mapper` 接口
  不在各服务的组件扫描范围内；若为此给各服务加 `@MapperScan`，会让 MyBatis-Plus 的
  自动扫描整体退避，**各服务自己原有的 mapper 全部停止注册**（只在启动期炸）。
  改用 JdbcTemplate 完全绕开这一层，且 `OutboxMetrics` 早已用同样方式读 outbox 表。
- `OutboxRelayTask` 靠 `@Scheduled` 驱动，因此**每个使用者都必须在启动类上
  `@EnableScheduling`**——漏掉没有编译期信号，只表现为事件静静堆在表里。

> 装配与事务代理由 `mall-common` 的 `OutboxConfigTest` 守住（不需要数据库）：
> 它验证 `OutboxConfig` 导出了 `OutboxService` 与 relay 任务两个 bean，且 `OutboxService` 是事务代理——
> 后者是 `enqueueNewTx` 的 `REQUIRES_NEW` 能否生效的前提。
> 发布确认回调的路由由 `OutboxConfirmInstallerTest` 守住，见下。

#### 发布确认：relay 怎么知道消息真的送到了

relay 用三参 `rabbitTemplate.send()` 时（无 `CorrelationData`、无 `mandatory`），
「消息到了交换机但没有任何队列可路由」会让 send 正常返回、relay 的 try/catch 不命中——
事件**静默丢失**。现在补上了发布确认，落点全在 `OutboxServiceImpl.send()` 这一个（也是唯一一个）投递出口：

- `publisher-confirm-type: correlated` 让 broker 的接收确认带上 `CorrelationData`（存 outbox 行 id）；
- `publisher-returns` + `template.mandatory` 让**无法路由**的消息被退回而不是丢弃。

两种失败的处理**刻意不同**（理由详见 `OutboxServiceImpl` 类注释）：

| 失败形态 | 处理 | 为什么 |
| ---- | ---- | ---- |
| 发送抛异常（连不上 broker）/ nack | 累加 `retry_count`，**保持待发送、无限重试** | 通常是暂时故障；加上限会让一次 broker 重启就永久丢事件 |
| 消息被退回（无法路由） | 累加计数，超 20 次置 `status=3 已放弃` + 计入 `mall.outbox.unroutable` | 路由配置错误，重试永远不会成功 |

> `ConfirmCallback` 拿得到 `CorrelationData`，但 `ReturnsCallback` **拿不到**——
> 后者只能从被退回消息的 `MessageProperties.messageId` 反查行号（两个 id 都写的是同一个 outbox 行 id）。
> 这处不对称极易被「简化」掉且**只在无法路由这条罕见路径上暴露**，故专门有测试守住。

> 开着确认时 relay **不再乐观置已发送**，改由 ack 回调推进；若某环境摘了确认配置，
> 会回落到乐观标记（否则该行没有任何回调推进，会被每 3s 无限重投）。

这根治了「订单已取消/已支付但事件没发出去」的非原子窗口——业务落库与事件入箱要么一起成功，
要么一起回滚。outbox 表存在于 **order / payment / inventory** 三库，
`mall.outbox.pending` 指标也在这三个服务上注册（见 [operations.md](operations.md#可观测性)）。

#### 例外：「业务注定回滚，但事件必须送出」

上面「同事务入箱」的前提是**业务会提交**。库存扣减失败回执是个反例：它产生于
`lockForOrder` 抛 `StockLockException`、事务**已经回滚**之后。此时若仍用默认传播入箱，
回执会挂在那同一个即将回滚的事务里被一并撤销，订单永远收不到 `deduct_failed`。

所以 `inventory` 的 `OutboxService` 多了一个 `enqueueNewTx(...)`
（`@Transactional(propagation = REQUIRES_NEW)`），只为失败回执使用。
区分规则很简单：**回执描述的是已提交的状态 → 同事务；描述的是被回滚的失败 → 独立事务。**

丢这条回执的后果不只是「晚点取消」：库存本就没锁上，而订单侧 `createPayOrder`
只校验 `order_status` 不校验库存，买家在超时取消前仍可正常付款，
订单会带着**零库存**推进到待发货——一条真实的超卖路径。
这也是它必须比 `inventory.deducted`（order 侧仅记日志）走更严机制的原因。

### 支付超时延迟消息

下单事务内同时入箱一条「超时标记」，带 `delay_ms`（= 支付超时阈值）；
relay 发送时设 per-message `expiration` 发到延迟交换机 → 无消费者持有队列 →
TTL 到点死信回主交换机 `order.timeout` → order 消费并走 `OrderCancelService.cancelByOrderNo`
统一取消漏斗（条件 0→4 + 同事务 outbox 发 `order.canceled`）。

原 60s 定时扫表**降频为 5 分钟对账兜底**（`OrderTimeoutTask`），防延迟消息丢失。

### 有界重试 + DLQ

order/inventory/payment 各自声明 `rabbitListenerContainerFactory`：

- `maxRetries(2)`（= 首次投递 + 2 次重试，共 3 次尝试），退避 `1000ms × 2.0`，上限 10s；
- `RejectAndDontRequeueRecoverer`——重试耗尽后 `basicReject(requeue=false)` 落入本服务 DLQ，
  **不再无限 requeue** 把队列堵死；
- 各入站队列带 `x-dead-letter-exchange=mall.order.dlx` 参数。

> ⚠️ **存量环境升级注意**：已存在的同名旧队列缺少 `x-dead-letter-exchange` 参数时，
> 重新声明会因参数不符报 **406 PRECONDITION_FAILED**。本地演示建议清空 RabbitMQ 数据
> 或换一个 vhost 后重启各服务。

### 库存扣减消费的幂等

移除了「先 Redis SETNX 打标」的做法；改为按商品 id 升序取 Redisson 锁后，在**单个 DB 事务**内
完成「条件扣库存 + 写 `change_type=3` 流水」，任一商品不足整单回滚。

幂等以 `inventory_log` 唯一键 `(order_id, product_id, change_type)` 为准——重投会跳过已锁商品
并重发回执，消除了「处理中崩溃 → 重投被挡 → 订单悬挂」的窗口。取消消费同理。

> 这个唯一键是**幂等的核心机制**，取消释放（`change_type=4`）与退货入库（`change_type=6`）
> 用同一个键的不同 `change_type` 值各自幂等，互不干扰。`change_type` 取值见 `inventory.sql` 注释：
> 1入库 / 2出库 / 3锁定 / 4释放锁定 / 5扣减 / 6退货入库。

### 站内通知的消费（user 服务）

「我的消息」的订单类通知由 user 服务消费 6 个事件写入（见
[domains.md](domains.md#消息通知与商店订阅)）。这里只记两条**踩了不会有报错**的规矩：

1. **一条队列只能有一个 `@RabbitListener`。** `q.user.order.canceled` 与
   `q.user.order.refunded` 早已被退券消费者持有，所以「订单取消」「退款到账」两条通知
   写在**现有处理方法的函数体里**，而不是新加一个监听器。给同一条队列挂两个监听方法
   会产生两个**竞争消费者**，Spring AMQP 轮询投递，两个方法各拿到约一半消息——
   **不报错、不记日志、不进 DLQ**，只表现为两张功能都时灵时不灵。
   站内通知的另外 4 条队列（created / pay.success / shipped / completed）没有这个问题。
2. **`order.shipped` 只在整单发货完成时发布一次**，不是每个卖家发一次。
   买家侧通知的去重键是 `(user_id, type, ref_id)`，若按卖家逐条发，
   第二个卖家的那条会被**静默吞掉**（是丢失，不是重复）。见
   [OrderShippedEvent](../model/src/main/java/com/model/event/OrderShippedEvent.java) 的类注释。

> **上线顺序**：`order.shipped` 是新路由键，先起 **user** 服务（声明 `q.user.order.shipped`），
> 再起 order 服务。反了的话第一条 `order.shipped` 因无队列可路由而被退回，
> relay 重试 20 次后把 outbox 行置 `status=3 已放弃`，通知**永久丢失**
> （各服务的启动顺序见 [getting-started.md](getting-started.md)）。

> **幂等**：MQ 是 at-least-once，重复投递靠 `notification.uk_user_type_ref`
> 唯一键挡掉，插入路径捕获 `DuplicateKeyException` 后静默返回。
> 若某次改动让重投开始落 `q.user.dlq`，那不是「通知重复」的小问题——
> 说明异常没被吞掉，真毒消息会被埋在同一堆 DLQ 里。

## 相关文档

- 状态机如何消费这些事件： [order-lifecycle.md](order-lifecycle.md)
- DLQ 堆积与 `mall.outbox.pending` 观测： [operations.md](operations.md#可观测性)
