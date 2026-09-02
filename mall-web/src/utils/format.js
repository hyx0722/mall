// 金额格式化：后端 BigDecimal 可能为 199 或 199.5，统一保留两位小数
export function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

// 订单状态（orderStatus）展示配置，type 为 el-tag 类型
export const ORDER_STATUS = {
  0: { text: '待付款', type: 'warning' },
  1: { text: '待发货', type: 'primary' },
  2: { text: '已发货', type: 'success' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' },
}

export function orderStatusTag(status) {
  const s = ORDER_STATUS[Number(status)]
  return s || { text: String(status ?? '-'), type: 'info' }
}

// 支付方式：1=支付宝 2=微信
export const PAY_METHOD = {
  1: { text: '支付宝', type: 'success' },
  2: { text: '微信支付', type: 'primary' },
}

export function payMethodTag(method) {
  const m = PAY_METHOD[Number(method)]
  return m || { text: String(method ?? '-'), type: 'info' }
}
