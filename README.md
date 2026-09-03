# mall —— 基于 Spring Cloud 的微服务商城

一个用于学习与演示的电商后端项目。基于 Spring Cloud 微服务架构，采用 Nacos 注册/配置中心、Spring Cloud Gateway 网关、MyBatis-Plus 操作 MySQL，Redis 做缓存与登录态，RabbitMQ 解耦「下单 → 扣库存」链路，可在简单的模拟高并发下单场景下运行。

## 技术栈

| 类别 | 选型 |
| ---- | ---- |
| 语言 / 构建 | Java 21、Maven（多模块） |
| 微服务框架 | Spring Cloud `2025.1.0`、Spring Cloud Alibaba `2025.1.0.0` |
| 基础框架 | Spring Boot `4.0.0` |
| 注册 / 配置中心 | Nacos |
| 网关 | Spring Cloud Gateway |
| ORM | MyBatis-Plus `3.5.17` |
| 存储 / 缓存 | MySQL、Redis |
| 消息队列 | RabbitMQ |
| 鉴权 | JWT（网关统一校验）+ Redis 登录态（单设备登录） |
| 分布式锁 / 布隆等 | Redisson |

## 模块结构

```
mall
├── model                      # 公共模块：实体 bean、统一返回 Result、全局异常、事件、工具类
├── mall-common                # 公共模块：身份拦截器、Feign 公共配置、Rabbit 拓扑常量（业务服务 @Import 复用）
├── mall-gateway               # 网关：路由转发 + JWT 鉴权 + 注入用户身份头
├── mall-service               # 业务服务聚合模块
│   ├── mall-service-user      # 用户 / 收货地址 / 商家上架商品入口
│   ├── mall-service-product   # 商品 / 分类（商家管理 + 买家浏览）
│   ├── mall-service-order     # 订单（下单主流程）
│   ├── mall-service-inventory # 库存（MQ 消费扣减 + 补货 + 流水）
│   └── mall-service-payment   # 支付（微信/支付宝）
└── pom.xml                    # 父工程（依赖版本统一管理）
```

> 仓库根目录另含两个 **Vue 前端工程**（不参与 Maven 构建）：`mall-web`（买家/卖家端）与 `mall-admin`（管理后台），详见 §10。

### 服务与端口

| 服务 | 端口 | 说明 |
| ---- | ---- | ---- |
| mall-gateway | 9999 | 统一入口，路由 /user /product /order /inventory /pay |
| mall-service-user | 9001 | 注册登录 / 资料 / 收货地址 / 商家一键上架 |
| mall-service-product | 9002 | 商品与分类 |
| mall-service-order | 9003 | 下单 |
| mall-service-inventory | 9004 | 库存锁定/扣减/补货 |
| mall-service-payment | 9005 | 支付（建支付单/渠道下单/异步回调/模拟支付） |

> 下游业务服务自身不做 JWT 鉴权，只信任网关注入的 `X-User-Id` / `X-Username` / `X-User-Role`（网关会先剥离入站同名伪造头再注入）；user 服务因承担登录签发，另保留一层本地 `LoginInterceptor` 二次校验。下游统一用 `mall-common.Auths` 读取当前用户/角色（`requireLogin()` / `requireAdmin()` 断言），详见 §8。

## 主要业务逻辑

### 1. 登录与鉴权链路（单设备登录）

1. 客户端经网关 `POST /user/login`（白名单，不校验 token）登录。
2. user 服务校验 BCrypt 密码（`status=0` 的禁用账号直接拒绝登录）→ 签发 JWT（内含 `claims.id / claims.username / claims.role`，有效期 1h）→ 写入 Redis `login:token:{id}`（TTL 1h）。
3. 网关 `AuthGlobalFilter` 对白名单外的请求统一鉴权：
   - 解析 JWT → 校验 Redis 中 token 与当前一致（支持主动失效 / 单设备踢下线）；
   - 通过后剥离入站伪造的 `X-User-Id/X-Username/X-User-Role`，再注入真实身份头下发给下游。
4. 下游 product/order/inventory 通过 `mall-common.IdentityInterceptor` 把身份头（含 `role`）写入 `ThreadLocal`，controller/service 用 `mall-common.Auths` 读取当前用户/角色并做 `requireLogin()` / `requireAdmin()` 断言；user 服务由 `LoginInterceptor` 直接解析 JWT + 校验 Redis。
5. 改密 / 删号（以及 Redis 1h TTL 到期）会使 `login:token:{id}` 失效，旧 token 立即不可用。

