# 系统架构

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 相关：[getting-started.md](getting-started.md)（跑起来）· [api.md](api.md)（接口）· [auth.md](auth.md)（鉴权）

## 技术栈（全量版本）

版本统一定义在根 `pom.xml` 的 `<properties>`，子模块不再各自写死。

| 类别 | 选型 | 版本 |
| ---- | ---- | ---- |
| 语言 / 构建 | Java、Maven（多模块） | Java 21 |
| 基础框架 | Spring Boot | `4.0.0` |
| 微服务框架 | Spring Cloud | `2025.1.0` |
| 微服务框架 | Spring Cloud Alibaba | `2025.1.0.0` |
| 注册 / 配置中心 | Nacos | 随 SCA BOM（客户端 3.1.1） |
| 网关 | Spring Cloud Gateway（WebFlux 版） | 随 SC BOM |
| ORM | MyBatis-Plus | `3.5.17` |
| 连接池 | Druid | `1.2.28` |
| 存储 / 缓存 | MySQL、Redis | — |
| 消息队列 | RabbitMQ | — |
| 鉴权 | JWT（`java-jwt`）+ Redis 登录态 | `4.6.0` |
| 分布式锁 | Redisson | `4.7.0` |
| 集成测试 | Testcontainers | `2.0.5` |

> 注意几个 Boot 4 时代的坑：Boot 4 的 BOM **不再托管** `org.testcontainers:*`（需自行 import BOM）；
> Testcontainers 2.x **改了构件坐标**（`testcontainers-mysql` / `testcontainers-junit-jupiter`，旧短坐标已不存在）；
> MyBatis-Plus 3.5.17 把 `MybatisSqlSessionFactoryBean` 挪到了 `com.baomidou.mybatisplus.spring`。

## 模块结构

```
mall
├── model                      # 契约模块：实体 bean、统一返回 Result、BusinessException、
│                              #   全局异常处理、领域事件契约、状态枚举、线程上下文工具
├── mall-common                # 运行时共享模块：身份拦截器、Auths 断言、Feign 公共配置、
│                              #   Rabbit 拓扑常量、outbox 指标（业务服务 @Import 复用）
├── mall-gateway               # 网关：路由转发 + JWT 鉴权 + 注入用户身份头
├── mall-service               # 业务服务聚合模块
│   ├── mall-service-user      # 用户 / 收货地址 / 商家上架商品入口
│   ├── mall-service-product   # 商品 / 分类 / 购物车（商家管理 + 买家浏览）
│   ├── mall-service-order     # 订单（下单主流程 + 取消 / 发货 / 收货 / 退款）
│   ├── mall-service-inventory # 库存（MQ 消费扣减 + 补货 + 流水）
│   └── mall-service-payment   # 支付（微信 / 支付宝）
└── pom.xml                    # 父工程（依赖版本统一管理）
```

> 仓库根目录另含两个 **Vue 前端工程**（不参与 Maven 构建，不在根 pom 的 `<modules>` 里）：
> `mall-web`（买家 / 卖家端）与 `mall-admin`（管理后台），见下面「前端工程」。

### `model` 与 `mall-common` 的分工

两者都叫「公共模块」，但职责不同，混用会破坏依赖边界：

| 模块 | 内容 | 依赖约束 |
| ---- | ---- | ---- |
| `model` | `bean/`（实体、`PageBean`、`Result`）、`enums/`（`OrderStatus`、`RefundAuditStatus`）、`event/`（**跨服务事件契约**）、`exception/BusinessException`、`web/GlobalExceptionHandler`、`util/ThreadLocalUtil` | 只依赖 `spring-web` + `slf4j-api`。**刻意不引 servlet-api**——凡继承 `jakarta.servlet.*` 的异常（如 `MissingServletRequestParameterException`）都不能塞进这里的共享 advice，否则编译不过 |
| `mall-common` | `web/IdentityInterceptor`、`web/Auths`、`web/CommonWebConfig`、`feign/CommonFeignConfig`、`feign/FeignIdentityInterceptor`、`rabbit/RabbitTopology`、`metrics/OutboxMetrics` | 面向 servlet/WebMVC 的业务服务。**网关不依赖它**（网关是 WebFlux，两者技术栈不通） |

> 依赖方向：业务服务 → `mall-common` → `model`。事件契约放 `model` 是为了让发布方与消费方共享同一个类。

业务服务（`mall-service/pom.xml`）统一继承：`model`、`mall-common`、`spring-boot-starter-web`、actuator、
`mysql-connector-j`、`druid-spring-boot-4-starter`、`spring-boot-starter-data-redis`、
`redisson-spring-boot-starter`、`spring-security-crypto`、Nacos discovery + config、OpenFeign、
loadbalancer、Sentinel。
其中 **order / inventory / payment** 额外加 `spring-boot-starter-amqp`；**payment** 另加
`alipay-sdk-java:4.40.630.ALL` 与 `wechatpay-java:0.2.17`。

## 服务与端口

