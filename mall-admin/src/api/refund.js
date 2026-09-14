import request from './request'

// ---------- 退款审核（/order/admin/*，仅管理员） ----------

// 退款申请列表，status 可省略（0待审核 1退款中 2已退款 3已驳回）
export function listRefunds(status) {
  return request.get('/order/admin/refunds', { params: { status } })
}

// 审核退款申请：approve=true 通过（异步转 payment 打款）；false 驳回（rejectReason 必填）
export function auditRefund(data) {
  return request.post('/order/admin/refund/audit', data)
}
