import { createRouter, createWebHistory } from 'vue-router'
import { token, role, loadProfile } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/', redirect: '/users' },
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/UserManage.vue'),
    meta: { requiresAdmin: true },
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/OrderManage.vue'),
    meta: { requiresAdmin: true },
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('../views/ProductManage.vue'),
    meta: { requiresAdmin: true },
  },
  {
    path: '/inventory',
    name: 'inventory',
    component: () => import('../views/InventoryQuery.vue'),
    meta: { requiresAdmin: true },
  },
  {
    path: '/category',
    name: 'category',
    component: () => import('../views/CategoryManage.vue'),
    meta: { requiresAdmin: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 后台页面需管理员：无 token 去登录；token 存在但角色未加载/非管理员则拉取资料后校验
router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin) return true
  if (!token.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (role.value !== 2) {
    await loadProfile()
  }
  if (role.value !== 2) {
    return { path: '/login' }
  }
  return true
})

export default router
