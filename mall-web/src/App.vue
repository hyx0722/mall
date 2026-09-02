<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { username, clearSession } from './stores/auth'

const route = useRoute()
const router = useRouter()

// 登录/注册等未登录页面不展示顶部导航
const isAuthPage = computed(() => ['login', 'register'].includes(route.name))

// 当前路由归属哪个一级菜单（高亮用）
const ACTIVE_MAP = {
  home: '/',
  'product-detail': '/',
  store: '/',
  checkout: '/',
  orders: '/orders',
  'order-detail': '/orders',
  pay: '/orders',
  seller: '/seller',
  'seller-orders': '/seller',
  profile: '/profile',
  address: '/address',
}
const activeMenu = () => ACTIVE_MAP[route.name] || '/'

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
      <div class="brand" @click="nav('/')">mall · 微服务商城演示</div>
      <el-menu
        mode="horizontal"
        class="nav"
        :default-active="activeMenu()"
        :ellipsis="false"
        @select="nav"
      >
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/orders">我的订单</el-menu-item>
        <el-menu-item index="/seller">卖家中心</el-menu-item>
      </el-menu>
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
    </el-header>
    <el-main class="main">
      <router-view />
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
.user-box {
  display: flex;
  align-items: center;
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
