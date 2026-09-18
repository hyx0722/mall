import request from './request'

// 优惠券（mall-service-user CouponController，网关前缀 /user）

// 券中心：启用中、有效期内、尚有余量的券
export function couponCenter() {
  return request.get('/user/coupon/center')
}

// 领券（每人每券限领 1 张，重复领取后端会返回友好提示）
export function receiveCoupon(couponId) {
  return request.post('/user/coupon/receive', null, { params: { couponId } })
}

// 我的券：status 不传即全部（0-未使用 1-已使用 2-已过期）
export function myCoupons(status) {
  const params = status === undefined || status === null ? {} : { status }
  return request.get('/user/coupon/mine', { params })
}

/**
 * 结算页可用券列表：一次性拿到「我的每张未使用券对本单是否可用 + 能抵多少」，
 * 不必为每张券各发一次试算。lines 为商品快照明细 [{ productId, categoryId, lineTotal }]。
 */
export function usableCoupons(lines) {
  return request.post('/user/coupon/usable', { lines })
}

// 店铺页：某商家当前可领的券（按用户名定位商家）
export function storeCoupons(username) {
  return request.get('/user/coupon/store', { params: { username } })
}

// ---------- 商家发券（/user/seller/coupon/*）----------
// 券的归属只取登录态，请求体里没有 sellerId，前端也无从伪造成别家店铺的券

// 我发的券（含停用与已领完）
export function sellerCoupons() {
  return request.get('/user/seller/coupon/list')
}

// 给自家商品发券：scopes 必须是 [{ scopeType: 1, scopeId: 商品id }]，后端逐个校验归属
export function createSellerCoupon(data) {
  return request.post('/user/seller/coupon/create', data)
}

export function updateSellerCouponStatus(couponId, status) {
  return request.put('/user/seller/coupon/status', null, { params: { couponId, status } })
}
