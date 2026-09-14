# mall-web —— mall 微服务商城前端演示页

Vue3 + Vite + Element Plus 实现的轻量前端，对接 `mall` 后端（Spring Cloud 微服务商城）。

## 功能

- **账户**：注册 / 登录（经网关，JWT 存 localStorage）、改资料 / 头像 / 密码、注销
- **商品浏览**：关键字搜索、分类筛选、排序、分页、商品详情、店铺页
- **购物车**：增删改清空、勾选结算（下架商品保留但不可结算）
- **交易**：结算下单 → 支付（支付宝表单 / 微信 code_url，演示环境走模拟支付）→ 我的订单 / 订单详情 / 取消 / 确认收货 / 物流查看
- **退款**：买家申请退款、查看退款进度
- **商家中心**：发布 / 编辑 / 上下架商品、补货、店铺订单、发货、退款审核
- **收货地址**：增删改查

## 运行

```bash
npm install
npm run dev      # http://localhost:5173
```

前提：`mall` 后端全链路已启动（Nacos / MySQL / Redis / RabbitMQ + 各业务服务 + 网关 9999）。

请求通过 Vite dev proxy 转发到网关 `http://localhost:9999`，无跨域问题。
后端地址如需修改：改 `vite.config.js` 顶部的 `VITE_GATEWAY`，或用环境变量覆盖。

## 目录

```
src/
├── api/        # axios 封装（统一注入 Authorization、解包 Result）+ 按域拆分的接口
│               #   address/auth/cart/inventory/order/pay/product/user/request
├── stores/     # 登录态（token/username）响应式 + localStorage 持久化
├── router/     # /login /register 公开，其余全部 requiresAuth（路由守卫）
├── views/      # 14 个页面：登录注册、商品列表/详情/店铺、购物车、结算、支付、
│               #   我的订单/订单详情、个人中心、地址管理、
│               #   商家中心/商家订单（发货与退款审核）
└── components/ # ProductCard 商品卡片
```

> 卖家页面（`/seller`、`/seller/orders`）在前端只做「已登录」校验，**归属与角色由后端接口把关**
> （商家只能操作自己名下的商品与订单）。
