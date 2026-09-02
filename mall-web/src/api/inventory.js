import request from './request'

// 库存（mall-service-inventory，网关前缀 /inventory）

// 库存补货（商家操作）：productId + qty 走 query，qty>0
export function restock(productId, qty) {
  return request.post('/inventory/restock', null, { params: { productId, qty } })
}

// 直接写入库存记录（等价新增/初始化一行库存）：body Inventory
export function updateInventory(payload) {
  return request.post('/inventory/updateInventory', payload)
}

// 为商品初始化库存（addNumInventory，feign 目标；发布商品时后端已代为完成）
export function addNumInventory(payload) {
  return request.post('/inventory/addNumInventory', payload)
}