### 2. 下单 → 锁库存 → 支付 → 发货 主链路（RabbitMQ 解耦「订单 ↔ 库存 ↔ 支付」）

模拟高并发场景，下单与扣库存通过消息异步解耦；**订单只有在支付成功后才会进入「待发货」**：

```
客户端 --createOrder--> order 服务 --order.created--> RabbitMQ --消费--> inventory 服务
                                        ^                                |
                                        +--- deducted / deduct_failed --+（回执确认锁库存/失败）
客户端 --pay/create---> payment 服务 ----支付单/渠道收银台-----------+
    |                                                                  |
    +--(渠道回调/mock)-- payment 落 pay_order=成功 --pay.success--> order 0待付款->1待发货
```

- **order 服务 `OrderServiceImpl.createOrder`（`@Transactional`）**
  1. 循环订单明细，Feign 同步拉取商品快照（价格 / 名称 / 主图），校验上架状态并计算总金额；
  2. 同一本地事务写 `orders`（状态 0 待付款，`useGeneratedKeys` 回填主键）+ `order_item` 明细；
  3. **事务提交后（`afterCommit`）**才向 `mall.order.exchange` 发布 `order.created`，避免下游在订单未落库时就消费。
- **inventory 服务 `OrderCreatedListener` 消费 `order.created`**
  1. 幂等：对 `orderNo` 执行 **Redis `SETNX dedup:order:{orderNo}`（TTL 24h）**，重复投递直接跳过，防同一订单被扣两次；
  2. 逐商品加 **Redisson 分布式锁 `lock:stock:{productId}`**；
  3. 条件 `UPDATE inventory SET locked_stock+?, available_stock-? WHERE available_stock>=?`（DB 条件保证不超卖，下单即预占库存）；
  4. 写库存流水 `inventory_log`（`change_type=3` 下单锁定）。
- **回执**：全部锁定成功 → 发 `inventory.deducted`；任一商品不足/失败 → 回补已锁定库存并发 `inventory.deduct_failed`。
- **order 服务 `OrderResultListener` 消费回执**：`deducted` 仅确认「库存已锁定」，订单**保持 0 待付款**等待支付；`deduct_failed` 把订单 `0 → 4 已取消`（都带 `order_status=0` 条件，天然防重）。
- **payment 服务支付**
  1. 买家 `POST /pay/create`（走网关登录态）→ Feign 复用 `GET /order/findDetailOrder?id=` 校验归属 + 待付款 → 幂等建 `pay_order`（`pay_no` 即渠道 `out_trade_no`）→ 调渠道（支付宝电脑网站 / 微信 Native）返回收银台参数；
  2. 渠道异步回调（或测试钩子 `POST /pay/mock/success`）→ payment 验签 → 事务内幂等把 `pay_order` 置 `payment_status=1` 并落 `payment_record` → **事务提交后（`afterCommit`）发布 `pay.success`**；
  3. **order 服务 `PaySuccessListener` 消费 `pay.success`** → `markPaid`：`0 待付款 → 1 待发货`（`order_status=0` 条件，天然防重）。

### 2.5 发货 → 确认收货 → 完成（订单状态机下半段）

支付成功后订单停在 `1待发货`，随后的发货/收货/完成由 **order 服务本地状态机**推进（不涉及库存/支付，无需额外 MQ 事件），状态推进沿用「条件 UPDATE + 受影响行数」防重惯例：

```
支付成功(pay.success) markPaid              0待付款 → 1待发货  （同事务后冻结 receiver_* 收货快照）
卖家对自有商品发货（新增 shipping 发货单）   1待发货 → 2待收货  （最后一卖触发整单翻转）
买家确认收货                                 2待收货 → 3已完成  （complete_time 落值）
```

