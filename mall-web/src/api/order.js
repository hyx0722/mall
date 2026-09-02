import request from './request'

// 订单（mall-service-order OrderController，网关前缀 /order）
// 下单/查询均以登录用户身份（Authorization 头）为准，无需传 userId。

// 我的订单列表
export function listOrders() {
  return request.get('/order/findAllOrder')
}

// 订单详情：id 走 query
export function getOrderDetail(id) {
  return request.get('/order/findDetailOrder', { params: { id } })
}

// 创建订单：body { addressId, remark, items: [{ productId, quantity }] }
export function createOrder(payload) {
  return request.post('/order/createOrder', payload)
}

// 买家取消自己的待付款订单（取消后释放库存、关闭未付支付单）
export function cancelOrder(id) {
  return request.post('/order/cancel', null, { params: { id } })
}

// 商家：查看含自己商品的订单（返回 items/cancellable）
export function listSellerOrders() {
  return request.get('/order/seller/orders')
}

// 商家取消某个待付款订单（仅限订单全部为本商家商品）
export function sellerCancelOrder(id) {
  return request.post('/order/seller/cancel', null, { params: { id } })
}
