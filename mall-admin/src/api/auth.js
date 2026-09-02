import request from './request'

// 登录（x-www-form-urlencoded，返回裸 JWT）
export function login(username, password) {
  const body = new URLSearchParams()
  body.append('username', username)
  body.append('password', password)
  return request.post('/user/login', body)
}

// 当前登录用户资料（含 role）
export function getUserInfo() {
  return request.get('/user/userInfo')
}