- **收货快照**：支付成功（`handlePaid`）后即以 `orders.address_id` 从 user 库冻结 `receiver_name/phone/address`（跨库直读，本仓已有同款先例），供卖家发货前预览与面单；地址已失效/缺失时尽力兜底默认地址，实在无地址则留给发货时再补一次。
- **混单拆分发货**：`orders` 一行只承载整单状态，但一单可含多个卖家商品，故新增 `shipping` 表（`uk_order_seller(order_id, seller_id)`，每卖家每单一条）记录各卖家各自的发货单（物流公司/单号/发货时间）。整单 `2待收货` 由「该单已发货卖家数 == 该单卖家总数」判定，最后一个卖家发货时条件翻转 `1 → 2`。
- **卖家发货** `POST /order/seller/ship`（orderId + 可选物流信息）：校验登录身份确有该单商品后写发货单。事务内**第一条语句对订单行 `select ... for update`**——串行化同一订单的多卖家并发发货，避免 RR 隔离级别下两个「最后一卖」互相读不到对方而把订单卡死在待发货；重复发货幂等（已存在发货单直接返回）。
- **买家确认收货** `POST /order/receive?id=`：仅本人且订单处于 `2待收货` 时条件更新到 `3已完成`（同样先锁行再判定，与「最后一卖发货」并发安全）。

### 3. RabbitMQ 拓扑（常量统一在 `mall-common.RabbitTopology`）

| 元素 | 名称 | 作用 |
| ---- | ---- | ---- |
| TopicExchange | `mall.order.exchange` | 下单/支付/取消事件总线（durable，order/inventory/payment 三端共用） |
| 路由键 | `order.created` | order 发布，inventory 订阅 |
| 路由键 | `order.canceled` | order 发布（支付超时 / 买家手动 / 商家整单取消），inventory 释放锁定 / payment 关闭未付支付单 |
| 路由键 | `inventory.deducted` / `inventory.deduct_failed` | inventory 回执，order 订阅 |
| 路由键 | `pay.success` | payment 发布，order 订阅（支付成功：0 待付款 → 1 待发货） |
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

#### 3.1 事件可靠性设计（事务 outbox / 延迟消息 / DLQ）

- **事务 outbox**：order 的 `order.created / order.canceled`、payment 的 `pay.success` 不再用
  `TransactionSynchronization.afterCommit` 或「事务外立即发」，而是与业务状态变更**同一本地事务**写入
  `outbox` 表（order/payment 库各一张），由各自 `@Scheduled(3s)` 的 relay 领取（`for update skip locked`）
  并投递，成功后置 `status=1`。根治「订单已取消/已支付但事件没发出去」的非原子窗口。
- **支付超时延迟消息**：下单事务内同时入箱一条「超时标记」，带 `delay_ms`（= 支付超时阈值）；
  relay 发送时设 per-message `expiration` 发到延迟交换机 → 无消费者持有队列 → TTL 到点死信回主交换机
  `order.timeout` → order 消费并走 `OrderCancelService.cancelByOrderNo` 统一取消漏斗（条件 0→4 +
  同事务 outbox 发 `order.canceled`）。原 60s 定时扫表降频为 5 分钟**对账兜底**（防延迟消息丢失）。
- **有界重试 + DLQ**：order/inventory/payment 各自声明 `rabbitListenerContainerFactory`（`maxRetries(2)` +
  `RejectAndDontRequeueRecoverer`），瞬时异常重试 3 次后 `basicReject(requeue=false)` 落入本服务 DLQ，
  不再无限 requeue。
- **库存扣减消费**：移除「先 Redis SETNX 打标」；改为按商品 id 升序取 Redisson 锁后，在**单个 DB 事务**
  内完成「条件扣库存 + 写 change_type=3 流水」，任一商品不足整单回滚；幂等以 `inventory_log`
  （`order_id, product_id, change_type` 唯一键）为准——重投会跳过已锁商品并重发回执，
  消除「处理中崩溃 → 重投被挡 → 订单悬挂」窗口。取消消费同理。

### 4. 商家上架商品 → 初始化库存（user → product → inventory 三段）

`POST /userToAddProduct`（user 服务，供商家端调用）：

1. 取登录态 userId 写入商品（归属以登录态为准）；
2. 调 product 服务 `/addNumProduct` 插入商品，**主键由 DB 自增回填并随响应返回**；
3. 用回填的 `product.id` 调 inventory 服务 `/addNumInventory` 初始化一条 0 库存记录；
4. 任一段失败即抛业务异常并中止，避免出现「商品建好了、库存却没建」的脏状态。

> 归属只取登录态，请求体（`PublishProductRequest`）不含 `id / userId`；商品一经创建即处于上架状态（`status=1`，发布即上架）。

### 5. 商品与分类

