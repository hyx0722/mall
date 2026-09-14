# mall-admin —— mall 微服务商城管理后台

Vue3 + Vite + Element Plus 实现的内部管理后台，对接 `mall` 后端（Spring Cloud 微服务商城）的
各服务 `/admin/*` 接口。

## 功能

- **登录**：走网关 `POST /user/login`，**要求管理员角色**（`role=2`）；非管理员登录后接口会拒绝
- **用户管理**：分页查用户、改状态/角色/邮箱/手机号、重置密码
- **商品管理**：查看全部商品（含已下架，带卖家名）、对任意商品上/下架
- **订单管理**：按状态查全部订单、订单详情与明细
- **退款审核**：查看退款申请、审核（**混单只有管理员能审**，卖家只能审整单属于自己的）
- **库存查询**：按商品查库存（带商品名/卖家名）
- **分类管理**：新增/修改多级分类

## 运行

```bash
npm install
npm run dev      # http://localhost:5174
```

前提：`mall` 后端全链路已启动（Nacos / MySQL / Redis / RabbitMQ + 各业务服务 + 网关 9999），
且库中已存在管理员账号（user 服务首次启动会自动创建，默认 `admin/admin123`）。

请求通过 Vite dev proxy 转发到网关 `http://localhost:9999`，无跨域问题。
后端地址如需修改：改 `vite.config.js` 顶部的 `VITE_GATEWAY`，或用环境变量覆盖。

> 只代理 `/user` `/product` `/order` `/inventory` 四个业务前缀——管理后台不涉及支付页，无需 `/pay`。

## 目录

```
src/
├── api/        # axios 封装（统一注入 Authorization、解包 Result）+ 按域拆分的接口
│               #   admin/auth/refund/request
├── stores/     # 登录态（token/username）响应式 + localStorage 持久化
├── router/     # /login 公开，其余全部需登录（路由守卫）；/ 重定向到 /users
├── views/      # 7 个页面：登录、用户管理、订单管理、退款审核、
│               #   商品管理、库存查询、分类管理
├── utils/      # format 格式化工具
└── components/ # RefCell 表格内引用单元格
```

路由：`/login`、`/users`、`/orders`、`/refunds`、`/products`、`/inventory`、`/category`
（`/` 重定向到 `/users`）。

> 前端只做「已登录」校验，**角色与管理权限由后端接口把关**（各 `/admin/*` 接口用
> `Auths.requireAdmin()` 断言）。详见仓库根 [docs/auth.md](../docs/auth.md)。
