import request from './request'

// 管理后台数据看板：四个服务各取一块，前端组合。
// silent=true 时由调用方统一提示（看板会并行请求 4 个服务，逐个弹错会刷屏）。
export function getOrderDashboard(days = 7, silent = false) {
  return request.get('/order/admin/dashboard', { params: { days }, _silent: silent })
}

export function getUserStats(days = 7, silent = false) {
  return request.get('/user/admin/stats', { params: { days }, _silent: silent })
}

export function getProductStats(silent = false) {
  return request.get('/product/admin/stats', { _silent: silent })
}

export function getInventoryStats(silent = false) {
  return request.get('/inventory/admin/stats', { _silent: silent })
}
