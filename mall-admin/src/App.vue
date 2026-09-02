<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { username, clearSession } from './stores/auth'

const route = useRoute()
const router = useRouter()

const isLoginPage = computed(() => route.name === 'login')

function onUserCommand(cmd) {
  if (cmd === '__logout') {
    clearSession()
    router.replace('/login')
  }
}
</script>

<template>
  <el-container v-if="!isLoginPage" class="admin-layout">
    <el-aside width="212px" class="side">
      <div class="logo">商城管理后台</div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/users">用户管理</el-menu-item>
        <el-menu-item index="/orders">订单管理</el-menu-item>
        <el-menu-item index="/products">商品管理</el-menu-item>
        <el-menu-item index="/inventory">库存查询</el-menu-item>
        <el-menu-item index="/category">分类管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="right">
      <el-header class="topbar">
        <span class="tt">内部系统 · 管理员</span>
        <el-dropdown @command="onUserCommand">
          <span class="uname">{{ username || '管理员' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="__logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
  <router-view v-else />
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
.admin-layout {
  height: 100%;
}
.side {
  background: #001529;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 1px;
}
.menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-text-color: #b7c0cd;
  --el-menu-hover-text-color: #fff;
  --el-menu-active-color: #fff;
  --el-menu-item-height: 46px;
}
.menu .el-menu-item {
  background: transparent;
}
.menu .el-menu-item.is-active {
  background: #409eff;
  color: #fff;
}
.menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.06);
}
.right {
  min-width: 0;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.tt {
  font-size: 15px;
  color: #303133;
  font-weight: 600;
}
.uname {
  color: #409eff;
  cursor: pointer;
  outline: none;
}
.main {
  background: #f5f7fa;
}
</style>