- **商家管理（归属校验均带 `user_id`）**：上架 `/addNumProduct`（`uk_user_name(user_id,name)` 防重复上架）、部分更新 `/updateProduct`、上下架 `/shelfProduct`。
- **买家浏览**：`/list` 关键词模糊 + 分类筛选 + 白名单排序（`price_asc/price_desc/newest`）+ 分页，只展示在售商品。
- **分类**：支持多级分类；`/category/tree` 在内存中递归拼树并做了防环保护，管理接口会校验父分类存在、禁止把自己挂到自己下、同级同名拦截。新增/修改分类（`/category/add`、`/category/update`）已收紧为**仅管理员**可操作（`Auths.requireAdmin()`），买家浏览不受影响。

### 6. 库存补货与流水

商家给自有商品补货 `/restock`：先按 `product_id+user_id` 校验归属，再条件 `UPDATE` 增加 `total_stock/available_stock`，并写一条 `inventory_log`（`change_type=1` 入库）。所有库存变动都落流水，带变动前后快照，便于对账。

### 7. 收货地址

`user` 服务提供地址增删改查；删除 / 修改 / 详情均带 `id AND user_id` 归属条件，防止越权操作他人地址。

### 8. 用户角色与后台管理

系统区分两类角色（`user.role`，注册默认为普通用户）：

| 值 | 角色 | 说明 |
| ---- | ---- | ---- |
| 1 | 普通用户 | 注册即得；下单、收货地址；作为商家可上架商品、补货、管理店铺订单 |
| 2 | 管理员 | 内部后台账号；可跨用户/商品/订单/库存做管理操作，可对任意商品上/下架 |

角色贯穿鉴权链路：

1. 登录时 user 服务把 `role` 写入 JWT `claims`（**旧 token 无 role 一律按普通用户处理**，向下兼容）；
2. 网关 `AuthGlobalFilter` 剥离入站伪造的 `X-User-Role` 头并注入真实值；
3. 下游各服务经 `mall-common.Auths` 读取身份做 `requireLogin()` / `requireAdmin()`，角色不符抛业务异常（`X-User-Role` 也随 Feign 透传）。

**管理员接口**（均需 `role=2`，网关前缀后路径）：

| 服务(前缀) | 接口 | 说明 |
| ---- | ---- | ---- |
| /user | `GET /admin/listUsers?page&size&keyword` | 分页查用户（用户名/邮箱/手机号过滤） |
| /user | `PUT /admin/updateUser` | 改状态/角色/邮箱/手机号；禁用或降级即删其 Redis token 强制下线；不允许改自己（防自锁） |
| /user | `PATCH /admin/resetPwd` | 重置密码并使其下线 |
| /product | `GET /admin/listAll?page&size&keyword` | 查看全部商品（含下架，联表带卖家名） |
| /product | `PUT /admin/shelf?id&status` | 对任意商品上/下架 |
| /order | `GET /admin/findAllOrder?status` | 按订单状态查全部订单 |
| /order | `GET /admin/findDetailOrder?id` · `GET /admin/findOrderItems?orderId` | 订单详情 / 明细（联表带买家名） |
| /inventory | `GET /admin/listAll?productId` | 查库存（联表带商品名/卖家名） |

> **账号禁用**：管理员把用户 `status` 置 0 即禁用，该账号此后登录被拒（"该账号已被禁用，请联系管理员"），已登录会话因 token 被删而立即失效。
>
> **管理员初始化**：user 服务启动时自动为旧库 `user` 表补齐 `role` 列，并在库中不存在管理员（`role=2`）时按 `mall.admin.username/password`（默认 `admin/admin123`，见 user 服务 `application.yml`）自动创建管理员账号。

### 9. 订单取消：买家手动 / 商家整单

除支付超时自动取消外，待付款订单还支持以下取消入口，三者统一发布 `order.canceled`（有事务时 `afterCommit` 后再发）→ inventory 释放锁定库存、payment 关闭未付支付单：

- **买家取消** `POST /order/cancel?id`：仅能取消**本人**且处于**待付款**的订单；`user_id AND order_status=0` 条件更新，与支付并发天然互斥，谁先提交谁生效。
- **商家查看** `GET /order/seller/orders`：返回含自己商品的订单及本人那份明细，并标记 `cancellable`（待付款 **且** 不含其它卖家商品）。
- **商家整单取消** `POST /order/seller/cancel?id`：订单须含自己的商品且**不含他人商品**（混单不可整单取消），仅待付款可取消。

### 10. 前端工程（mall-web / mall-admin）