| 服务 | 端口 | 说明 |
| ---- | ---- | ---- |
| mall-gateway | 9999 | 统一入口，路由 `/user` `/product` `/order` `/inventory` `/pay` |
| mall-service-user | 9000 | 注册登录 / 资料 / 收货地址 / 商家一键上架 |
| mall-service-product | 8000 | 商品、分类、购物车 |
| mall-service-order | 6000 | 下单 / 取消 / 发货 / 收货 / 退款 |
| mall-service-inventory | 5000 | 库存锁定 / 扣减 / 补货 |
| mall-service-payment | 9005 | 支付（建支付单 / 渠道下单 / 异步回调 / 模拟支付） |

### 端口从哪来（**这一节能省你半小时**）

上表是**实际生效的端口**。它和仓库里 `application.yml` 写的值**不一致**，原因是两层配置叠加：

1. 各服务本地 `src/main/resources/application.yml` 里写的是 **9001 / 9002 / 9003 / 9004 / 9005**；
2. 但每个服务的 `spring.config.import` 会去 Nacos 拉 `nacos:common.yaml?group=mall-service-<name>`
   （namespace `dev`），而 Nacos 里那份 `common.yaml` 只做一件事——**用 `server.port` 覆盖掉本地端口**。
   Nacos 配置的优先级高于本地 `application.yml`，所以最终生效的是第 1 节列的那些值。

**payment 是唯一的例外**：它 Nacos 里那条键被写成了 `spring.port` 而不是 `server.port`。
`spring.port` 不是 Spring Boot 认得的配置键，等于没写，于是 payment 回落到本地的 **9005**。

> 所以**光看仓库里的 yml 推不出运行端口**，要以 Nacos `common.yaml` 为准。
> 排查端口对不上时，先去 Nacos 控制台（namespace `dev`）看对应 group 的 `common.yaml`。

### 网关配置的例外

`mall-gateway` **不读 Nacos 配置**——它没有 `spring.config.import`，也没有数据源
（显式排除了 `DataSourceAutoConfiguration` 与 `DataSourceTransactionManagerAutoConfiguration`，
因为父 pom 全局引入了 MyBatis-Plus starter，连带 JDBC 自动配置）。
网关的 Nacos 客户端**只用于服务发现 / 注册**。

网关的全部配置来自它自己的 `application.yml`：

- `server.port: 9999`
- Nacos `server-addr: localhost:8848`（仅 discovery）
- 静态路由（见下）
- `jwt.secret: ${JWT_SECRET:}`（从环境变量注入）
- `spring.data.redis`（校验登录态用）
- actuator 暴露 `health,info,metrics`

> Spring Cloud Gateway 5.x 的静态路由前缀是 `spring.cloud.gateway.server.webflux.routes`，
> 旧的 `spring.cloud.gateway.routes` **已不再识别**。

## 网关路由与鉴权边界

路由按前缀转发，全部带 `StripPrefix=1`（即网关看到 `/user/login`，转发给 user 服务的是 `/login`）：

| 网关前缀 | 目标服务 | 路由 id |
| ---- | ---- | ---- |
| `/user/**` | `lb://mall-service-user` | mall-user |
| `/product/**` | `lb://mall-service-product` | mall-product |
| `/order/**` | `lb://mall-service-order` | mall-order |
| `/inventory/**` | `lb://mall-service-inventory` | mall-inventory |
| `/pay/**` | `lb://mall-service-payment` | mall-payment |

> **没有 `/admin/**` 路由**。管理员接口是各服务内部的 `/admin/*` 路径，对外表现为
> `/user/admin/**`、`/product/admin/**`、`/order/admin/**`、`/inventory/admin/**`。

**鉴权白名单**（仅这 4 条，其余一律要求携带 token）：

```
/user/login
/user/register
/pay/alipay/notify
/pay/wx/notify
```

> 下游业务服务自身**不做 JWT 鉴权**，只信任网关注入的身份头
> （网关会先剥离入站同名伪造头再注入真实值）。这意味着**服务端口不能直接暴露到公网**。
> user 服务因承担登录签发，另保留一层本地 `LoginInterceptor` 二次校验。
> 详细链路见 [auth.md](auth.md)。

## 前端工程

仓库根目录含两个独立的 Vue 3 + Vite 工程，都经 Vite dev proxy 转发到网关，不改写路径：

| 工程 | 开发端口 | 代理前缀 | 说明 |
| ---- | ---- | ---- | ---- |
| `mall-web` | **5173** | `/user` `/product` `/order` `/inventory` `/pay` | 买家 / 卖家端，见 [mall-web/README.md](../mall-web/README.md) |
| `mall-admin` | **5174** | `/user` `/product` `/order` `/inventory` | 管理后台，见 [mall-admin/README.md](../mall-admin/README.md) |

- 两个工程的后端地址都由 `vite.config.js` 顶部的 `VITE_GATEWAY` 决定（默认 `http://localhost:9999`），
  可用同名环境变量覆盖。
- `mall-admin` 只代理 4 个业务前缀——管理后台不涉及支付页，无需 `/pay`。
- 两者的 `dist/` 构建产物与 `node_modules/` 均被各自工程的 `.gitignore` 忽略，不入库。

## 相关文档

- 跑起来： [getting-started.md](getting-started.md)
- 接口全表： [api.md](api.md)
- 鉴权细节： [auth.md](auth.md)
