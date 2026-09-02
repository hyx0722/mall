<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, updateUser, resetUserPwd } from '../api/admin'
import { username } from '../stores/auth'
import RefCell from '../components/RefCell.vue'

const loading = ref(true)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const data = (await listUsers({ page: page.value, size: size.value, keyword: keyword.value })) || { total: 0, items: [] }
    list.value = data.items || []
    total.value = Number(data.total) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggleStatus(row) {
  const disable = Number(row.status) === 1
  try {
    await ElMessageBox.confirm(
      disable ? `确定禁用「${row.username}」？禁用后其登录立即失效。` : `确定启用「${row.username}」？`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await updateUser({ id: row.id, status: disable ? 0 : 1 })
    ElMessage.success(disable ? '已禁用' : '已启用')
    load()
  } catch {
    // 拦截器已提示
  }
}

async function toggleRole(row) {
  const toAdmin = Number(row.role) !== 2
  try {
    await ElMessageBox.confirm(
      toAdmin ? `确定将「${row.username}」设为管理员？` : `确定取消「${row.username}」的管理员角色？`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await updateUser({ id: row.id, role: toAdmin ? 2 : 1 })
    ElMessage.success('角色已更新')
    load()
  } catch {
    // 拦截器已提示
  }
}

const resetVisible = ref(false)
const resetId = ref(null)
const resetName = ref('')
const newPwd = ref('')

function openReset(row) {
  resetId.value = row.id
  resetName.value = row.username
  newPwd.value = ''
  resetVisible.value = true
}

async function submitReset() {
  if (!/^\S{5,16}$/.test(newPwd.value)) {
    ElMessage.warning('新密码长度必须在 5-16 位且不能包含空格')
    return
  }
  try {
    await resetUserPwd({ id: resetId.value, newPassword: newPwd.value })
    ElMessage.success('密码已重置，该用户需重新登录')
    resetVisible.value = false
  } catch {
    // 拦截器已提示
  }
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}
function nextPage() {
  if (page.value * size.value >= total.value) return
  page.value += 1
  load()
}

const isSelf = (row) => row.username === username.value

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">用户管理</h3>
      <div class="tools">
        <el-input v-model="keyword" class="kw" placeholder="用户名 / 邮箱 / 手机号" clearable @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column label="ID / 用户名" min-width="150">
          <template #default="{ row }">
            <RefCell :main="row.username" :id="row.id" />
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="170">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="Number(row.status) === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag v-if="Number(row.role) === 2" type="danger" size="small">管理员</el-tag>
            <el-tag v-else type="info" size="small">普通用户</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="注册时间" width="170">
          <template #default="{ row }">{{ (row.createdTime || '').replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link :disabled="isSelf(row)" @click="toggleStatus(row)">
              {{ Number(row.status) === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="warning" :disabled="isSelf(row)" @click="toggleRole(row)">
              {{ Number(row.role) === 2 ? '取消管理员' : '设为管理员' }}
            </el-button>
            <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无用户" />
    </el-card>

    <div v-if="total > 0" class="pager">
      <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
      <span class="pno">第 {{ page }} 页 / 共 {{ total }} 条</span>
      <el-button :disabled="page * size >= total" @click="nextPage">下一页</el-button>
    </div>

    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <p class="tip">为用户「{{ resetName }}」设置新密码（5-16 位非空白字符）。重置后该用户需重新登录。</p>
      <el-input v-model="newPwd" type="password" show-password placeholder="输入新密码" />
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReset">确定重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 12px;
}
.title {
  margin: 0;
}
.tools {
  display: flex;
  gap: 8px;
}
.kw {
  width: 240px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 12px 0 24px;
}
.pno {
  color: #606266;
  font-size: 14px;
}
.tip {
  color: #909399;
  font-size: 13px;
  margin: 0 0 12px;
}
</style>
