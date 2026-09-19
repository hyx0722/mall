import request from './request'

// 商品评价（mall-service-order OrderController，网关前缀 /order）
//
// 资格规则在服务端：只有**已完成**订单里的商品能评价，且每个订单每个商品一条。
// 前端不需要（也无法）自行判断——传错 orderId/productId 只会被服务端拒绝。

// ---------- 公开读取（商品详情页） ----------

/** 某商品的评价分页，返回 { total, items } */
export function listReviews(productId, page = 1, size = 10) {
  return request.get('/order/review/list', { params: { productId, page, size } })
}

/** 某商品的评价汇总：{ avgRating, total, distribution }。无评价时 avgRating 为 null */
export function reviewStat(productId) {
  return request.get('/order/review/stat', { params: { productId } })
}

/**
 * 单条评价。站内通知里「商家回复了你的评价」只带 reviewId，
 * 靠它换出 productId 才能跳到商品页。
 */
export function reviewDetail(id) {
  return request.get('/order/review/detail', { params: { id } })
}

// ---------- 买家 ----------

/** 我买过该商品、订单已完成、且尚未评价的订单列表（写评价弹框的订单选择器） */
export function myReviewableOrders(productId) {
  return request.get('/order/review/mine', { params: { productId } })
}

/** 写评价：body { orderId, productId, rating, content } */
export function createReview(payload) {
  return request.post('/order/review/create', payload)
}

/** 订单明细 + 每行能否评价（订单详情页）。带归属校验，非本人订单返回空数组 */
export function listOrderItems(orderId) {
  return request.get('/order/findOrderItems', { params: { orderId } })
}

// ---------- 商家 ----------

/** 商家：我商品的评价分页；onlyUnreplied=true 只列未回复的 */
export function listSellerReviews(onlyUnreplied, page = 1, size = 10) {
  const params = { page, size }
  if (onlyUnreplied) params.onlyUnreplied = true
  return request.get('/order/seller/reviews', { params })
}

/** 商家回复评价：body { reviewId, content }。只能回复一次 */
export function replyReview(reviewId, content) {
  return request.post('/order/seller/review/reply', { reviewId, content })
}
