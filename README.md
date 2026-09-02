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

### 服务与端口

| 服务 | 端口 | 说明 |
| ---- | ---- | ---- |
| mall-gateway | 9999 | 统一入口，路由 /user /product /order /inventory |
| mall-service-user | 9000 | 注册登录 / 资料 / 收货地址 / 商家一键上架 |
| mall-service-product | 8000 | 商品与分类 |
| mall-service-order | 6000 | 下单 |
| mall-service-inventory | 5000 | 库存锁定/扣减/补货 |
| mall-service-payment | 7000 | 支付（建支付单/渠道下单/异步回调/模拟支付） |

> 下游业务服务自身不做 JWT 鉴权，只信任网关注入的 `X-User-Id` / `X-Username`（网关会先剥离入站同名伪造头再注入）；user 服务因承担登录签发，另保留一层本地 `LoginInterceptor` 二次校验。

## 主要业务逻辑

### 1. 登录与鉴权链路（单设备登录）

1. 客户端经网关 `POST /user/login`（白名单，不校验 token）登录。
2. user 服务校验 BCrypt 密码 → 签发 JWT（内含 `claims.id / claims.username`，有效期 1h）→ 写入 Redis `login:token:{id}`（TTL 1h）。
3. 网关 `AuthGlobalFilter` 对白名单外的请求统一鉴权：
   - 解析 JWT → 校验 Redis 中 token 与当前一致（支持主动失效 / 单设备踢下线）；
   - 通过后剥离入站伪造的 `X-User-Id/X-Username`，再注入真实身份头下发给下游。
4. 下游 product/order/inventory 通过 `mall-common.IdentityInterceptor` 把身份头写入 `ThreadLocal`，controller/service 读取当前用户；user 服务由 `LoginInterceptor` 直接解析 JWT + 校验 Redis。
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

### 3. RabbitMQ 拓扑（常量统一在 `mall-common.RabbitTopology`）

| 元素 | 名称 | 作用 |
| ---- | ---- | ---- |
| TopicExchange | `mall.order.exchange` | 下单/支付/取消事件总线（durable，order/inventory/payment 三端共用） |
| 路由键 | `order.created` | order 发布，inventory 订阅 |
| 路由键 | `order.canceled` | order 发布（支付超时自动取消），inventory 释放锁定 / payment 关闭未付支付单 |
| 路由键 | `inventory.deducted` / `inventory.deduct_failed` | inventory 回执，order 订阅 |
| 路由键 | `pay.success` | payment 发布，order 订阅（支付成功：0 待付款 → 1 待发货） |
| Queue | `q.inventory.order.created` | 库存侧消费下单事件 |
| Queue | `q.inventory.order.canceled` | 库存侧消费订单取消事件（释放锁定库存） |
| Queue | `q.pay.order.canceled` | 支付侧消费订单取消事件（关闭未付支付单） |
| Queue | `q.order.deducted` / `q.order.deduct.failed` | 订单侧消费扣减回执 |
| Queue | `q.order.pay.success` | 订单侧消费支付成功回执 |

### 4. 商家上架商品 → 初始化库存（user → product → inventory 三段）

`POST /userToAddProduct`（user 服务，供商家端调用）：

1. 取登录态 userId 写入商品（归属以登录态为准）；
2. 调 product 服务 `/addNumProduct` 插入商品，**主键由 DB 自增回填并随响应返回**；
3. 用回填的 `product.id` 调 inventory 服务 `/addNumInventory` 初始化一条 0 库存记录；
4. 任一段失败即抛业务异常并中止，避免出现「商品建好了、库存却没建」的脏状态。

### 5. 商品与分类

- **商家管理（归属校验均带 `user_id`）**：上架 `/addNumProduct`（`uk_user_name(user_id,name)` 防重复上架）、部分更新 `/updateProduct`、上下架 `/shelfProduct`。
- **买家浏览**：`/list` 关键词模糊 + 分类筛选 + 白名单排序（`price_asc/price_desc/newest`）+ 分页，只展示在售商品。
- **分类**：支持多级分类；`/category/tree` 在内存中递归拼树并做了防环保护，管理接口会校验父分类存在、禁止把自己挂到自己下、同级同名拦截。

