// 金额格式化
export function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

// 订单状态（orderStatus）
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
