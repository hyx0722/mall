<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { username, token, clearSession } from './stores/auth'
import { unreadCount } from './api/message'

const route = useRoute()
const router = useRouter()

// ---------- 未读消息角标 ----------
const unread = ref(0)

// 30s 轮询一次。足够「有消息时很快看到」，又不会把网关刷爆；
// WebSocket/SSE 才是正解，但本仓没有推送通道，加一条长连接不划算。
const POLL_MS = 30000
let timer = null

async function refreshUnread() {
  try {
    // 这个接口在 api 层标了 _silent：轮询失败不能弹 toast
    unread.value = Number((await unreadCount()) || 0)
  } catch {
    // 静默失败并**保留上一次的数字**：服务抖一下就把角标清零，
    // 反而会让用户以为消息是被自己读掉的
  }
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/**
 * 只跟随 token 起停轮询。
 *
 * ⚠️ 必须先 stopPoll 再 start：watch 会在每次登录/登出时回调，
 * 不先清就会每切换一次多留一个定时器，请求数悄悄翻倍且没有任何报错。
 * 另外 App.vue 只挂载一次，登出后组件不会卸载，所以光靠 onUnmounted 不够。
 */
watch(
  token,
  (tk) => {
    stopPoll()
    if (!tk) {
      unread.value = 0
      return
    }
    refreshUnread()
    timer = setInterval(refreshUnread, POLL_MS)
  },
  { immediate: true },
)

onUnmounted(stopPoll)

/**
 * 路由切换时补一次。30s 的轮询对「刚看完消息退回首页」这种动作太慢——
 * 角标会挂着刚才已经读过的数字，看起来像是没生效。导航是低频动作，
 * 多这一次轻量请求换来角标的即时性。
 */
watch(
  () => route.fullPath,
  () => {
    if (token.value) refreshUnread()
  },
)

// 登录/注册等未登录页面不展示顶部导航
const isAuthPage = computed(() => ['login', 'register'].includes(route.name))

// 当前路由归属哪个一级菜单（高亮用）
const ACTIVE_MAP = {
  home: '/',
  'product-detail': '/',
  store: '/',
  cart: '/cart',
  checkout: '/cart',
  coupons: '/coupons',
  backpack: '/backpack',
  messages: '/messages',
  orders: '/orders',
  'order-detail': '/orders',
  pay: '/orders',
  seller: '/seller',
  'seller-orders': '/seller',
  'seller-coupons': '/seller',
  'seller-stats': '/seller/stats',
  'seller-messages': '/seller',
  profile: '/profile',
  address: '/address',
}
const activeMenu = () => ACTIVE_MAP[route.name] || '/'

// 页面底色按业务模块分组，与主背景 #f5f7fa 区分开。
// 色调取 OKLCH 浅色区（L≈0.94）下的可用色相：该明度下蓝/紫/灰互相分不开，
// 故只保留实测能分辨的 7 组；两两 ΔE ≥ 3.3，各自与主背景 ΔE 4.2–6.5。
const PAGE_TINT = {
  // 浏览/商品 —— 蓝
  home: 'tint-browse',
  'product-detail': 'tint-browse',
  store: 'tint-browse',
  // 购物车/结算/支付 —— 绿
  cart: 'tint-trade',
  checkout: 'tint-trade',
  pay: 'tint-trade',
  // 订单 —— 青
  orders: 'tint-order',
  'order-detail': 'tint-order',
  // 优惠券/背包/我的消息 —— 粉
  coupons: 'tint-promo',
  backpack: 'tint-promo',
  messages: 'tint-promo',
  // 个人中心/收货地址 —— 黄
  profile: 'tint-me',
  address: 'tint-me',
  // 商家中心全系 —— 橙
  seller: 'tint-seller',
  'seller-orders': 'tint-seller',
  'seller-coupons': 'tint-seller',
  'seller-stats': 'tint-seller',
  'seller-messages': 'tint-seller',
  // 登录/注册 —— 中性灰
  login: 'tint-auth',
  register: 'tint-auth',
}
const pageTint = computed(() => PAGE_TINT[route.name] || 'tint-browse')

function nav(path) {
  router.push(path)
}

function onUserCommand(cmd) {
  if (cmd === '__logout') {
    clearSession()
    router.push('/login')
  } else {
    router.push(cmd)
  }
}

</script>

<template>
  <el-container class="layout">
    <el-header v-if="!isAuthPage" class="topbar">
      <!-- 左：身份入口（头像圆圈 + 用户名；未登录时是登录按钮） -->
      <div class="user-box">
        <template v-if="username">
          <el-dropdown @command="onUserCommand">
            <span class="uname">
              <el-avatar :size="26" class="avatar">{{ username.charAt(0).toUpperCase() }}</el-avatar>
              <span class="name">{{ username }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="/coupons">优惠券</el-dropdown-item>
                <el-dropdown-item command="/backpack">背包</el-dropdown-item>
                <el-dropdown-item command="/address">收货地址</el-dropdown-item>
                <el-dropdown-item divided command="__logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button size="small" type="primary" plain @click="nav('/login')">登录</el-button>
        </template>
      </div>

      <!-- 中：导航菜单。.nav 是 flex:1，占满中间并把后面的品牌与铃铛一起顶到右端 -->
      <el-menu
        mode="horizontal"
        class="nav"
        :default-active="activeMenu()"
        :ellipsis="false"
        @select="nav"
      >
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/cart">购物车</el-menu-item>
        <el-menu-item index="/orders">我的订单</el-menu-item>
        <el-menu-item index="/seller">我的商品</el-menu-item>
        <el-menu-item index="/seller/stats">
          <span class="dot-icon" aria-hidden="true">
            <svg viewBox="0 0 16 16" width="10" height="10">
              <polyline
                points="1,11 5,7 8,10 15,2"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          我的商店数据
        </el-menu-item>
      </el-menu>

      <!-- 「我的商店数据」右侧：品牌。放在菜单之后（而不是最左端）,
           点它回首页的行为不变 -->
      <div class="brand" @click="nav('/')">mall · 微服务商城演示</div>

      <!-- 右：我的消息。未登录不显示（消息页需要登录态）。
           角标走 el-badge，为 0 时整个徽标隐藏（不是显示 0） -->
      <div v-if="username" class="actions">
        <el-badge :value="unread" :max="99" :hidden="!unread" class="bell-badge">
          <button
            class="bell"
            type="button"
            title="我的消息"
            aria-label="我的消息"
            @click="nav('/messages')"
          >
            <svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
              <path
                d="M8 1.5a4 4 0 0 0-4 4v2.2L2.8 10a.6.6 0 0 0 .5.9h9.4a.6.6 0 0 0 .5-.9L12 7.7V5.5a4 4 0 0 0-4-4Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linejoin="round"
              />
              <path
                d="M6.4 12.4a1.6 1.6 0 0 0 3.2 0"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </el-badge>
      </div>
    </el-header>
    <el-main class="main">
      <!-- 页面底面板：铺满主区域、四角留出主背景 #f5f7fa 作为边框 -->
      <div class="page-surface" :class="pageTint">
        <router-view />
      </div>
    </el-main>
  </el-container>
</template>

<style>
* {
  box-sizing: border-box;
}
html,
body,
#app {
  height: 100%;
  margin: 0;
}
body {
  font-family: 'Helvetica Neue', Arial, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: #f5f7fa;
}
</style>

<style scoped>
.layout {
  height: 100%;
}

/* el-main 默认有 20px 内边距，这里靠它露出主背景作为面板外框 */
.main {
  display: flex;
  flex-direction: column;
}
/* flex-grow:1 让短页面把底色撑满视口；flex-shrink:0 很关键——
   内容比视口高时若允许压缩，面板会被压成容器高度，父级算不出真实滚动高度，
   长页面（订单列表、商店数据）底部就滚不到了 */
.page-surface {
  flex: 1 0 auto;
  border-radius: 8px;
  padding: 20px;
}

/* 各业务模块的页面底色。都是浅色，白卡片压在上面仍有 1.17–1.30 的对比，
   正文 #303133 对比度 ≥ 10.7，不牺牲可读性。 */
.tint-browse {
  background: #e3ebfd;
}
.tint-trade {
  background: #d2f6dd;
}
.tint-order {
  background: #c6f5fd;
}
.tint-promo {
  background: #fce1fd;
}
.tint-me {
  background: #f0eec7;
}
.tint-seller {
  background: #fde5dd;
}
.tint-auth {
  background: #dfe2e7;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 20px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.brand {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
  cursor: pointer;
  white-space: nowrap;
}
.nav {
  flex: 1;
  border-bottom: none;
  min-width: 0;
}
/* 「我的商店数据」入口前的圆形图标 */
.dot-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-right: 6px;
  border-radius: 50%;
  background: #2a78d6;
  color: #fff;
  flex-shrink: 0;
  vertical-align: middle;
}
/* 左侧身份区。现在只放头像下拉（或登录按钮），不再和铃铛同框 */
.user-box {
  display: flex;
  align-items: center;
}
/* 右侧动作区。靠 .nav 的 flex:1 被顶到最右端，不需要 margin-left:auto */
.actions {
  display: flex;
  align-items: center;
}
/* 「我的消息」圆形入口：与「我的商店数据」的 .dot-icon 同一套圆形语言，
   但做成可点击的按钮，尺寸对齐左侧 26px 的头像 */
.bell-badge {
  display: flex;
  align-items: center;
}
.bell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.bell:hover {
  background: #409eff;
  color: #fff;
}
.uname {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  cursor: pointer;
  outline: none;
  user-select: none;
}
.name {
  font-size: 14px;
}
.avatar {
  background: #409eff;
  color: #fff;
  flex-shrink: 0;
}
.main {
  background: #f5f7fa;
}
</style>
