import request from './request'

// 站内通知（mall-service-user NotificationController，网关前缀 /user）
// 通知的写入全部在服务端（MQ 消费者 + 商店扇出），前端只能读和标已读。

/**
 * 收件箱分页。
 * @param category all 全部 / order 订单 / store 商店
 * @param isRead   undefined 全部，0 未读，1 已读
 */
export function listMessages({ category, isRead, page = 1, size = 10 } = {}) {
  const params = { category, page, size }
  if (isRead !== undefined && isRead !== null) params.isRead = isRead
  return request.get('/user/message/list', { params })
}

/**
 * 未读数（顶栏铃铛角标）。
 *
 * `_silent` 是必须的：本接口被定时轮询，服务不可用时**不能弹 toast**。
 * 注意光靠调用方 catch 挡不住——拦截器在 reject 之前就已经 ElMessage.error 了。
 */
export function unreadCount() {
  return request.get('/user/message/unreadCount', { _silent: true })
}

export function markRead(id) {
  return request.put('/user/message/read', null, { params: { id } })
}

export function markAllRead() {
  return request.put('/user/message/readAll')
}

export function deleteMessage(id) {
  return request.delete('/user/message/delete', { params: { id } })
}