### 6. 库存补货与流水

商家给自有商品补货 `/restock`：先按 `product_id+user_id` 校验归属，再条件 `UPDATE` 增加 `total_stock/available_stock`，并写一条 `inventory_log`（`change_type=1` 入库）。所有库存变动都落流水，带变动前后快照，便于对账。

### 7. 收货地址

`user` 服务提供地址增删改查；删除 / 修改 / 详情均带 `id AND user_id` 归属条件，防止越权操作他人地址。

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
| /product | GET /list | 买家分页浏览（关键词/分类/排序） |
| /product | GET /findProductById?id= | 按 id 查商品（供下单快照） |
| /product | GET /findProductByUserId · /findProductByUserName | 按卖家查商品 |
| /product | POST /addNumProduct · PUT /updateProduct · PUT /shelfProduct | 商家商品管理 |
| /product | GET /category/list · /category/tree | 分类浏览 |
| /product | POST /category/add · PUT /category/update | 分类管理 |
| /order | POST /createOrder | 下单（发 order.created 事件） |
| /order | GET /findAllOrder · GET /findDetailOrder?id= | 查我的订单 |
| /inventory | POST /addNumInventory · POST /restock?productId&qty | 初始化库存 / 补货 |
| /pay | POST /create | 创建支付单并返回渠道收银台参数（支付宝表单/微信 code_url） |
| /pay | POST /mock/success | 模拟支付成功（测试钩子，需配置 payment.mock.enabled=true） |
| /pay | POST /alipay/notify · POST /wx/notify | 微信/支付宝异步回调（网关白名单，无登录态） |

> 网关按 `/user/**` `/product/**` `/order/**` `/inventory/**` `/pay/**` 前缀路由并 `StripPrefix=1`，上表路径是各服务 StripPrefix 之后的本服务路径。

## 快速启动

前置依赖：JDK 21、Maven、MySQL、Redis、RabbitMQ、Nacos。

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

- **超卖防护**：扣库存不依赖分布式锁的互斥，而是「条件 UPDATE（`available_stock>=qty`）」在数据库层保证原子，Redisson 锁用于串行化同一商品的竞争、降低无效 UPDATE。
- **MQ 幂等**：当前以 Redis `SETNX` 对订单号打标实现 at-least-once 下的去重（TTL 24h）。简单直观，但存在「处理中崩溃 → 重投被跳过而订单悬挂」的窗口；生产建议补充：手动 ack + 死信队列、扣库存与流水同事务、或 outbox/对账补偿。
- **主键类型**：表主键/外键为 `BIGINT`，Java 侧实体与身份信息统一使用 `Long`。
- **错误提示**：业务失败抛 `BusinessException`，由 `model.GlobalExceptionHandler` 统一转为 `Result.error(友好文案)`，避免向前端泄露 SQL 等内部信息。
- **支付为真实 SDK 结构 + 占位配置**：`mall-service-payment` 已引入支付宝（`alipay-sdk-java`）与微信（`wechatpay-java` APIv3）官方 SDK 结构，但商户号/AppID/证书密钥当前为**占位值**（见 payment `application.yml` 的 `payment.*` 段），故渠道回调收不到；本地演示请置 `payment.mock.enabled=true` 后调 `POST /pay/mock/success` 模拟支付成功（走与真实回调相同的幂等落库与 `pay.success` 事件）。
- **支付超时自动取消**：order 服务定时任务（默认每 60s）扫描超过 `order.pay-timeout-minutes`（默认 30 分钟）未支付的待付款订单并取消（0 → 4），发布 `order.canceled`：inventory 侧释放该订单锁定库存（写 `inventory_log` change_type=4）、payment 侧关闭该订单未付支付单。取消与支付同为 `order_status=0` 条件更新，谁先提交谁生效，不会误发货。
- **已知未完成**：退款/售后链路、真正可用的分布式事务（Seata 依赖已移除，`@GlobalTransactional` 仅演示用后已清理）尚未实现。另取消落库与 `order.canceled` 事件发布非原子（发布失败时订单已取消但库存未释放，仅靠下一次扫描补 canceltime 不会重发事件，需 outbox/补偿），演示可接受。
