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
> 它验证 `OutboxConfig` 确实导出两个 bean，且 `OutboxService` 是事务代理——
> 后者是 `enqueueNewTx` 的 `REQUIRES_NEW` 能否生效的前提。

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

## 相关文档

- 状态机如何消费这些事件： [order-lifecycle.md](order-lifecycle.md)
- DLQ 堆积与 `mall.outbox.pending` 观测： [operations.md](operations.md#可观测性)
