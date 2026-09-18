import request from './request'

// 库存（mall-service-inventory，网关前缀 /inventory）

// 库存补货（商家操作）：productId + qty 走 query，qty>0
export function restock(productId, qty) {
  return request.post('/inventory/restock', null, { params: { productId, qty } })
}

// 注：曾有一个 updateInventory(payload) 直写库存记录的导出，对应后端 POST /inventory/updateInventory。
// 该后端接口无管理员/归属校验，任何登录用户都能对任意 product_id 插入库存行且不写 inventory_log，
// 破坏「所有库存变动都落流水」的不变量，已连同前端导出一并移除。库存只能经 restock / addNumInventory 变更。

// 为商品初始化库存（addNumInventory，feign 目标；发布商品时后端已代为完成）
export function addNumInventory(payload) {
  return request.post('/inventory/addNumInventory', payload)
}
