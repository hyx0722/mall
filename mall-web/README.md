# mall-web —— mall 微服务商城前端演示页

Vue3 + Vite + Element Plus 实现的轻量前端，对接 `mall` 后端（Spring Cloud 微服务商城）。

## 功能

- 注册 / 登录（经网关，JWT 存 localStorage）
- 商品浏览：关键字搜索、分类筛选、排序、分页

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
├── api/        # axios 封装（统一注入 Authorization、解包 Result）+ 接口
├── stores/     # 登录态（token/username）响应式 + localStorage 持久化
├── router/     # /login /register 公开，首页需登录（路由守卫）
├── views/      # Login / Register / ProductList
└── components/ # ProductCard 商品卡片
```
