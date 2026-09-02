import request from './request'

// mall-service-user（网关前缀 /user）——登录注册见 auth.js

// 查看他人公开信息（店铺页卖家信息）：{ username, avatar, status }
export function getOtherUser(username) {
  return request.get('/user/ortherUser', { params: { username } })
}

// 修改资料：body { email, phone }
export function updateProfile(payload) {
  return request.put('/user/update', payload)
}

// 更新头像：avatar 必须为 URL
export function updateAvatar(avatar) {
  return request.patch('/user/updateAvatar', null, { params: { avatar } })
}

// 修改密码：body 键为 { old_pwd, new_pwd, re_pwd }（snake_case）
export function updatePassword(payload) {
  return request.patch('/user/updatePwd', payload)
}

// 注销当前账号
export function deleteAccount() {
  return request.delete('/user/delete')
}

// 新增收货人详情（等价于添加地址，userId 由后端取当前登录用户）
export function addReceiverDetail(payload) {
  return request.post('/user/addReceiverDetail', payload)
}
