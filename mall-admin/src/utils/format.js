// 金额格式化
export function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

// 订单状态（orderStatus），语义与 DB 注释一致：2-待收货（全部卖家已发货、等待买家确认收货）
export const ORDER_STATUS = {
  0: { text: '待付款', type: 'warning' },
  1: { text: '待发货', type: 'primary' },
  2: { text: '待收货', type: 'success' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' },
  5: { text: '退款中', type: 'warning' },
  6: { text: '已退款', type: 'info' },
}

export function orderStatusTag(status) {
  const s = ORDER_STATUS[Number(status)]
  return s || { text: String(status ?? '-'), type: 'info' }
}
