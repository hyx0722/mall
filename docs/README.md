# mall 文档索引

> 所属：[mall 项目说明](../README.md)

根 [README](../README.md) 只回答「这是什么、怎么跑起来」；本目录放设计细节与参考资料。

## 按目的找

| 我想…… | 读这篇 |
| ---- | ---- |
| 把项目跑起来 / 跑不起来要排错 | [getting-started.md](getting-started.md) |
| 搞清楚模块划分、端口从哪来、网关怎么路由 | [architecture.md](architecture.md) |
| 改订单流程（下单 / 取消 / 发货 / 退款） | [order-lifecycle.md](order-lifecycle.md) |
| 加事件，或排查消息堆积、看 MQ 拓扑 | [events.md](events.md) |
| 动鉴权，或加需要登录 / 管理员权限的接口 | [auth.md](auth.md) |
| 改购物车 / 商品 / 库存 / 收货地址 | [domains.md](domains.md) |
| 查某个接口的路径与参数 | [api.md](api.md) |
| 跑测试、看监控指标、了解已知边界 | [operations.md](operations.md) |

## 全部文档

| 文档 | 一句话 |
| ---- | ---- |
| [architecture.md](architecture.md) | 模块职责与边界、**端口解析规则**、网关路由与白名单、前端工程、全量依赖版本 |
| [auth.md](auth.md) | 登录与鉴权链路、JWT + Redis 单设备登录、身份头契约、角色模型与管理员初始化 |
| [order-lifecycle.md](order-lifecycle.md) | 订单状态机全景：正向链路、取消三个入口、退款逆向分支 |
| [events.md](events.md) | RabbitMQ 拓扑全表、事务 outbox、支付超时延迟消息、有界重试 + DLQ、幂等键 |
| [domains.md](domains.md) | 购物车、商家上架三段式、商品与分类、库存补货与流水、收货地址 |
| [api.md](api.md) | 全量接口表（按服务分节）、网关前缀与 StripPrefix、鉴权白名单 |
| [getting-started.md](getting-started.md) | 完整启动步骤、**必须手建的 Nacos 配置**、存量迁移、排错 |
| [operations.md](operations.md) | 测试、可观测性、关键设计与已知边界（含 Seata 残留说明） |

## 阅读顺序建议

- **第一次接触**：根 [README](../README.md) → [getting-started.md](getting-started.md) → [architecture.md](architecture.md)。跑通之后再回头看设计。
- **只改某一处代码**：从上表按目的直达，不必通读。
- **通读设计**：[architecture.md](architecture.md) → [auth.md](auth.md) → [order-lifecycle.md](order-lifecycle.md) → [events.md](events.md) → [domains.md](domains.md) → [operations.md](operations.md)。

> 文档之间的引用一律用文件链接 + 标题锚点，**不使用「§N」式章节编号**——章节会重排，编号会失效。
