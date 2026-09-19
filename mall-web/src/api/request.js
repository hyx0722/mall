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
//
// _silent：调用方在 config 里传 `_silent: true` 可关掉这里的错误弹窗，**只用于轮询类请求**
// （如顶栏未读数）。这类请求会周期性重复，服务抖一下就弹一次 toast 是骚扰。
// ⚠️ 注意**光靠调用方 .catch 是挡不住的**——弹窗发生在 reject 之前，Promise 链上根本没机会拦。
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      if (!response.config?._silent) ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      // 401 的副作用（清会话 + 跳登录）不受 _silent 影响：登录过期必须处理，
      // 静默的只是那条 toast，不是登出本身
      clearSession()
      if (!location.pathname.startsWith('/login')) {
        if (!error.config?._silent) ElMessage.error(error.response?.data?.message || '未登录或登录已失效')
        location.href = '/login'
      }
    } else if (!error.config?._silent) {
      ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default request
