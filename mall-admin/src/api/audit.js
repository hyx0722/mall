import request from './request'

// 每个服务在自己的库里有一张 audit_log，所以按服务查询。
const PREFIX = {
  user: '/user',
  product: '/product',
  order: '/order',
  inventory: '/inventory',
  payment: '/pay',
}

export const AUDIT_SERVICES = [
  { key: 'user', label: '用户服务' },
  { key: 'product', label: '商品服务' },
  { key: 'order', label: '订单服务' },
  { key: 'inventory', label: '库存服务' },
  { key: 'payment', label: '支付服务' },
]

export function listAuditLogs(service, params) {
  const prefix = PREFIX[service]
  if (!prefix) {
    return Promise.reject(new Error(`未知服务：${service}`))
  }
  return request.get(`${prefix}/admin/audit/logs`, { params })
}
