# mall —— 基于 Spring Cloud 的微服务商城

一个用于学习与演示的电商后端项目。基于 Spring Cloud 微服务架构，采用 Nacos 注册/配置中心、
Spring Cloud Gateway 网关、MyBatis-Plus 操作 MySQL，Redis 做缓存与登录态，
RabbitMQ 解耦「下单 → 扣库存 → 支付」链路。

> 本文只讲**这是什么**和**怎么跑起来**。设计细节在 [`docs/`](docs/README.md)，见文末[文档地图](#文档地图)。

## 技术栈

| 类别 | 选型 |
| ---- | ---- |
| 语言 / 构建 | Java 21、Maven（多模块） |
| 基础框架 / 微服务 | Spring Boot `4.0.0`、Spring Cloud `2025.1.0`、Spring Cloud Alibaba `2025.1.0.0` |
| 注册 / 配置中心 | Nacos |
| 网关 | Spring Cloud Gateway |
| ORM / 连接池 | MyBatis-Plus `3.5.17`、Druid `1.2.28` |
| 存储 / 缓存 | MySQL、Redis |
| 消息队列 | RabbitMQ |
| 鉴权 | JWT（网关统一校验）+ Redis 登录态（单设备登录） |
| 分布式锁 | Redisson `4.7.0` |

全量版本与 Boot 4 时代的依赖坑见 [docs/architecture.md](docs/architecture.md#技术栈全量版本)。

## 模块结构

```
mall
├── model                      # 契约模块：实体、统一返回 Result、业务异常、领域事件契约、状态枚举
├── mall-common                # 运行时共享：身份拦截器、Auths 断言、Feign 配置、Rabbit 拓扑、
│                              #   事务 outbox 实现、outbox 指标
├── mall-gateway               # 网关：路由转发 + JWT 鉴权 + 注入用户身份头
├── mall-service               # 业务服务聚合模块
│   ├── mall-service-user      # 用户 / 收货地址 / 商家上架商品入口 / 优惠券
│   ├── mall-service-product   # 商品 / 分类 / 购物车
│   ├── mall-service-order     # 订单（下单 + 取消 / 发货 / 收货 / 退款）
│   ├── mall-service-inventory # 库存（MQ 消费扣减 + 补货 + 流水）
│   └── mall-service-payment   # 支付（微信 / 支付宝）
└── pom.xml                    # 父工程（依赖版本统一管理）
```

仓库根目录另含两个 **Vue 前端工程**（不参与 Maven 构建）：`mall-web`（买家/卖家端，dev 端口
**5173**）与 `mall-admin`（管理后台，dev 端口 **5174**），两者均经 Vite 代理到网关 9999。
详见各自的 README：[mall-web](mall-web/README.md) · [mall-admin](mall-admin/README.md)。

## 服务与端口

| 服务 | 端口 | 说明 |
| ---- | ---- | ---- |
| mall-gateway | 9999 | 统一入口，路由 `/user` `/product` `/order` `/inventory` `/pay` |
| mall-service-user | 9000 | 注册登录 / 资料 / 收货地址 / 商家一键上架 |
| mall-service-product | 8000 | 商品、分类、购物车 |
| mall-service-order | 6000 | 下单 / 取消 / 发货 / 收货 / 退款 |
| mall-service-inventory | 5000 | 库存锁定 / 扣减 / 补货 |
| mall-service-payment | 9005 | 支付（建支付单 / 渠道下单 / 异步回调 / 模拟支付） |

> ⚠️ 上表是**实际生效的端口**，和仓库里 `application.yml` 写的 **9001-9005 不一致**——
> 因为端口由 **Nacos 的 `common.yaml` 覆盖**（namespace `dev`、group `mall-service-<name>`）。
> 排查端口对不上时，**以 Nacos 为准**，不要看仓库里的 yml。
> 例外：payment 的 Nacos 配置把键名写成了 `spring.port`（无效键），故它回落到本地的 9005。
> 完整解析规则见 [docs/architecture.md](docs/architecture.md#端口从哪来这一节能省你半小时)。

> 下游业务服务自身**不做 JWT 鉴权**，只信任网关注入的身份头（网关会先剥离入站同名伪造头再注入）。
> 因此**服务端口不要暴露到公网**。详见 [docs/auth.md](docs/auth.md)。

## 快速启动

前置依赖：JDK 21、Maven、MySQL、Redis、RabbitMQ、Nacos。

### ⚠️ 第 0 步：在 Nacos 里手建配置（最容易卡住的地方）

**仓库里不含任何 Nacos 配置**——没有 `common.yaml`、也没有 `datasource.yaml`。
5 个业务服务各自通过 `spring.config.import` 从 Nacos 拉这两份配置
（namespace **`dev`**，group 为**服务名**），所以在手动创建它们之前，服务**启动不起来**：

- `common.yaml` — 至少要有 `server.port`，**它决定了服务的实际运行端口**；
- `datasource.yaml` — 数据库连接（**每个服务的 url 必须指向自己的库**）+ Redis 连接。

RabbitMQ 连接不在这里，而在 order/inventory/payment 的本地 `application.yml`，用 `RABBITMQ_*` 环境变量覆盖。
网关不读 Nacos 配置（只用它做服务发现）。

完整的键清单与可抄的模板见 [docs/getting-started.md](docs/getting-started.md#3-在-nacos-准备配置最容易卡住的一步)。

### 第 1 步：准备中间件（两种方式二选一）

**方式 A —— 本机已装好 MySQL / Redis / RabbitMQ / Nacos：直接用**，跳过本条。

**方式 B —— 用仓库根的 `docker-compose.yml` 拉起四个中间件**：

```bash
# ⚠️ 别盲目 cp：这会无条件覆盖已存在的 .env。已有配好的 .env 就直接编辑它。
cp .env.example .env
# 然后**必须**填 MYSQL_PASSWORD —— 留空 compose 会直接中止，容器只被 Created、不会启动
# 报错形如：required variable MYSQL_PASSWORD is missing a value
docker compose up -d
```

> compose 默认把**宿主机端口错开**（MySQL `3307`、Redis `6380`、RabbitMQ `5673`/`15673`、
> Nacos `8858`），以便与本机已装的中间件**并存而不抢端口**。要占标准端口，在 `.env` 里覆盖
> `MYSQL_PORT` 等变量（此时须先停掉本机对应中间件）。
> **注意**：业务服务是按标准端口连中间件的，所以端口错开时容器只是并存可用，
> 服务连的仍是本机那套。
>
> MySQL 首次启动会自动执行各模块建表脚本；`initdb` **只在数据卷为空时执行一次**，
> 改了 SQL 想重建先 `docker compose down -v`。

### 第 2~5 步

> 下面的命令**分 PowerShell 与 bash 两种写法**，别混用——`export` 是 bash 语法，
> 在 PowerShell 里会报「无法将"export"项识别为 cmdlet」。

**第 2 步：编译并安装公共模块**（各服务依赖 `model` 与 `mall-common`）

仓库自带 **Maven Wrapper**，**不依赖 `mvn` 是否在 PATH 上**，推荐用它：

```powershell
# PowerShell
.\mvnw.cmd clean install -DskipTests
```

```bash
# bash
./mvnw clean install -DskipTests
```

> 首次执行会按 `.mvn/wrapper/maven-wrapper.properties` 指定的版本（Maven 3.9.16）
> 自动下载到 `~/.m2/wrapper/dists/`，之后走本地缓存。
>
> 想用本机装的 Maven 也行（`mvn clean install -DskipTests`），但**要求 `mvn` 在 PATH 上**。
> 报「无法将"mvn"项识别为 cmdlet、函数、脚本文件或可运行程序」时：
>
> - 临时用完整路径：`& "E:\javase\apache-maven-3.9.16\bin\mvn.cmd" clean install -DskipTests`
> - 或修 PATH 那条坏项。**常见错误写法是 `MAVEN_HOME%/bin`——`%` 只写了后半边**，
>   必须写成 `%MAVEN_HOME%\bin`。只写 `MAVEN_HOME%\bin` 会被当成一个字面目录名，
>   永远解析不出实际路径，症状正是「环境变量明明配了却找不到 mvn」。

**第 3 步：设置 JWT 密钥**（gateway 与 user 必须用同一个值，≥32 字符；不设则这两个服务启动失败）

```powershell
# PowerShell：生成 32 字节随机密钥（不依赖 openssl）
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)

$env:JWT_SECRET = $secret          # 只对当前窗口生效；IDE 需在 Run Configuration 里配
# setx JWT_SECRET "$secret"        # 永久写入用户级变量（base64 结尾的 = 必须加引号）
```

```bash
# bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

> 在 IDE 里启动服务的话，环境变量要配在 **Run Configuration** 里，配完重启 IDE；
> `setx` 写的持久变量对**已打开**的 IDE 窗口无效。详见
> [docs/getting-started.md](docs/getting-started.md#6-设置-jwt_secret)。

**第 4 步：依次启动各服务**

运行各模块 `*Application` 主类（或在模块目录下 `mvn spring-boot:run`）。
顺序：**各业务服务 → 最后 mall-gateway**。网关用 `lb://` 做服务发现，目标服务没注册上来路由会失败。

> 验收：`POST http://localhost:9999/user/login` 能走通完整流程即算成功。
> 各服务健康检查 `GET http://localhost:<端口>/actuator/health`，端口以上表为准。
> 起不来时看 [docs/getting-started.md](docs/getting-started.md#10-排错) 的排错表。

## 测试

```bash
mvn test
```

仓库**共 7 个测试**，都用来守「靠约定维持、重构时容易被静默破坏」的不变量：

| 模块 | 测试 | 守住什么 |
| ---- | ---- | ---- |
| `mall-common` | `IdentityContextTest` | 身份写入 ThreadLocal 后**必须在请求结束被清除**（否则线程池复用会导致越权） |
| `mall-common` | `OutboxConfigTest` | outbox 装配出正确的事务代理（否则 `enqueueNewTx` 的 `REQUIRES_NEW` 静默失效、丢事件） |
| `mall-common` | `OutboxConfirmInstallerTest` | 发布确认回调按行号正确路由；退回回调**必须靠 `messageId` 反查**（`ReturnsCallback` 拿不到 `CorrelationData`） |
| `mall-service-user` | `CouponServiceImplTest` | 优惠券抵扣计算：未达门槛不可用、**抵扣不超过商品金额（不倒找钱）**、折扣舍入到分、封顶生效、范围匹配只计命中行；以及券面文案不得出现 `1E+2` 这类科学计数法（`stripTrailingZeros` 的陷阱） |
| `mall-service-user` | `CouponPreviewRequestValidationTest` | `CouponPreviewRequest` 不带 `userCouponId` 时必须通过校验（`/coupon/usable` 就这么调），且嵌套的 `Line` 约束确实生效（`@Valid` 不能漏） |
| `mall-service-order` | `DiscountAllocatorTest` | 整单优惠按行占比分摊后 **Σ分摊恰好等于整单优惠**（尾差归末行）；单行分摊不超过该行小计；整单为 0 时不除零 |
| `mall-service-inventory` | `InventoryMapperConcurrencyTest` | 并发扣库存**绝不超卖**；释放锁定不能凭空造出库存 |

最后一个用 Testcontainers 起**真实 MySQL**，**需要本机 Docker 守护进程在运行**（前两个不需要）。
详见 [docs/operations.md](docs/operations.md#测试)。

## 可观测性

各服务与网关均接入 actuator（端口即服务端口）：`GET /actuator/health`、`GET /actuator/metrics`。

值得留意的是 `GET /actuator/metrics/mall.outbox.pending`（**outbox 待投递事件数**，order / payment / inventory）：
outbox 由 relay 定时投递，一旦 relay 停摆或持续投递失败，事件会静静堆在表里而**没有任何外部表征**
（订单不推进、库存不释放），只能从业务现象倒推。

还有 `mall.outbox.unroutable`（**无法路由而被退回的消息数**）：发布侧已开 publisher-confirms + returns，
消息到不了任何队列时不再静默丢失，而是计数告警并可重投。各服务 DLQ 堆积在 RabbitMQ 管理台看。

详见 [docs/operations.md](docs/operations.md#可观测性)。

## 文档地图

| 文档 | 讲什么 | 什么时候读 |
| ---- | ---- | ---- |
| [architecture.md](docs/architecture.md) | 模块职责与边界、**端口从哪来**、网关路由与白名单、前端工程、依赖版本 | 想改代码结构 / 端口对不上时 |
| [auth.md](docs/auth.md) | 登录链路、JWT + Redis 单设备登录、身份头契约、角色模型与管理员初始化 | 加接口要做鉴权时 |
| [order-lifecycle.md](docs/order-lifecycle.md) | 订单状态机全景：正向链路、取消三个入口、退款逆向分支 | 改订单流程时 |
| [events.md](docs/events.md) | RabbitMQ 拓扑全表、事务 outbox、延迟消息、有界重试 + DLQ、幂等键 | 加事件 / 消息堆积时 |
| [domains.md](docs/domains.md) | 购物车、商家上架三段式、商品与分类、库存补货与流水、收货地址 | 改业务域时 |
| [api.md](docs/api.md) | 全量接口表（按服务分节）、StripPrefix 规则、鉴权白名单 | 查接口时 |
| [getting-started.md](docs/getting-started.md) | 完整启动步骤、Nacos 配置清单、存量迁移、排错表 | 跑不起来时 |
| [operations.md](docs/operations.md) | 测试、可观测性、关键设计与已知边界 | 上生产 / 排查时 |

> 密钥、数据源、MQ 地址一律由**环境变量或 Nacos** 提供，仓库内不再保留任何**真实**凭据
> （支付商户参数与默认管理员口令都是文档化的占位值，**部署前必须更换**）。
