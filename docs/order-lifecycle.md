# 订单状态机

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 前置阅读：[architecture.md](architecture.md)
> 相关：[events.md](events.md)（MQ 事件与可靠性）· [api.md](api.md#order)（接口）

订单的全部流转都收敛在 `orders.order_status` 这一个字段上。本文先给一张全景图，再逐段拆解。
所有状态推进都沿用同一个惯例：**条件 UPDATE + 受影响行数**——把「当前状态必须是 X」写进 `WHERE`，
用受影响行数判断是否抢到这次转移，天然防重、天然并发安全。

## 状态全景

```
                        ┌──────────────── 买家申请退款（整单全额）
                        ▼                 （仅 1/2/3 可申请）
   下单          支付成功       全部卖家发货        买家确认收货
  createOrder ──► 0 待付款 ──► 1 待发货 ──────► 2 待收货 ──────► 3 已完成
                    │             │                 │                │
                    │             └─────────────────┴────────────────┘
                    │                              │
                    │                       5 退款中 ──审核通过──► 6 已退款
                    │                          │      （payment 原路退回 + 库存回补）
                    │                    审核驳回
                    │                          │
                    │                          └──► 回到申请前的状态（1/2/3）
                    │
                    └──► 4 已取消
                   （支付超时 / 买家手动 / 商家整单取消 / 锁库存失败）
```

| 状态 | 含义 | 可转入 |
| ---- | ---- | ---- |
| 0 | 待付款 | 1（支付成功）、4（各类取消） |
| 1 | 待发货 | 2（卖家发货）、5（申请退款） |
| 2 | 待收货 | 3（确认收货）、5（申请退款） |
| 3 | 已完成 | 5（申请退款） |
| 4 | 已取消 | 终态 |
| 5 | 退款中 | 6（审核通过）、回退到申请前的状态（驳回） |
| 6 | 已退款 | 终态 |

---

## 正向链路：下单 → 锁库存 → 支付 → 发货

模拟高并发场景，下单与扣库存通过消息**异步解耦**；**订单只有在支付成功后才会进入「待发货」**：

```
客户端 --createOrder--> order 服务 --order.created--> RabbitMQ --消费--> inventory 服务
                                        ^                                |
                                        +--- deducted / deduct_failed --+（回执确认锁库存/失败）
客户端 --pay/create---> payment 服务 ----支付单/渠道收银台-----------+
    |                                                                  |
    +--(渠道回调/mock)-- payment 落 pay_order=成功 --pay.success--> order 0待付款->1待发货
```

### order 服务 `OrderServiceImpl.createOrder`（`@Transactional`）

1. 循环订单明细，Feign 同步拉取商品快照（价格 / 名称 / 主图），校验上架状态并计算总金额；
2. 同一本地事务写 `orders`（状态 0 待付款，`useGeneratedKeys` 回填主键）+ `order_item` 明细；
3. **同一事务内**把 `order.created` 写入 `outbox` 表（并同时入箱一条支付超时延迟标记），
   由 relay 定时投递——既避免下游在订单未落库时就消费，也避免订单落库了事件却丢失。

### inventory 服务 `OrderCreatedListener` 消费 `order.created`

1. **幂等**：以 `inventory_log` 的 `(order_id, product_id, change_type)` 唯一键为准，
   该订单已有 `change_type=3` 锁定流水即跳过，防同一订单被扣两次；
2. 逐商品加 **Redisson 分布式锁 `lock:stock:{productId}`**（按商品 id 升序取，避免死锁）；
3. 条件 `UPDATE inventory SET locked_stock+?, available_stock-? WHERE available_stock>=?`
   （DB 条件保证不超卖，下单即预占库存）；
4. 写库存流水 `inventory_log`（`change_type=3` 下单锁定）。

**回执**：全部锁定成功 → 发 `inventory.deducted`；任一商品不足/失败 → 回补已锁定库存并发
`inventory.deduct_failed`。

### order 服务 `OrderResultListener` 消费回执

- `deducted`：仅确认「库存已锁定」，订单**保持 0 待付款**等待支付；
- `deduct_failed`：把订单 `0 → 4 已取消`。

两者都带 `order_status=0` 条件，天然防重。

### payment 服务支付

1. 买家 `POST /pay/create`（走网关登录态）→ Feign 复用 `GET /order/findDetailOrder?id=`
   校验归属 + 待付款 → 幂等建 `pay_order`（`pay_no` 即渠道 `out_trade_no`）→ 调渠道
   （支付宝电脑网站 / 微信 Native）返回收银台参数；
2. 渠道异步回调（或测试钩子 `POST /pay/mock/success`）→ payment 验签 → 事务内幂等把
   `pay_order` 置 `payment_status=1` 并落 `payment_record` → **同一事务内把 `pay.success`
   写入 `outbox`**，由 relay 投递；
3. **order 服务 `PaySuccessListener` 消费 `pay.success`** → `markPaid`：`0 待付款 → 1 待发货`
   （`order_status=0` 条件，天然防重）。

### 发货 → 确认收货 → 完成

支付成功后订单停在 `1待发货`，随后的发货/收货/完成由 **order 服务本地状态机**推进
（不涉及库存/支付，无需额外 MQ 事件）：

```
支付成功(pay.success) markPaid              0待付款 → 1待发货  （同事务后冻结 receiver_* 收货快照）
卖家对自有商品发货（新增 shipping 发货单）   1待发货 → 2待收货  （最后一卖触发整单翻转）
买家确认收货                                 2待收货 → 3已完成  （complete_time 落值）
```

- **收货快照**：支付成功（`handlePaid`）后即以 `orders.address_id` 从 user 库冻结
  `receiver_name/phone/address`（跨库直读，本仓已有同款先例），供卖家发货前预览与面单；
  地址已失效/缺失时尽力兜底默认地址，实在无地址则留给发货时再补一次。
- **混单拆分发货**：`orders` 一行只承载整单状态，但一单可含多个卖家商品，故新增 `shipping` 表
  （`uk_order_seller(order_id, seller_id)`，每卖家每单一条）记录各卖家各自的发货单
  （物流公司/单号/发货时间）。整单 `2待收货` 由「该单已发货卖家数 == 该单卖家总数」判定，
  最后一个卖家发货时条件翻转 `1 → 2`。
- **卖家发货** `POST /order/seller/ship`（orderId + 可选物流信息）：校验登录身份确有该单商品后写发货单。
  事务内**第一条语句对订单行 `select ... for update`**——串行化同一订单的多卖家并发发货，
  避免 RR 隔离级别下两个「最后一卖」互相读不到对方而把订单卡死在待发货；重复发货幂等
  （已存在发货单直接返回）。
- **买家确认收货** `POST /order/receive?id=`：仅本人且订单处于 `2待收货` 时条件更新到 `3已完成`
  （同样先锁行再判定，与「最后一卖发货」并发安全）。

---

## 取消：三个入口

除支付超时自动取消外，待付款订单还支持买家手动取消与商家整单取消。三者在业务事务内都把
`order.canceled` 写入 `outbox`（由 relay 投递）→ inventory 释放锁定库存、payment 关闭未付支付单。

| 入口 | 接口 | 约束 |
| ---- | ---- | ---- |
| 支付超时 | （无接口，延迟消息触发） | `order.pay-timeout-minutes` 默认 30 分钟；另每 5 分钟扫表对账兜底 |
| 买家手动 | `POST /order/cancel?id` | 仅能取消**本人**且处于**待付款**的订单 |
| 商家整单 | `POST /order/seller/cancel?id` | 订单须含自己的商品且**不含他人商品**（混单不可整单取消），仅待付款可取消 |
| 锁库存失败 | （无接口，回执触发） | `inventory.deduct_failed` → 0→4 |

- 买家取消用 `user_id AND order_status=0` 条件更新，与支付并发天然互斥，谁先提交谁生效。
- 商家查看订单 `GET /order/seller/orders` 返回含自己商品的订单及本人那份明细，
  并标记 `cancellable`（待付款 **且** 不含其它卖家商品）——前端据此决定按钮是否可点。

超时取消的机制细节（延迟消息 + 对账兜底）见 [events.md](events.md#支付超时延迟消息)。

---

## 退款逆向分支

正向链路走到 `3已完成` 之后没有回头路，退款补上了这条逆向分支。状态位与表结构在早期就预留好了
（`orders.order_status` 5/6、`payment.refund` 表、`inventory_log.change_type=6`），后来把它们接上：

```
1待发货 ─┐
2待收货 ─┼─ 买家申请退款 ──> 5退款中 ── 卖家/管理员审核通过 ──> payment 原路退回 ──> 6已退款（+ 库存回补）
3已完成 ─┘                     │
                               └─ 审核驳回 ──> 回到申请前的状态
```

### 归属划分

退款状态机的主人是 **order**（`order_refund` 表记「申请-审核」），payment 只按指令办事
（`refund` 表记「钱退出去没有」）。两张表通过 `refund_no` 对齐，`refund_no` 由 order 侧生成。

两张表的 `refund_status` 语义不同，故**不共用枚举**：order 用 `RefundAuditStatus`
（0待审核/1退款中/2已退款/3已驳回），payment 仍为三态（0退款中/1成功/2失败）。

### 申请

`POST /order/refund/apply`（整单全额）：条件更新 `order_status in (1,2,3) -> 5`。
越权拦截、可退状态校验、并发重复申请防重三件事全由这一条 `WHERE` 兜住；
同事务写 `order_refund`(待审核) 并发 `refund.request(APPLY)` 让 payment 建退款单
（**此时不动钱**）。

### 审核

卖家经 `POST /order/seller/refund/audit`，管理员经 `POST /order/admin/refund/audit`。
卖家只能审「整单商品都属于自己」的申请，混单（多卖家）只有管理员能审——与「商家整单取消」同规矩。

- 通过 → `order_refund` 置退款中并发 `refund.request(APPROVE)`；
- 驳回 → 置已驳回、**订单回退到申请前状态**、发 `refund.request(REJECT)`。

**驳回后的状态回退不需要额外记「申请前状态」**：申请退款不覆盖 `shipping_status`，
而 `shipping_status`（0未发货/1已发货/2已收货）与可申请退款的三态一一对应，
故 `OrderMapper.revertRefunding` 直接用 `case shipping_status` 反推
（未发货→待发货、已发货→待收货、已收货→已完成），`shipping_time` / `complete_time` 保持原值不清空。

### 打款

payment 调渠道原路退回（支付宝 `AlipayTradeRefund` / 微信 APIv3 `RefundService.create`），
`refund_no` 同时作为渠道的 `out_request_no` / `out_refund_no`，**渠道按它幂等**——
这是「渠道失败就重试」策略成立的前提。成功后在**同一事务**内落
`refund`(成功) + `pay_order`(已退款 2) + `pay.refund.success` 入 outbox。

**渠道失败的策略是抛异常重试而非置失败**：此时订单还停在 `5退款中`，把退款单置失败会造成
「订单说退款中、退款单说失败」的永久不一致且无人修正。抛出后由有界重试兜瞬时故障，
耗尽落 `q.pay.dlq` 等人工介入。

### 库存回补

order 消费 `pay.refund.success` 置 `6已退款`，同事务发 `order.refunded`；
inventory 把该订单占用的库存从 `locked_stock` 拨回 `available_stock`，写 `change_type=6`（退货入库）流水。

账务动作与「取消释放」相同，区别只在流水类型——而 `change_type` 正是幂等键
（`inventory_log` 唯一键 `order_id+product_id+change_type`），两条链路各自幂等、互不干扰。

> **演示路径**：渠道商户参数是占位值，退款走 `payment.mock.enabled=true` 时模拟打款成功，
> 与 `/pay/mock/success` 同一开关。见 [operations.md](operations.md#支付为真实-sdk-结构--占位配置)。

## 相关文档

- MQ 事件、outbox、延迟消息与 DLQ： [events.md](events.md)
- 订单相关接口全表： [api.md](api.md#order)
- 退款与并发的已知边界： [operations.md](operations.md#关键设计与已知边界)
