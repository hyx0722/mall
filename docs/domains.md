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

## 商品评价

在 **order 服务**（`/order/review/*`），表 `product_review` 在 `mall_service_order` 库。

**为什么放 order 服务**：「这个人买过这个商品吗」只有 order 库能答。
放这里，资格校验与写入是同一条 SQL，没有 check-then-insert 窗口，也不新增任何依赖。
若放 product 服务，每次写评价都要 Feign 问 order，而 order 已依赖 product，会双向依赖。

### 资格规则与它防的坑

| 规则 | 实现 |
| ---- | ---- |
| 只有**已完成**（`order_status=3`）的订单能评价 | `insertEligibleReview` 的 `WHERE o.order_status = 3` |
| 只能评**自己**的订单 | `WHERE o.user_id = #{userId}`（登录态，非参数） |
| 只能评订单里**确实有**的商品 | `join order_item … and oi.product_id = #{productId}` |
| **每订单每商品一条**（买两次可评两次） | `UNIQUE uk_order_product(order_id, product_id)` |
| 商家只能回复**自己商品**的评价、只能一次 | `WHERE id=? and seller_id=? and reply_content is null` |

判定全部做进 SQL，**受影响行数 0 即不具备资格**——沿用本仓「条件 UPDATE + 受影响行数」
的既有惯例，只是搬到了 `INSERT … SELECT` 上。任何一条不满足都得到同样的 0 行，
也因此**不应该**为了让错误文案更精确而在前面加校验查询：那会把判定写两遍，并把窗口放回来。

> ⚠️ `INSERT … SELECT` 里有一句 `and oi.id = (select min(oi2.id) …)` 看着多余，**但不能删**。
> `order_item` 上没有 `UNIQUE(order_id, product_id)`，而 `createOrder` 逐条插入、不去重 productId——
> 客户端传 `[{productId:7,qty:1},{productId:7,qty:2}]` 就能造出同商品的两行明细。
> 少了这个条件会 join 出 2 行、撞唯一键、触发 **InnoDB 语句级回滚（0 行 + 异常）**，
> 结果是**这个买家永远评不了这个商品**，还报「系统繁忙」。详见 `ProductReviewMapper`。

### 通知

评价写入/回复成功后，与业务**同一事务**经 outbox 投递 `review.created` / `review.replied`，
user 服务消费后写站内通知（类型 10 / 11）。
`ref_id` 必须是 **reviewId**——理由见 [events.md](events.md#站内通知的消费user-服务)。

### 刻意的非目标

- **退款不回收评价**：订单在评价后仍可走到 5退款中/6已退款，但评价保留。评价是历史事实，
  且 5→3（退款被驳回）是合法转换，按状态实时过滤会让评价「消失又出现」。
- **不阻止卖家买自己商品后评价**。
- **不做评价排序**（只有 `order by id desc`）、不做追评。

### 商品列表的星级

`/product/list` 用两个**相关子查询**带出 `avg_rating` / `review_count`
（不是 `left join … group by`——那会废掉索引分页，且只在主键分组时合法）。
无评价时 `avgRating` 是 **NULL 而非 0**，前端整块不渲染，否则「没人评过」会被显示成「0 分」。
这是 product 服务唯一一处跨库读 order 库，代价是 order 库不存在时商品浏览会 500。

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
- 评价资格如何防住重复明细行： [ProductReviewMapper](../mall-service/mall-service-order/src/main/java/com/order/mapper/ProductReviewMapper.java)
