# 接口速览

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 相关：[auth.md](auth.md)（鉴权与角色）· [architecture.md](architecture.md#网关路由与鉴权边界)（路由与白名单）

本文的路径是**各服务自身的路径**——网关按前缀路由并 `StripPrefix=1`，
所以网关侧访问时要在前面加上服务前缀，例如 user 服务的 `/login` 对应 `POST http://localhost:9999/user/login`。

| 网关前缀 | 目标服务 | StripPrefix 后 |
| ---- | ---- | ---- |
| `/user/**` | mall-service-user | 去掉 `/user` |
| `/product/**` | mall-service-product | 去掉 `/product` |
| `/order/**` | mall-service-order | 去掉 `/order` |
| `/inventory/**` | mall-service-inventory | 去掉 `/inventory` |
| `/pay/**` | mall-service-payment | 去掉 `/pay` |

**鉴权**：除 `/user/login`、`/user/register`、`/pay/alipay/notify`、`/pay/wx/notify` 四条白名单外，
一律要求 `Authorization: <裸 JWT>` 头。管理员接口另需 `role=2`，见 [管理员接口](#管理员接口)。

---

## /user

用户服务：mall-service-user

| 方法与路径 | 说明 |
| ---- | ---- |
| POST `/login` | 登录，返回 token |
| POST `/register` | 注册 |
| GET `/userInfo` | 当前用户资料 |
| GET `/ortherUser?username` | 按用户名查其他用户（路径拼写沿用源码原样） |
| PUT `/update` · PATCH `/updateAvatar` · PATCH `/updatePwd` | 更新资料 / 头像 / 密码 |
| DELETE `/delete` | 注销当前用户 |
| POST `/addReceiverDetail` · POST `/addUserAddress` | 新增收货地址 |
| POST `/updateUserAddressById?id=` · DELETE `/deleteUserAddress?id=` | 改 / 删地址 |
| GET `/selectUserAddress` · GET `/selectUserDetailAddress?id=` | 查地址 |
| POST `/userToAddProduct` | 商家一键上架商品并初始化库存 |
| GET `/coupon/center` | 券中心：**平台券**中启用、有效期内、尚有余量的（商家券不在此列，只在各自店铺页露出） |
| GET `/coupon/store?username` | 店铺页：某商家当前可领的券（按用户名定位商家） |
| POST `/coupon/receive?couponId` | 领券（并发抢券靠条件 UPDATE；每人每券限领 1 张靠唯一键） |
| GET `/coupon/mine?status` | 我持有的券（背包页数据源；`status` 不传即全部：0未使用 1已使用 2已过期） |
| POST `/coupon/usable` | 结算页可用券列表：一次评估本单所有未使用券的可用性与抵扣额（body 传商品快照明细） |
| GET `/seller/coupon/list` | **商家**：我发的券（含停用与已领完） |
| POST `/seller/coupon/create` | **商家**：给自家商品发券；`scopes` 只能是自己的商品，后端逐个校验归属 |
| PUT `/seller/coupon/status?couponId&status` | **商家**：启停自家的券（条件 UPDATE 带 `seller_id`，改不动别人的） |
| POST `/coupon/preview` | **服务间**：用券试算（body 传商品快照明细），只读 |
| POST `/coupon/use?userCouponId&orderId` | **服务间**：核销（下单事务内调用，失败即让下单回滚） |
| GET `/message/list?category&isRead&page&size` | 我的消息：`category` = `all`(默认)/`order`/`store`，`isRead` 不传即全部（0未读 1已读） |
| GET `/message/unreadCount` | 未读数（顶栏铃铛角标；前端轮询时**必须静默失败**，见 `api/request.js` 的 `_silent`） |
| PUT `/message/read?id` · PUT `/message/readAll` | 单条 / 全部标已读 |
| DELETE `/message/delete?id` | 删除单条消息 |
| GET `/store/status?username` | 店铺页：`{ username, subscribed, subscriberCount }` |
| POST `/store/subscribe?username` · POST `/store/unsubscribe?username` | 订阅 / 退订店铺（均幂等；不能订阅自己的店） |
| GET `/store/my` | 我订阅的商店用户名列表 |
| GET `/store/message/list?username&page&size` | 某店的店铺公告（**不要求订阅**，店铺页对所有人可见） |
| POST `/store/seller/message/create` | **商家**：发布公告（body `{content}`，≤500 字）；落库 + 群发给订阅者，同事务 |
| GET `/store/seller/message/list?page&size` | **商家**：我发过的公告 |
| DELETE `/store/seller/message/delete?id` | **商家**：删除自己的公告（已投递的通知不回收） |

> **消息类接口没有「创建」入口**：通知是业务事实的产物（订单推进、商店更新），
> 不是可以手工 POST 的资源。所有读写的 `userId` 一律取自登录态，
> 没有任何一个接口收 `userId` 参数。详见 [domains.md](domains.md#消息通知与商店订阅)。

> **`/store/*` 全部按 `username` 而不是 storeId**：店铺页路由是 `/store/:username`，
> 而 `/ortherUser` 只返回 username/avatar/status，前端拿不到卖家 id，由服务端解析。
> 与 `/coupon/store?username` 是同一套做法。

> 券的**定义与归属**都在 user 服务，但**核销**发生在 order 服务下单那一刻，靠 Feign 同步调用
> `/coupon/preview` 与 `/coupon/use`。可用性规则（门槛、有效期、指定商品/分类）只在 user 侧实现一份。
> 下单金额：`orders.total_amount` 记原价合计，`orders.discount_amount` 记抵扣，
> **实付 = 两者之差**（支付单的 `pay_amount` 取该差值）。

### 两类券：平台券 与 商家券

由 `coupon.seller_id` 区分，这个字段同时决定**在哪里能领到**：

| | 平台券（`seller_id=0`） | 商家券（`seller_id=商家`） |
| --- | --- | --- |
| 谁发 | 管理员，`/user/admin/coupon/*` | 商家，`/user/seller/coupon/*` |
| 在哪领 | 券中心 `/coupon/center` | 该商家的店铺页 `/coupon/store?username=` |
| 作用范围 | 全场 / 指定商品 / 指定分类 | **只能是商家自己的商品**（逐个向商品服务校验归属） |
| 怎么发 | 手填范围 ID | 从自己的商品列表勾选 |

> 商家券强制「指定商品」且归属校验失败即拒绝：分类是全平台共享的，放开等于让商家
> 用别人的商品成本给自己店铺引流。归属失败**绝不降级放行**——降级等于任何商家
> 都能给别人的商品发券。
>
> 券的持有与查看：买家领到的券在**背包**（`/backpack`，数据源 `GET /coupon/mine`）里按
> 可用 / 已使用 / 已过期分组展示。

> **退券没有接口**，只由 user 服务消费 `order.canceled` / `order.refunded` 触发。
> 若开成 HTTP 接口、券 id 由客户端给，买家就能「用券下单拿到折扣后立刻把券要回来」重复抵扣。

## /product

商品服务：mall-service-product

| 方法与路径 | 说明 |
| ---- | ---- |
| GET `/list` | 买家分页浏览（关键词 / 分类 / 排序） |
| GET `/findProductById?id=` | 按 id 查商品（供下单快照） |
| GET `/findProductByProductName` | 按商品名查 |
| GET `/findProductByUserId?start&size` | 商家查看自己发布的商品（含已下架），返回 `{ total, items }`，`start` 为页码 |
| GET `/findProductByUserName` | 按卖家用户名查其在售商品 |
| POST `/addNumProduct` · PUT `/updateProduct` · PUT `/shelfProduct` | 商家商品管理（上架 / 部分更新 / 上下架） |
| GET `/cart/list` · GET `/cart/count` | 购物车查看 / 角标数 |
| POST `/cart/add` · POST `/cart/update` · POST `/cart/remove` · POST `/cart/removeItems` · DELETE `/cart/clear` | 购物车增删改（`quantity<=0` 即移除） |
| GET `/category/list` · GET `/category/tree` | 分类浏览 |
| POST `/category/add` · PUT `/category/update` | 分类管理（仅管理员） |

## /order

订单服务：mall-service-order

| 方法与路径 | 说明 |
| ---- | ---- |
| POST `/createOrder` | 下单（发 `order.created` 事件） |
| GET `/findAllOrder` · GET `/findDetailOrder?id=` | 查我的订单 / 订单详情 |
| POST `/cancel?id` | 买家手动取消本人待付款订单 |
| GET `/seller/orders` · POST `/seller/cancel?id` | 商家查看 / 整单取消含自己商品的订单 |
| POST `/seller/ship` | 商家发货（自有商品所属订单，写 shipping 发货单；最后一卖后整单 1→2） |
| POST `/receive?id` | 买家确认收货（待收货 → 已完成） |
| GET `/shippings?orderId` | 买家查看订单物流发货单列表 |
| GET `/seller/settlement` | 卖家对账：订单完成后生成的结算明细与各状态汇总 |
| GET `/seller/withdraw` | 卖家提现：可提现余额（已扣申请中金额）+ 历史提现申请 |
| POST `/seller/withdraw/apply` | 卖家发起提现申请（body `{amount}`），审核在管理端 |
| POST `/refund/apply` · GET `/refund/detail?orderId` · GET `/refund/list` | 买家申请退款 / 查看退款进度 / 我的退款单 |
| GET `/seller/refunds` · POST `/seller/refund/audit` | 商家查看待审退款 / 审核（仅整单属于自己的订单） |
| GET `/review/list?productId&page&size` | 某商品的评价分页（公开） |
| GET `/review/stat?productId` | 某商品评价汇总：`avgRating`（**无评价时为 null**）/ `total` / 1-5 星分布 |
| GET `/review/detail?id` | 单条评价（公开）。站内通知的「商家回复」深链靠它换出 `productId` |
| GET `/review/mine?productId` | 我买过该商品、订单已完成、且尚未评价的订单列表（写评价弹框的订单选择器） |
| POST `/review/create` | 写评价（body `{orderId, productId, rating, content}`） |
| GET `/findOrderItems?orderId` | **买家**：订单明细 + 每行的 `canReview` / `reviewId`（含归属校验，非本人订单返回空数组） |
| GET `/seller/reviews?onlyUnreplied&page&size` | **商家**：我商品的评价分页 |
| POST `/seller/review/reply` | **商家**：回复评价（body `{reviewId, content}`），只能回复一次 |

> **评价的资格规则**：只有 `order_status=3`（已完成）的订单能评价，且**每个订单每个商品一条**
> （买两次可评两次）。判定被写进了 `insertEligibleReview` 的 `INSERT…SELECT` 本身——
> 受影响行数 0 即不具备资格，没有 check-then-insert 窗口。
> 详见 [domains.md](domains.md#商品评价)。

## /inventory

库存服务：mall-service-inventory

| 方法与路径 | 说明 |
| ---- | ---- |
| POST `/addNumInventory` | 初始化库存（供商家上架三段式调用） |
| POST `/restock?productId&qty` | 补货 |
| POST `/warnThreshold?productId&threshold` | 商家设置自有商品的库存预警阈值（0 表示关闭预警） |

> 曾有 `POST /updateInventory`（直写库存行，实为 INSERT），因无管理员/归属校验且不写 `inventory_log`、
> 破坏「所有库存变动都落流水」的不变量，已移除。库存只能经 `addNumInventory` / `restock` 变更。

## /pay

支付服务：mall-service-payment

| 方法与路径 | 说明 |
| ---- | ---- |
| POST `/create` | 创建支付单并返回渠道收银台参数（支付宝表单 / 微信 code_url） |
| POST `/mock/success` | 模拟支付成功（测试钩子，受 `payment.mock.enabled` 开关控制，仓库默认已置 `true`） |
| POST `/alipay/notify` · POST `/wx/notify` | 微信 / 支付宝异步回调（网关白名单，无登录态） |

---

## 管理员接口

均需 `role=2`（`Auths.requireAdmin()` 断言）。注意**网关没有 `/admin/**` 路由**——
管理员接口是各服务内部的 `/admin/*` 路径，对外表现为「服务前缀 + `/admin/…`」。

| 服务(前缀) | 接口 | 说明 |
| ---- | ---- | ---- |
| /user | GET `/admin/listUsers?page&size&keyword` | 分页查用户（用户名 / 邮箱 / 手机号过滤） |
| /user | PUT `/admin/updateUser` | 改状态 / 角色 / 邮箱 / 手机号；禁用或降级即删其 Redis token 强制下线；不允许改自己（防自锁） |
| /user | PATCH `/admin/resetPwd` | 重置密码并使其下线 |
| /product | GET `/admin/listAll?page&size&keyword` | 查看全部商品（含下架，联表带卖家名） |
| /product | PUT `/admin/shelf?id&status` | 对任意商品上 / 下架 |
| /order | GET `/admin/findAllOrder?status` | 按订单状态查全部订单 |
| /order | GET `/admin/findDetailOrder?id` · GET `/admin/findOrderItems?orderId` | 订单详情 / 明细（联表带买家名） |
| /order | GET `/admin/refunds?status` · POST `/admin/refund/audit` | 后台退款审核（混单只能由管理员审） |
| /inventory | GET `/admin/listAll?productId` | 查库存（联表带商品名 / 卖家名） |
| /user | GET `/admin/coupon/list?keyword` | 全量券（含停用与已领完） |
| /user | POST `/admin/coupon/create` | 建券（满减 / 折扣，可挂指定商品 / 分类范围） |
| /user | PUT `/admin/coupon/status?couponId&status` | 券上下架 |
| /order | GET `/admin/withdrawals` | 待审核的商家提现申请 |
| /order | POST `/admin/withdraw/audit` | 审核提现；通过即视为已打款并把对应结算明细置为已提现 |

> **商家结算与提现**：订单完成后生成 `settlement` 明细（待结算），账期 T+N
> （`order.settlement-delay-days`，默认 7 天）到点由 `SettlementTask` 转为**可提现**；
> 商家申请提现后，审核通过才把申请时刻之前的可提现明细置为**已提现**。
> 可提现余额 = Σ可提现明细 − Σ申请中的提现（先在余额里占住，防止重复申请）。

> 管理员账号的初始化方式（默认 `admin/admin123`）与各接口的规则约束见 [auth.md](auth.md#管理员接口的规则)。

## 相关文档

- 鉴权链路与角色： [auth.md](auth.md)
- 状态机与各接口的前置状态： [order-lifecycle.md](order-lifecycle.md)
