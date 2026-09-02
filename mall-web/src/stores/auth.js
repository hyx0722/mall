import { ref } from 'vue'

const TOKEN_KEY = 'mall_token'
const USERNAME_KEY = 'mall_username'

export const token = ref(localStorage.getItem(TOKEN_KEY) || '')
export const username = ref(localStorage.getItem(USERNAME_KEY) || '')

export function getToken() {
  return token.value
}

export function isLoggedIn() {
  return !!token.value
}

export function setSession(tk, uname) {
  token.value = tk
  username.value = uname || ''
  localStorage.setItem(TOKEN_KEY, tk)
  if (uname) localStorage.setItem(USERNAME_KEY, uname)
}

export function setUsername(uname) {
  username.value = uname || ''
  localStorage.setItem(USERNAME_KEY, uname || '')
}

export function clearSession() {
  token.value = ''
  username.value = ''
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
}
