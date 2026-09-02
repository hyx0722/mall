<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { setSession, loadProfile, clearSession } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const tk = await login(form.username, form.password)
    setSession(tk, form.username, 1)
    const r = await loadProfile()
    if (r !== 2) {
      clearSession()
      ElMessage.error('该账号无管理员权限')
      return
    }
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/')
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <el-card class="auth-card" shadow="always">
      <h2 class="auth-title">商城管理后台</h2>
      <p class="auth-sub">管理员内部系统</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="管理员账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #001529;
}
.auth-card {
  width: 380px;
  border-radius: 8px;
}
.auth-title {
  margin: 0;
  text-align: center;
  font-size: 20px;
  color: #303133;
}
.auth-sub {
  margin: 6px 0 18px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
.auth-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
