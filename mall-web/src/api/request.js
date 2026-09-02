import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearSession } from '../stores/auth'

const request = axios.create({ baseURL: '/', timeout: 15000 })

// 网关鉴权：请求头 Authorization 直接放裸 JWT（后端无需 Bearer 前缀）。
request.interceptors.request.use((config) => {
  const tk = getToken()
  if (tk) config.headers.Authorization = tk
  return config
})

// 统一解包 Result：{ code, message, data }，成功 code=0 直接返回 data；
// 业务失败 code=1（HTTP 200）与鉴权失败 HTTP 401 均在此统一提示。
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clearSession()
      if (!location.pathname.startsWith('/login')) {
        ElMessage.error(error.response?.data?.message || '未登录或登录已失效')
        location.href = '/login'
      }
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default request
