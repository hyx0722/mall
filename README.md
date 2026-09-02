# mall —— 基于 Spring Cloud 的微服务商城

一个用于学习与演示的电商后端项目。基于 Spring Cloud 微服务架构，采用 Nacos 注册/配置中心、Spring Cloud Gateway 网关、MyBatis-Plus 操作 MySQL，Redis 做缓存，RabbitMQ 解耦下单与扣库存流程，可在简单的模拟高并发下单场景下运行。

## 技术栈

| 类别 | 选型 |
| ---- | ---- |
| 语言 / 构建 | Java 21、Maven（多模块） |
| 微服务框架 | Spring Cloud `2025.1.0`、Spring Cloud Alibaba `2025.1.0.0` |
| 基础框架 | Spring Boot `4.0.0` |
| 注册 / 配置中心 | Nacos |
| 网关 | Spring Cloud Gateway |
| ORM | MyBatis-Plus `3.5.17` |
| 存储 / 缓存 | MySQL、Redis |
| 消息队列 | RabbitMQ |
| 鉴权 | JWT（网关统一校验） |

## 模块结构

```
mall
├── model                      # 公共模块：实体 bean、统一返回、全局异常、工具类
├── mall-gateway               # 网关：路由转发 + JWT 鉴权过滤器
├── mall-service               # 业务服务聚合模块
│   ├── mall-service-user      # 用户服务
│   ├── mall-service-product   # 商品 / 分类服务
│   ├── mall-service-order     # 订单服务
│   ├── mall-service-inventory # 库存服务
│   └── mall-service-payment   # 支付服务
└── pom.xml                    # 父工程（依赖版本统一管理）
```

### 服务与端口

| 服务 | 端口 |
| ---- | ---- |
| mall-gateway | 9999 |
| mall-service-user | 9000 |
| mall-service-product | 8000 |
| mall-service-order | 6000 |
| mall-service-inventory | 5000 |
| mall-service-payment | 7000 |

## 核心流程

下单链路采用 RabbitMQ 解耦：

1. 客户端经网关 `/order/**` 发起下单请求；
2. `mall-service-order` 创建订单记录后，发布“订单创建”消息；
3. `mall-service-inventory` 通过 `OrderCreatedListener` 消费消息完成库存扣减并记录库存流水；
4. 网关通过 `AuthGlobalFilter` 对请求统一做 JWT 鉴权后按路径前缀路由到各服务。

## 快速启动

前置依赖：JDK 21、Maven、MySQL、Redis、RabbitMQ、Nacos。

1. **初始化数据库**：新建各业务库，执行对应模块 `src/main/resources` 下的建表脚本（如 `Product.sql`、`inventory.sql`）。
2. **启动 Nacos** 并准备配置：各服务的 `application.yml` 通过 `nacos:common.yaml` / `nacos:datasource.yaml`（group 为服务名）拉取公共与数据源配置，请先在 Nacos 中创建对应配置。
3. **编译并安装公共模块**（各服务依赖 `model`，改动后需先安装才能生效）：
   ```bash
   mvn clean install -DskipTests
   ```
4. **依次启动服务**（可分别运行各模块下的 `*Application` 主类，也可在模块目录执行）：
   ```bash
   mvn spring-boot:run
   ```
   建议启动顺序：model → 各业务服务 → mall-gateway。

> 服务配置（Nacos / Redis / RabbitMQ 地址、JWT 密钥等）见各模块 `src/main/resources/application*.yml`。生产环境请通过环境变量注入密钥，避免硬编码入库。

## 说明

- 本项目定位为学习 / 课程演示项目，部分服务为最小演示实现；
- 目录名 `intercetors`（拦截器包）沿用了原命名，仅供参考。
