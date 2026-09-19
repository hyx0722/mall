# 业务域细节

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 相关：[api.md](api.md)（接口全表）· [architecture.md](architecture.md)（模块划分）

订单状态机与事件总线之外的那些业务域，集中在这里。

## 购物车（Redis Hash）

`cart:{userId}` Hash，field = `productId`，value = 数量——**只存最小事实**，
商品名称/价格/主图在读取时从 `product` 表批量补全。商家改价后购物车立刻反映新价，
不存在「车里存着过期价格」。

- **放在 product 服务**（`CartController`）：购物车展示必须补全商品信息，放商品服务可直接查库，
  省一次跨服务往返。
- 接口：`/cart/add`（累加）、`/cart/update`（覆盖，`<=0` 即移除）、`/cart/remove`、`/cart/clear`、
  `/cart/list`、`/cart/count`、`/cart/removeItems`。单件上限 999（体验护栏，真正的超卖防护在下单链路）。
- **下架/已删除的商品保留在车里**，但标记 `available=false` 且不可勾选结算，不会静默消失。
- 购物车**不需要建表**——它整个在 Redis 里。

### 结算复用下单接口

结算**没有新增下单接口**：前端把选中项编码进 `/checkout?items=1:2,3:1`，
复用现有的 `POST /order/createOrder`（它本来就收 `items` 列表）；
下单成功后前端再调 `/cart/removeItems` 清理已结算条目（尽力而为，失败不影响订单）。

## 商家上架商品 → 初始化库存（三段式）

`POST /userToAddProduct`（user 服务，供商家端调用）串起三个服务：

1. 取登录态 userId 写入商品（**归属以登录态为准**）；
2. 调 product 服务 `/addNumProduct` 插入商品，**主键由 DB 自增回填并随响应返回**；
3. 用回填的 `product.id` 调 inventory 服务 `/addNumInventory` 初始化一条 0 库存记录；
4. 任一段失败即抛业务异常并中止，避免出现「商品建好了、库存却没建」的脏状态。

> 归属只取登录态，请求体（`PublishProductRequest`）不含 `id / userId`；
> 商品一经创建即处于上架状态（`status=1`，发布即上架）。

## 商品与分类

- **商家管理（归属校验均带 `user_id`）**：上架 `/addNumProduct`
  （`uk_user_name(user_id,name)` 防重复上架）、部分更新 `/updateProduct`、上下架 `/shelfProduct`。
- **买家浏览**：`/list` 关键词模糊 + 分类筛选 + 白名单排序（`price_asc` / `price_desc` / `newest`）
  + 分页，只展示在售商品。
- **分类**：支持多级分类；`/category/tree` 在内存中递归拼树并做了**防环保护**，
  管理接口会校验父分类存在、禁止把自己挂到自己下、同级同名拦截。
  新增/修改分类（`/category/add`、`/category/update`）已收紧为**仅管理员**可操作
  （`Auths.requireAdmin()`），买家浏览不受影响。

## 库存补货与流水

商家给自有商品补货 `/restock`：先按 `product_id + user_id` 校验归属，
再条件 `UPDATE` 增加 `total_stock/available_stock`，并写一条 `inventory_log`（`change_type=1` 入库）。

**所有库存变动都落流水**，带变动前后快照，便于对账。`change_type` 取值
（见 `inventory.sql` 注释）：1入库 / 2出库 / 3锁定 / 4释放锁定 / 5扣减 / 6退货入库。

> 各变动类型的幂等语义见 [events.md](events.md#库存扣减消费的幂等)。

## 消息通知与商店订阅

都在 **user 服务**（`/message/*` 与 `/store/*`），复用 `mall_service_user` 库，
不新增微服务、端口或网关路由。

**「商店」在本仓没有独立实体**——商店就是卖家用户（商品靠 `product.user_id` 归属，
店铺页是 `/store/:username`），所以 `store_subscription.store_id` 指的是 `user.id`，
所有店铺接口也一律**收 username、服务端解析**（前端拿不到卖家 id）。

三张新表（`User.sql` 第 6/7/8 张，`UserStartupSetup` 会在启动时幂等补建）：

| 表 | 作用 |
| ---- | ---- |
| `store_subscription` | 订阅关系；`uk_user_store` 挡重复订阅 |
| `store_message` | 商家发的公告 |
| `notification` | 每个收件人一条消息；**只存 `store_id`，列表时批量补全店名** |

### 通知从哪来

| 触发 | 机制 | 类型 |
| ---- | ---- | ---- |
| 下单 / 支付 / 发货 / 完成 | user 消费 MQ 事件 | 1 / 2 / 3 / 4 |
| 取消 / 退款到账 | user **复用现有的退券队列**，在同一个监听方法里顺带写 | 5 / 6 |
| 商家发公告 | 落库后按订阅关系**一条 INSERT…SELECT** 群发 | 7 |
| 商家上新商品 | `userToAddProduct` 成功后群发（**尽力而为**，失败不连累上架） | 8 |
| 商家发店铺券 | `createForSeller` 后群发（**只在这里**，平台券不发） | 9 |

> 商店侧三条**不走 MQ**：订阅表、公告表、券表、商品发布入口本来就都在 user 服务内，
> 发事件等于自己发给自己。这也让 user 服务保持「只消费不发布」——没有 outbox 表。
> 代价是直接调 `POST /product/addNumProduct` 建的商品不会产生上新通知，
> 那是前端发布商品的唯一实际路径之外的旁路。

> 去重靠 `notification.uk_user_type_ref(user_id, type, ref_id)`。三处都是刻意的：
> 必须**含 `type`**（一张订单合法产生最多 6 条通知）、**含 `user_id`**（群发要写给 N 个人）、
> 且 `ref_id` / `ref_type` **不能为 NULL**（MySQL 唯一索引视多个 NULL 为互不相同，会静默失效）。

### 前端入口

顶栏最右侧的**圆形铃铛**（`App.vue`，带未读数角标，30s 轮询）→ `/messages`；
商家侧 `/seller/messages` 发公告。通知里的 `refType`/`refId` 由前端
映射成路由（`ORDER→/order/:id`、`PRODUCT→/product/:id`、`COUPON`/`STORE→/store/:username`），
后端不需要知道前端路由长什么样。

> 优惠券通知指向**店铺页**而不是 `/coupons`：商家券只在自家店铺页可领，券中心只列平台券，
> 指到券中心会让用户找不到那张券。

## 收货地址

`user` 服务提供地址增删改查；**删除 / 修改 / 详情均带 `id AND user_id` 归属条件**，
防止越权操作他人地址。

## 相关文档

- 接口路径与参数： [api.md](api.md)
- 上架后的下单链路： [order-lifecycle.md](order-lifecycle.md)
- 库存流水如何保证不超卖： [operations.md](operations.md#超卖防护)
- 通知消费的事件拓扑与幂等： [events.md](events.md#站内通知的消费user-服务)
