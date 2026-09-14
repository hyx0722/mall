import request from './request'

// 购物车（mall-service-product CartController，网关前缀 /product）
// 归属一律以登录态为准，不传 userId。

// 我的购物车：返回带实时价格/主图/可购买标记的条目列表
export function listCart() {
  return request.get('/product/cart/list')
}

// 购物车商品种类数（导航角标）
export function countCart() {
  return request.get('/product/cart/count')
}

// 加购（已存在则累加），返回加购后的数量
export function addToCart(productId, quantity = 1) {
  return request.post('/product/cart/add', { productId, quantity })
}

// 覆盖数量；quantity <= 0 即移除
export function updateCartItem(productId, quantity) {
  return request.post('/product/cart/update', { productId, quantity })
}

// 移除单个商品
export function removeCartItem(productId) {
  return request.post('/product/cart/remove', { productId })
}

// 清空购物车
export function clearCart() {
  return request.delete('/product/cart/clear')
}

// 结算成功后清理已下单的商品
export function removeCartItems(productIds) {
  return request.post('/product/cart/removeItems', productIds)
}
