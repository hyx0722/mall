# 完整启动指南

> 所属：[mall 项目说明](../README.md) · [文档索引](README.md)
> 前置阅读：[architecture.md](architecture.md)（模块与端口解析规则）
> 相关：[operations.md](operations.md)（测试与可观测性）

根 [README](../README.md) 的「快速启动」是happy path；本文是完整版，含 Nacos 配置清单、
存量迁移与排错。

## 1. 前置依赖

JDK 21、Maven、MySQL、Redis、RabbitMQ、Nacos。

## 2. 起中间件

两种方式二选一。

### 方式 A：本机已装好中间件，直接用

本机若已有 MySQL / Redis / RabbitMQ / Nacos 在跑，**跳过本步**，直接进第 3 步。
注意服务连接用的是**标准端口**（MySQL `3306`、Redis `6379`、RabbitMQ `5672`、Nacos `8848`）。

### 方式 B：用 Docker

仓库根的 `docker-compose.yml` 一键拉起 MySQL / Redis / RabbitMQ / Nacos：

```bash
# ⚠️ 别盲目执行这行 cp —— 它会**无条件覆盖**已存在的 .env。
#    如果 .env 已经配好能用，直接编辑它。
cp .env.example .env
# 然后**必须**填 MYSQL_PASSWORD。compose 里写的是 ${MYSQL_PASSWORD:?...}，
# 留空会让 up 直接中止（容器只被 Created、不会启动），报：
#   error while interpolating services.mysql.environment.MYSQL_ROOT_PASSWORD:
#   required variable MYSQL_PASSWORD is missing a value
docker compose up -d
```

**宿主机端口默认错开**，以便与本机已装的中间件并存而不抢端口：

| 中间件 | 宿主机端口 | 容器内 | 覆盖变量 |
| ---- | ---- | ---- | ---- |
| MySQL | `3307` | 3306 | `MYSQL_PORT` |
| Redis | `6380` | 6379 | `REDIS_PORT` |
| RabbitMQ AMQP | `5673` | 5672 | `RABBITMQ_PORT` |
| RabbitMQ 管理台 | `15673` | 15672 | `RABBITMQ_MGMT_PORT` |
| Nacos HTTP / 控制台 | `8858` | 8848 | `NACOS_PORT` |
| Nacos gRPC | `9858` / `9859` | 9848 / 9849 | `NACOS_GRPC_PORT` / `NACOS_RAFT_PORT` |

> 要占标准端口（例如你就是想让容器接管 MySQL），在 `.env` 里设 `MYSQL_PORT=3306`，
> 并**先停掉本机对应的中间件**。
>
> ⚠️ 但请记住：**业务服务是按标准端口连中间件的**（数据源地址在 Nacos 配置里、
> RabbitMQ 在各自 `application.yml` 里）。所以端口错开时，容器只是「并存可用」——
> 服务连的仍然是本机那套中间件，而不是容器里的。要让服务改用容器，
> 得同时把 Nacos 配置里的地址指过去。

MySQL 首次启动会**自动执行**各模块 `resources` 下的建表脚本（挂到 `docker-entrypoint-initdb.d`，
文件名前缀仅用于固定执行顺序；各脚本自带 `CREATE DATABASE` + `USE`，实际互不依赖）：

| 容器内路径 | 来源 |
| ---- | ---- |
| `01-user.sql` | `mall-service/mall-service-user/src/main/resources/User.sql` |
| `02-product.sql` | `mall-service/mall-service-product/src/main/resources/Product.sql` |
| `03-order.sql` | `mall-service/mall-service-order/src/main/resources/order.sql` |
| `04-inventory.sql` | `mall-service/mall-service-inventory/src/main/resources/inventory.sql` |
| `05-payment.sql` | `mall-service/mall-service-payment/src/main/resources/payment.sql` |

> ⚠️ `initdb` **只在数据卷为空时执行一次**。改了 SQL 想重建，先 `docker compose down -v`。

附带两个控制台（端口按上表的错开值）：RabbitMQ <http://localhost:15673>（看各 DLQ 堆积）、
Nacos <http://localhost:8858/nacos>。

其他注意点：

- MySQL 启动参数带 `--lower-case-table-names=1`（与 Windows 上大小写不敏感的本地 MySQL 对齐，
  避免「本地能跑、容器里报表不存在」）；字符集 `utf8mb4` / `utf8mb4_unicode_ci`。
