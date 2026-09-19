import request from './request'

// 商店订阅与商店公告（mall-service-user StoreController，网关前缀 /user）
//
// 全部按 **username** 而不是 storeId：店铺页路由是 /store/:username，
// 而 /user/ortherUser 只返回 username/avatar/status，前端拿不到卖家 id。

// ---------- 买家 ----------

/** 店铺页顶部：{ username, subscribed, subscriberCount } */
export function storeStatus(username) {
  return request.get('/user/store/status', { params: { username } })
}

export function subscribeStore(username) {
  return request.post('/user/store/subscribe', null, { params: { username } })
}

export function unsubscribeStore(username) {
  return request.post('/user/store/unsubscribe', null, { params: { username } })
}

/** 我订阅的商店用户名列表 */
export function mySubscriptions() {
  return request.get('/user/store/my')
}

/** 某店的公告（店铺页可见，不要求订阅） */
export function storeMessages(username, page = 1, size = 10) {
  return request.get('/user/store/message/list', { params: { username, page, size } })
}

// ---------- 商家 ----------

/** 发布公告：落库 + 群发给订阅者 */
export function createStoreMessage(content) {
  return request.post('/user/store/seller/message/create', { content })
}

/** 我发过的公告 */
export function sellerMessages(page = 1, size = 10) {
  return request.get('/user/store/seller/message/list', { params: { page, size } })
}

/** 删除自己的公告（已投递到别人收件箱的通知不回收） */
export function deleteStoreMessage(id) {
  return request.delete('/user/store/seller/message/delete', { params: { id } })
}