仓库根目录另含两个 Vue 前端工程（不参与 Maven 构建），开发时均经 Vite 代理到网关 9999：

- `mall-web`：买家/卖家端 —— 注册登录、商品浏览、下单/支付、我的订单、商家中心（店铺商品 / 卖家订单）与收货地址管理等；
- `mall-admin`：管理后台 —— 用户管理、商品管理、订单管理、库存查询、分类管理（对接各服务 `/admin/*` 接口，鉴权要求管理员角色）。

## 接口速览

| 服务(网关前缀) | 方法与路径 | 说明 |
| ---- | ---- | ---- |
| /user | POST /login | 登录，返回 token |
| /user | POST /register | 注册 |
| /user | GET /userInfo | 当前用户资料 |
| /user | PUT /update · PATCH /updateAvatar · PATCH /updatePwd | 更新资料/头像/密码 |
| /user | DELETE /delete | 注销当前用户 |
| /user | POST /addReceiverDetail · POST /addUserAddress | 新增收货地址 |
| /user | POST /updateUserAddressById?id= · DELETE /deleteUserAddress?id= | 改/删地址 |
| /user | GET /selectUserAddress · GET /selectUserDetailAddress?id= | 查地址 |
| /user | POST /userToAddProduct | 商家一键上架商品并初始化库存 |
| /user | GET /admin/listUsers?page&size&keyword · PUT /admin/updateUser · PATCH /admin/resetPwd | 后台用户管理（仅管理员） |
| /product | GET /list | 买家分页浏览（关键词/分类/排序） |
| /product | GET /findProductById?id= | 按 id 查商品（供下单快照） |
| /product | GET /findProductByUserId · /findProductByUserName | 按卖家查商品 |
| /product | POST /addNumProduct · PUT /updateProduct · PUT /shelfProduct | 商家商品管理 |
| /product | GET /admin/listAll?page&size&keyword · PUT /admin/shelf?id&status | 后台商品管理（仅管理员） |
| /product | GET /category/list · /category/tree | 分类浏览 |
| /product | POST /category/add · PUT /category/update | 分类管理（仅管理员） |
| /order | POST /createOrder | 下单（发 order.created 事件） |
| /order | GET /findAllOrder · GET /findDetailOrder?id= | 查我的订单 |
| /order | POST /cancel?id | 买家手动取消本人待付款订单 |
| /order | GET /seller/orders · POST /seller/cancel?id | 商家查看 / 整单取消含自己商品的订单 |
| /order | POST /seller/ship | 商家发货（自有商品所属订单，写 shipping 发货单；最后一卖后整单 1→2） |
| /order | POST /receive?id | 买家确认收货（待收货 → 已完成） |
| /order | GET /shippings?orderId | 买家查看订单物流发货单列表 |
| /order | GET /admin/findAllOrder?status · GET /admin/findDetailOrder?id · GET /admin/findOrderItems?orderId | 后台订单查询（仅管理员） |
| /inventory | POST /addNumInventory · POST /restock?productId&qty | 初始化库存 / 补货 |
| /inventory | GET /admin/listAll?productId | 后台库存查询（仅管理员） |
| /pay | POST /create | 创建支付单并返回渠道收银台参数（支付宝表单/微信 code_url） |
| /pay | POST /mock/success | 模拟支付成功（测试钩子，需配置 payment.mock.enabled=true） |
| /pay | POST /alipay/notify · POST /wx/notify | 微信/支付宝异步回调（网关白名单，无登录态） |

> 网关按 `/user/**` `/product/**` `/order/**` `/inventory/**` `/pay/**` 前缀路由并 `StripPrefix=1`，上表路径是各服务 StripPrefix 之后的本服务路径。

## 快速启动

前置依赖：JDK 21、Maven、MySQL、Redis、RabbitMQ、Nacos。

> **自「事件可靠性加固」起的存量迁移**：
> - order/payment 库需手动补 `outbox` 建表 DDL（见 `order.sql` / `payment.sql` 尾部）；inventory 库需执行
>   `ALTER TABLE inventory_log ADD UNIQUE KEY uk_order_product_type (order_id, product_id, change_type)`
>   （若历史数据有同订单同商品同类型重复流水，先清理再执行）。
> - RabbitMQ 侧新增延迟交换机/持有队列/死信交换机/各 DLQ，且原入站队列现在带
>   `x-dead-letter-exchange=mall.order.dlx` 参数——**已存在的同名旧队列与旧参数不符会导致 406
>   PRECONDITION_FAILED**，本地演示建议清空 Rabbit 数据或换一个 vhost 后重启各服务（队列由各服务 RabbitConfig 自动声明）。

