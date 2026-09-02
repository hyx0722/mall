import { ref } from 'vue'
import { getUserInfo } from '../api/auth'

const TOKEN_KEY = 'mall_admin_token'
const USERNAME_KEY = 'mall_admin_username'
const ROLE_KEY = 'mall_admin_role'

export const token = ref(localStorage.getItem(TOKEN_KEY) || '')
export const username = ref(localStorage.getItem(USERNAME_KEY) || '')
export const role = ref(Number(localStorage.getItem(ROLE_KEY)) || 0)

export function getToken() {
  return token.value
}

export function setSession(tk, uname, r = 1) {
  token.value = tk
  username.value = uname || ''
  role.value = Number(r) || 1
  localStorage.setItem(TOKEN_KEY, tk)
  if (uname) localStorage.setItem(USERNAME_KEY, uname)
  localStorage.setItem(ROLE_KEY, String(role.value))
}

// 拉取当前用户资料，刷新 role/username；返回 role（供登录与路由守卫判断管理员）
export async function loadProfile() {
  if (!token.value) return role.value
  try {
    const u = await getUserInfo()
    if (u) {
      role.value = Number(u.role) || 1
      if (u.username) username.value = u.username
      localStorage.setItem(ROLE_KEY, String(role.value))
      localStorage.setItem(USERNAME_KEY, username.value)
    }
  } catch {
    // 401 由 request 统一处理
  }
  return role.value
}

export function isAdmin() {
  return role.value === 2
}

export function clearSession() {
  token.value = ''
  username.value = ''
  role.value = 0
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
  localStorage.removeItem(ROLE_KEY)
}
