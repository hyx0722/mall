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
| POST `/refund/apply` · GET `/refund/detail?orderId` · GET `/refund/list` | 买家申请退款 / 查看退款进度 / 我的退款单 |
| GET `/seller/refunds` · POST `/seller/refund/audit` | 商家查看待审退款 / 审核（仅整单属于自己的订单） |

## /inventory

库存服务：mall-service-inventory

| 方法与路径 | 说明 |
| ---- | ---- |
| POST `/addNumInventory` | 初始化库存（供商家上架三段式调用） |
| POST `/restock?productId&qty` | 补货 |
| POST `/updateInventory` | 库存更新（遗留接口） |

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

> 管理员账号的初始化方式（默认 `admin/admin123`）与各接口的规则约束见 [auth.md](auth.md#管理员接口的规则)。

## 相关文档

- 鉴权链路与角色： [auth.md](auth.md)
- 状态机与各接口的前置状态： [order-lifecycle.md](order-lifecycle.md)