- **Nacos 镜像未固定小版本**（`nacos/nacos-server:latest`），需要可复现构建请自行改成具体版本。
- compose 里 **`NACOS_AUTH_ENABLE=false`**（本地演示未开鉴权），生产请开启并配置独立账号。
- RabbitMQ 额外挂载 `docker/rabbitmq/rabbitmq.conf`，内容只有一行 `loopback_users.guest = false`——
  让宿主机上的业务服务能以默认 `guest` 账号跨 docker bridge 连上。

## 3. 在 Nacos 准备配置（**最容易卡住的一步**）

> **仓库里不含任何 Nacos 配置**——没有 `common.yaml`、没有 `datasource.yaml`，一个 fixture 都没有。
> 它们需要你**手工创建**。`docker compose up -d` **不足以**让业务服务启动起来。

5 个业务服务各自通过 `spring.config.import` 拉两份配置（namespace `dev`，group 为服务名）：

```yaml
spring:
  config:
    import:
      - nacos:common.yaml?group=mall-service-user
      - nacos:datasource.yaml?group=mall-service-user
```

所以要在 Nacos（namespace **`dev`**）下为这 5 个 group 各建两条配置：

`mall-service-user`、`mall-service-product`、`mall-service-order`、`mall-service-inventory`、`mall-service-payment`

### `common.yaml`（每个 group 一份）

至少要有 `server.port`——**它决定了服务的实际运行端口**（覆盖本地 `application.yml`）：

```yaml
server:
  port: 9000    # 换成该服务的目标端口
```

各服务端口取值与「为什么和仓库里的 yml 不一致」见
[architecture.md](architecture.md#端口从哪来这一节能省你半小时)。

### `datasource.yaml`（每个 group 一份）

数据库与 Redis。**每个服务的 url 必须指向自己的库**：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mall_service_user?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的密码
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: 100ms
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

> 五个库名分别是 `mall_service_user` / `mall_service_product` / `mall_service_order` /
> `mall_service_inventory` / `mall_service_payment`。

> 📌 各服务 `src/main/resources/application-datasource.yml` 只是 `datasource` profile 下的
> **本地参考兜底**（该 profile **默认不启用**），可以用来抄格式，但**运行时以 Nacos 为准**。

### RabbitMQ 不在 Nacos

order / inventory / payment 的 RabbitMQ 连接来自各自**本地 `application.yml`**，
用环境变量覆盖：

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
```

### 网关不参与这一步

`mall-gateway` **不读 Nacos 配置**（只用 Nacos 做服务发现），无需为它建任何配置。

## 4. 初始化数据库

用 Docker 起 MySQL 的话已自动完成（见第 2 步）。手动准备的话，
新建各业务库并执行对应模块 `src/main/resources` 下的建表脚本
（`User.sql`、`Product.sql`、`order.sql`、`inventory.sql`、`payment.sql`）。

## 5. 编译并安装公共模块

各业务服务依赖 `model` 与 `mall-common`，且**单跑某个服务时这两个依赖解析自本地 Maven 仓库**，
不是 reactor 里的 `target/classes`。所以改了 `model` / `mall-common` 必须 install，否则跑起来还是旧的。

仓库自带 **Maven Wrapper**，不依赖 `mvn` 是否在 PATH 上，推荐用它：

```powershell
# PowerShell
.\mvnw.cmd clean install -DskipTests
```

```bash
# bash
./mvnw clean install -DskipTests
```

首次执行会按 `.mvn/wrapper/maven-wrapper.properties` 指定的版本（Maven 3.9.16）
自动下载到 `~/.m2/wrapper/dists/`，之后走本地缓存。

想用本机装的 Maven（`mvn clean install -DskipTests`）也行，但**要求 `mvn` 在 PATH 上**。
报「无法将"mvn"项识别为 cmdlet / 不是内部或外部命令」时：

- 临时用完整路径（`&` 是 PowerShell 的调用运算符）：

  ```powershell
  & "E:\javase\apache-maven-3.9.16\bin\mvn.cmd" clean install -DskipTests
  ```

- 或修 PATH 里那条坏项。**最常见的错误写法是 `MAVEN_HOME%/bin`——`%` 只写了后半边**，
  正确写法是 `%MAVEN_HOME%\bin`。只写 `MAVEN_HOME%\bin` 会被当作一个字面目录名，
  永远不会被展开，症状正是「`MAVEN_HOME` 明明设了却找不到 `mvn`」。
  改系统 PATH 需要管理员权限（系统属性 → 环境变量 → 系统变量 → Path）。
  也可先在本机验证：`[Environment]::GetEnvironmentVariable('MAVEN_HOME','Machine')`。

## 6. 设置 JWT_SECRET

`mall-gateway` 与 `mall-service-user` 需要**同一个**签名密钥（≥32 字符），
未设置时这两个服务**启动失败**并提示原因。详见 [auth.md](auth.md#jwt-密钥)。

> 下面两段**按你用的 shell 二选一**，别混用：`export` 是 bash 语法，
> 在 PowerShell 里会报「无法将"export"项识别为 cmdlet、函数、脚本文件或可运行程序」。

```bash
# bash：生成一个密钥（只做一次，两个服务必须用同一个值）
openssl rand -base64 32

# 写进 shell 或 IDE 的 Run Configuration 环境变量
export JWT_SECRET='<上一步生成的值>'
```

Windows PowerShell（不必依赖 `openssl`）：

```powershell
# 生成 32 字节随机密钥
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes)

