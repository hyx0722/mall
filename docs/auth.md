# 登录与鉴权

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 前置阅读：[architecture.md](architecture.md)（网关路由与白名单）
> 相关：[api.md](api.md)（管理员接口全表）

## 登录链路（单设备登录）

1. 客户端经网关 `POST /user/login`（白名单，不校验 token）登录。
2. user 服务校验 BCrypt 密码（`status=0` 的禁用账号直接拒绝登录）→ 签发 JWT
   （内含 `claims.id / claims.username / claims.role`，有效期 1h）→ 写入 Redis `login:token:{id}`（TTL 1h）。
3. 网关 `AuthGlobalFilter` 对白名单外的请求统一鉴权：
   - 解析 JWT → 校验 Redis 中 token 与当前一致（支持主动失效 / 单设备踢下线）；
   - 通过后**先剥离入站伪造的** `X-User-Id/X-Username/X-User-Role`，再注入真实身份头下发给下游。
4. 下游 product/order/inventory 通过 `mall-common.IdentityInterceptor` 把身份头（含 `role`）
   写入 `ThreadLocal`，controller/service 用 `mall-common.Auths` 读取当前用户/角色并做
   `requireLogin()` / `requireAdmin()` 断言；user 服务由 `LoginInterceptor` 直接解析 JWT + 校验 Redis。
5. 改密 / 删号（以及 Redis 1h TTL 到期）会使 `login:token:{id}` 失效，旧 token 立即不可用。

### JWT 密钥

`mall-gateway` 与 `mall-service-user` 需要**同一个** JWT 签名密钥，它**不再硬编码在仓库里**，
由环境变量 `JWT_SECRET` 注入（`jwt.secret: ${JWT_SECRET:}`）。两端 `JwtUtil` 的构造函数都做同一套校验：

- 未配置或全空白 → 抛出 `IllegalStateException`，**服务启动直接失败**；
- 长度不足 32 字符 → 同样启动失败（HMAC256 强度取决于密钥长度）。

即只有这两个服务需要 `JWT_SECRET`，其余服务不读它。生成方式见
[getting-started.md](getting-started.md#6-设置-jwt_secret)。

### ThreadLocal 的清理约定

`IdentityInterceptor` 把身份写入 `ThreadLocal` 后，**必须在请求结束（`afterCompletion`）清除**。
漏掉 `remove()` 时，线程池复用会让下一个请求继承上一个请求的身份，表现为随机、极难复现的越权。
`mall-common` 的 `IdentityContextTest` 专门守这条不变量，见 [operations.md](operations.md#测试)。

## 角色模型

系统区分两类角色（`user.role`，注册默认为普通用户）：

| 值 | 角色 | 说明 |
| ---- | ---- | ---- |
| 1 | 普通用户 | 注册即得；下单、收货地址；作为商家可上架商品、补货、管理店铺订单 |
| 2 | 管理员 | 内部后台账号；可跨用户/商品/订单/库存做管理操作，可对任意商品上/下架 |

角色贯穿鉴权链路：

1. 登录时 user 服务把 `role` 写入 JWT `claims`（**旧 token 无 role 一律按普通用户处理**，向下兼容）；
2. 网关 `AuthGlobalFilter` 剥离入站伪造的 `X-User-Role` 头并注入真实值；
3. 下游各服务经 `mall-common.Auths` 读取身份做 `requireLogin()` / `requireAdmin()`，角色不符抛业务异常
   （`X-User-Role` 也随 Feign 透传）。

## 管理员接口的规则

接口路径全表见 [api.md](api.md#管理员接口)。这里只讲**规则**——这些是接口表里看不出来的约束：

- **均需 `role=2`**，由 `Auths.requireAdmin()` 断言；网关侧只负责把真实角色头注入，不做管理员判断。
- **不允许改自己**（`PUT /admin/updateUser` 防自锁）：管理员把自己降级或禁用会立刻失去后台入口。
- **禁用即下线**：把用户 `status` 置 0 后，该账号此后登录被拒（提示"该账号已被禁用，请联系管理员"），
  已登录会话因 Redis token 被删而立即失效。
- **重置密码同理会强制下线**（`PATCH /admin/resetPwd`）。
- **退款审核的归属限制**：卖家只能审「整单商品都属于自己」的退款申请，**混单（多卖家）只有管理员能审**——
  与「商家整单取消」同规矩。细节见 [order-lifecycle.md](order-lifecycle.md#退款逆向分支)。

### 管理员初始化

user 服务启动时（`UserStartupSetup`）会自动：

1. 为旧库 `user` 表补齐 `role` 列（存量库升级用）；
2. 在库中**不存在管理员**（`role=2`）时，按 `mall.admin.username/password`
   （默认 `admin/admin123`，见 user 服务 `application.yml`）自动创建管理员账号。

> ⚠️ 默认口令 `admin/admin123` 是为本地演示准备的，**部署到任何非本地环境前必须改掉**。

## 相关文档

- 网关路由与白名单： [architecture.md](architecture.md#网关路由与鉴权边界)
- 管理员接口全表： [api.md](api.md#管理员接口)
- 测试如何守住 ThreadLocal 清理： [operations.md](operations.md#测试)
