import request from './request'

// ---------- 用户管理（/user/admin/*） ----------
export function listUsers(params) {
  return request.get('/user/admin/listUsers', { params })
}
export function updateUser(data) {
  return request.put('/user/admin/updateUser', data)
}
export function resetUserPwd(data) {
  return request.patch('/user/admin/resetPwd', data)
}

// ---------- 商品管理（/product/admin/*） ----------
export function listAllProducts(params) {
  return request.get('/product/admin/listAll', { params })
}
export function shelfProduct(id, status) {
  return request.put('/product/admin/shelf', null, { params: { id, status } })
}

// ---------- 订单管理（/order/admin/*） ----------
export function listAllOrders(status) {
  return request.get('/order/admin/findAllOrder', { params: { status } })
}
export function getAdminOrder(id) {
  return request.get('/order/admin/findDetailOrder', { params: { id } })
}
export function getAdminOrderItems(orderId) {
  return request.get('/order/admin/findOrderItems', { params: { orderId } })
}

// ---------- 库存查询（/inventory/admin/*） ----------
export function listAllInventory(productId) {
  return request.get('/inventory/admin/listAll', { params: { productId } })
}

// ---------- 分类管理（/product/category/*） ----------
export function categoryTree() {
  return request.get('/product/category/tree')
}
export function addCategory(data) {
  return request.post('/product/category/add', data)
}
export function updateCategory(data) {
  return request.put('/product/category/update', data)
}