1. **初始化数据库**：新建各业务库，执行对应模块 `src/main/resources` 下的建表脚本（如 `User.sql`、`Product.sql`、`order.sql`、`inventory.sql`、`payment.sql`）。
2. **启动 Nacos** 并准备配置：各服务 `application.yml` 通过 `spring.config.import` 拉取 `nacos:common.yaml` / `nacos:datasource.yaml`（namespace `dev`、group 为服务名）。仓库内 `application-datasource.yml` 仅作本地参考兜底，实际数据源以 Nacos 配置为准。
3. **编译并安装公共模块**（各服务依赖 `model` 与 `mall-common`，改动后需先安装）：
   ```bash
   mvn clean install -DskipTests
   ```
4. **依次启动服务**（运行各模块 `*Application` 主类即可）：
   ```bash
   mvn spring-boot:run
   ```
   建议顺序：model → mall-common → 各业务服务 → mall-gateway。网关启动后从 `POST http://localhost:9999/user/login` 走完整流程。

> JWT 密钥、MySQL/Rabbit 地址见各模块 `application*.yml`。生产环境请通过环境变量注入密钥，避免硬编码入库。

## 关键设计与已知边界

- **超卖防护**：扣库存不依赖分布式锁的互斥，而是「条件 UPDATE（`available_stock>=qty`）」在数据库层保证原子，Redisson 锁用于串行化同一商品的竞争、降低无效 UPDATE（锁在 DB 事务之外按商品 id 升序先取好）。
- **事件可靠性（outbox / 延迟消息 / DLQ / DB 幂等）**：order/payment 的对外事件均走**事务 outbox**（与业务同库同事务入 `outbox` 表，relay 定时投递）；支付超时改用**延迟消息**（per-message TTL + 死信回主交换机）并保留低频对账兜底；order/inventory/payment 消费端统一**有界重试(3) + DLQ**，库存扣减改为「单事务扣库存+流水」并以 `inventory_log`（order_id, product_id, change_type 唯一键）做幂等（移除先 SETNX）。详见 §3.1。
- **主键类型**：表主键/外键为 `BIGINT`，Java 侧实体与身份信息统一使用 `Long`。
- **错误提示**：业务失败抛 `BusinessException`，由 `model.GlobalExceptionHandler` 统一转为 `Result.error(友好文案)`，避免向前端泄露 SQL 等内部信息。
- **支付为真实 SDK 结构 + 占位配置**：`mall-service-payment` 已引入支付宝（`alipay-sdk-java`）与微信（`wechatpay-java` APIv3）官方 SDK 结构，但商户号/AppID/证书密钥当前为**占位值**（见 payment `application.yml` 的 `payment.*` 段），故渠道回调收不到；本地演示请置 `payment.mock.enabled=true` 后调 `POST /pay/mock/success` 模拟支付成功（走与真实回调相同的幂等落库与 `pay.success` 事件）。
- **支付超时自动取消**：主路径为下单时入箱的超时延迟消息（`order.pay-timeout-minutes` 默认 30 分钟，per-message TTL 到点死信触发），order 消费后经统一取消漏斗条件 0→4 并同事务 outbox 发 `order.canceled`（inventory 释放锁定、payment 关闭未付支付单）；另保留每 5 分钟的对账扫表兜底，防延迟消息丢失/宕机窗口。取消与支付同为 `order_status=0` 条件更新，谁先提交谁生效。
- **发货/收货并发**：`/seller/ship` 与 `/receive` 事务内第一条语句对订单行 `select ... for update`，串行化同一订单的并发操作。下单时锁库存、支付、取消等已处理，故发货按「卖家是否已全部发货」聚合整单推进；混单（多卖家）必须各自都发货后整单才 `1 → 2待收货`，买家确认整单收货后 `→ 3已完成`。
- **已知未完成**：退款/售后链路（`refund` 表 / `order_status` 5退款中、6已退款 / `inventory_log` change_type=6 / `payment_status=2` 等均已预留但无代码路径）、真正可用的分布式事务（Seata 依赖已移除，`@GlobalTransactional` 仅演示用后已清理）尚未实现。发布侧发送未开 publisher-confirms（无法路由的消息静默丢失仍靠对账兜底）。
