import request from './request'

// 收货地址（mall-service-user UserAddressController，网关前缀 /user）

// 当前用户地址列表
export function listAddresses() {
  return request.get('/user/selectUserAddress')
}

// 地址详情
export function getAddress(id) {
  return request.get('/user/selectUserDetailAddress', { params: { id } })
}

// 新增：body UserAddress { receiverName, receiverPhone, province, city, district, detailAddress, isDefault }
export function addAddress(payload) {
  return request.post('/user/addUserAddress', payload)
}

// 修改：id 走 query，body 携带要更新的地址字段
export function updateAddress(id, payload) {
  return request.post('/user/updateUserAddressById', payload, { params: { id } })
}

// 删除
export function deleteAddress(id) {
  return request.delete('/user/deleteUserAddress', { params: { id } })
}