# 永久写入用户级环境变量（base64 结尾的 = 必须加引号，否则被 setx 误解析）
setx JWT_SECRET "$secret"
$env:JWT_SECRET = $secret      # 让当前窗口也生效；setx 不影响已开的窗口
```

> `setx` 只写持久变量，**已启动的 IDE 读不到**——设完请重启 IDEA，
> 且从开始菜单启动（从旧终端启动会继承旧环境）。
> 也可只给 `mall-gateway` 与 `mall-service-user` 两个 Run Configuration 配 `JWT_SECRET`，其余服务不需要。

## 7. 启动服务

各服务运行 `*Application` 主类即可（`mvn spring-boot:run` 也行）。

**建议顺序**：先确保 `model` / `mall-common` 已 install → 各业务服务 → 最后 mall-gateway。

启动顺序之所以是「业务服务先、网关后」，是因为网关用 `lb://` 做服务发现，
目标服务没注册上来时路由会失败。

## 8. 验收

网关起来后，用一条链路确认全通：

```bash
# 1) 注册 + 登录（白名单，不需要 token）
curl -X POST http://localhost:9999/user/register -d "username=demo001&password=123456"
TOKEN=$(curl -s -X POST http://localhost:9999/user/login -d "username=demo001&password=123456" \
        | sed 's/.*"data":"\([^"]*\)".*/\1/')

# 2) 带 token 访问受保护接口（应返回 200 + 业务数据）
curl -H "Authorization: $TOKEN" http://localhost:9999/product/category/list

# 3) 不带 token（应返回 401，证明网关鉴权生效）
curl -o /dev/null -w "%{http_code}\n" http://localhost:9999/product/category/list
```

