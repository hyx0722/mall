import request from './request'

// 后端 login/register 接收的是表单参数（x-www-form-urlencoded），不是 JSON body。
function toForm(username, password) {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  return params
}

// 成功返回裸 JWT 字符串
export function login(username, password) {
  return request.post('/user/login', toForm(username, password))
}

export function register(username, password) {
  return request.post('/user/register', toForm(username, password))
}

export function getUserInfo() {
  return request.get('/user/userInfo')
}
