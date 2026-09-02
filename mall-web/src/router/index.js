import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../stores/auth'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/ProductList.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') },
  // 买家浏览
  { path: '/product/:id', name: 'product-detail', component: () => import('../views/ProductDetail.vue'), meta: { requiresAuth: true } },
  { path: '/store/:username', name: 'store', component: () => import('../views/StoreProducts.vue'), meta: { requiresAuth: true } },
  // 下单与订单
  { path: '/checkout', name: 'checkout', component: () => import('../views/Checkout.vue'), meta: { requiresAuth: true } },
  { path: '/orders', name: 'orders', component: () => import('../views/MyOrders.vue'), meta: { requiresAuth: true } },
  { path: '/order/:id', name: 'order-detail', component: () => import('../views/OrderDetail.vue'), meta: { requiresAuth: true } },
  // 支付
  { path: '/pay', name: 'pay', component: () => import('../views/Pay.vue'), meta: { requiresAuth: true } },
  // 个人中心与收货地址
  { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/address', name: 'address', component: () => import('../views/AddressManage.vue'), meta: { requiresAuth: true } },
  // 卖家中心（分类管理已移入管理员内部系统 mall-admin）
  { path: '/seller', name: 'seller', component: () => import('../views/SellerCenter.vue'), meta: { requiresAuth: true } },
  // 卖家中心：查看自己商品的订单
  { path: '/seller/orders', name: 'seller-orders', component: () => import('../views/SellerOrders.vue'), meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isLoggedIn()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router