各服务的健康检查：`GET http://localhost:<端口>/actuator/health`，端口以实际生效值为准
（见 [architecture.md](architecture.md#服务与端口)）。

## 9. 从旧提交升级的存量迁移

> 全新搭建可跳过本节。

**自「退款 / 购物车」起**：

- order 库需执行 `order.sql` 尾部的 `CREATE TABLE order_refund`（退款申请单）。
- 全部 5 个库里的 `undo_log` 表已从建表脚本中删除（Seata 早已移除，该表是残留死表）；
  存量库可自行 `DROP TABLE undo_log`。
- RabbitMQ 侧新增 `refund.request` / `pay.refund.success` / `order.refunded` 三个队列，
  由各服务 `RabbitConfig` 自动声明，无需手工操作。
- 购物车用 Redis，不需要建表；但各业务服务需能连到 Redis（配置在 Nacos `datasource.yaml`）。

**自「事件可靠性加固」起**：

- order / payment 库需手动补 `outbox` 建表 DDL（见 `order.sql` / `payment.sql` 尾部）；
  inventory 库需执行：

  ```sql
  ALTER TABLE inventory_log ADD UNIQUE KEY uk_order_product_type (order_id, product_id, change_type);
  ```

  （若历史数据有同订单同商品同类型重复流水，先清理再执行。）

**自「库存回执入 outbox」起**：

- **inventory 库需手动补 `outbox` 建表 DDL**（见 `inventory.sql` 尾部第 3 节）。
  缺这张表不会影响库存消费本身，但 relay 每 3 秒会因「表不存在」报错、扣减回执发不出去，
  订单侧收不到 `deduct_failed` 就不再被及时取消。存量库执行：

  ```sql
  -- 完整语句见 inventory.sql 尾部
  CREATE TABLE `outbox` ( ... ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  ```
- RabbitMQ 侧新增延迟交换机 / 持有队列 / 死信交换机 / 各 DLQ，且原入站队列现在带
  `x-dead-letter-exchange=mall.order.dlx` 参数——**已存在的同名旧队列与旧参数不符会导致 406
  PRECONDITION_FAILED**。本地演示建议清空 RabbitMQ 数据或换一个 vhost 后重启各服务。

**JWT 密钥**：旧的 JWT 密钥已随仓库公开，**必须重新生成**（改密钥会让所有已签发 token 立即失效，属预期行为）。

## 10. 排错

| 现象 | 原因 | 处理 |
| ---- | ---- | ---- |
| `docker compose ps` 里 nacos 一直 `Restarting`，日志末行 `env NACOS_AUTH_TOKEN must be set with Base64 String.` | Nacos 3.x 镜像**即使 `NACOS_AUTH_ENABLE=false` 也强制要求**该变量非空（Base64，解码后 ≥32 字符），为空即 `exit 255` | compose 已给演示默认值；要覆盖就在 `.env` 设 `NACOS_AUTH_TOKEN`（生成 `openssl rand -base64 32`）。同类还有 `NACOS_AUTH_IDENTITY_KEY` / `_VALUE`，非空即可 |
| nacos 一直 `health: starting` 最后变 `unhealthy`，但控制台能打开 | compose 的 healthcheck 探测 `/nacos/v1/console/health/readiness`，**该端点在 Nacos 3.x 已移除**（404） | 已改为探测 `/nacos/`。若你用的是旧版 compose，手动改一下 |
| `export : 无法将"export"项识别为 cmdlet、函数、脚本文件或可运行程序` | 在 **PowerShell** 里执行了 bash 语法 | PowerShell 用 `$env:JWT_SECRET = "..."`，见 [第 6 步](#6-设置-jwt_secret) |
| `mvn : 无法将"mvn"项识别为 cmdlet…` / `不是内部或外部命令` | `mvn` 不在 PATH 上；常见于 PATH 里写成了 `MAVEN_HOME%/bin`（`%` 只写了后半边，解析不出） | **改用仓库自带的 `.\mvnw.cmd`**（不依赖 PATH），或用完整路径 `& "…\bin\mvn.cmd"`。见 [第 5 步](#5-编译并安装公共模块) |
| `docker compose up -d` 报 `required variable MYSQL_PASSWORD is missing a value`，容器只被 Created 不启动 | `.env` 里 `MYSQL_PASSWORD` 是空的（compose 用了 `${VAR:?}` 必填语法） | 填上 `MYSQL_PASSWORD`；若你原本的 `.env` 被 `cp .env.example .env` 覆盖过，需要重新填回真实值 |
| 服务启动即失败，日志提示「未配置 JWT 密钥」或「JWT 密钥过短」 | 缺 `JWT_SECRET` 或不足 32 字符 | 见 [第 6 步](#6-设置-jwt_secret)，且 gateway 与 user 必须同值 |
| 启动报找不到 `common.yaml` / `datasource.yaml` | 第 3 步的 Nacos 配置没建 | 按服务名建 group，namespace 必须是 `dev` |
| 连不上 RabbitMQ，报 `Connection refused` | 目标主机只监听了 IPv6，而 JVM 把 `localhost` 解析成了 `127.0.0.1` | 用 `RABBITMQ_HOST=[::1]` 启动（**方括号不能省**，写裸 `::1` 会被 amqp-client 当成未加引号的 IPv6 直接报错） |
| 连接成功但报表不存在 | Nacos `datasource.yaml` 的 url 指错了库 | 报错信息里 `Table 'mall_service_xxx.表' doesn't exist` 即为线索 |
| 队列声明报 **406 PRECONDITION_FAILED** | 旧队列缺少 `x-dead-letter-exchange` 参数 | 见 [第 9 步](#9-从旧提交升级的存量迁移) |
| 前端弹「系统繁忙，请稍后重试」 | 后端抛异常被 `GlobalExceptionHandler` 兜底成了友好文案 | 真实原因只在服务控制台日志里，去对应服务的日志找堆栈 |
| 端口和本文档/仓库 yml 对不上 | 运行时以 Nacos `common.yaml` 为准 | 见 [architecture.md](architecture.md#端口从哪来这一节能省你半小时) |

## 相关文档

- 端口与路由： [architecture.md](architecture.md)
- 测试与指标： [operations.md](operations.md)
