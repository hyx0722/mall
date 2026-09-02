<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserInfo } from '../api/auth'
import { updateProfile, updateAvatar, updatePassword, deleteAccount } from '../api/user'
import { username, clearSession } from '../stores/auth'

const router = useRouter()
const loading = ref(true)
const user = ref(null)

// 编辑资料
const editVisible = ref(false)
const editRef = ref()
const editForm = reactive({ email: '', phone: '' })
const editRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

// 头像
const avatarVisible = ref(false)
const avatarValue = ref('')

// 改密码
const pwdVisible = ref(false)
const pwdRef = ref()
const pwdForm = reactive({ old_pwd: '', new_pwd: '', re_pwd: '' })
const validateRe = (rule, value, callback) => {
  if (value !== pwdForm.new_pwd) callback(new Error('两次输入的新密码不一致'))
  else callback()
}
const pwdRules = {
  old_pwd: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  new_pwd: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^\S{5,16}$/, message: '5-16 位非空白字符', trigger: 'blur' },
  ],
  re_pwd: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateRe, trigger: 'blur' },
  ],
}

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    user.value = (await getUserInfo()) || null
  } catch {
    user.value = null
  } finally {
    loading.value = false
  }
}

function openEdit() {
  editForm.email = user.value?.email || ''
  editForm.phone = user.value?.phone || ''
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editRef.value.validate()
  } catch {
    return
  }
  try {
    await updateProfile({ email: editForm.email, phone: editForm.phone })
    ElMessage.success('资料已更新')
    editVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  }
}

function openAvatar() {
  avatarValue.value = user.value?.avatar || ''
  avatarVisible.value = true
}

async function submitAvatar() {
  const url = avatarValue.value.trim()
  if (!url) {
    ElMessage.warning('请输入头像图片 URL')
    return
  }
  try {
    await updateAvatar(url)
    ElMessage.success('头像已更新')
    avatarVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  }
}

async function submitPwd() {
  try {
    await pwdRef.value.validate()
  } catch {
    return
  }
  try {
    await updatePassword({ ...pwdForm })
    ElMessage.success('密码修改成功，请重新登录')
    pwdVisible.value = false
    clearSession()
    router.replace('/login')
  } catch {
    // 拦截器已提示（如原密码错误）
  }
}

async function removeAccount() {
  try {
    await ElMessageBox.confirm(
      `注销将删除账号「${username.value || ''}」，且不可恢复，确定继续吗？`,
      '注销账号',
      { type: 'error', confirmButtonText: '确认注销', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteAccount()
    ElMessage.success('账号已注销')
    clearSession()
    router.replace('/login')
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h3 class="title">个人中心</h3>
    <el-card v-loading="loading" shadow="never">
      <template v-if="user">
        <div class="profile-head">
          <el-avatar :size="72" :src="user.avatar || ''" class="big-avatar">
            {{ (user.username || '?').charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="base">
            <div class="uname">{{ user.username }}</div>
            <div class="time">注册时间：{{ fmtTime(user.createdTime) }}</div>
          </div>
        </div>
        <el-descriptions :column="1" border class="desc">
          <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ user.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ user.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag v-if="Number(user.status) === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">停用</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button type="primary" @click="openEdit">编辑资料</el-button>
          <el-button @click="openAvatar">更换头像</el-button>
          <el-button @click="pwdVisible = true">修改密码</el-button>
          <el-button type="danger" plain @click="removeAccount">注销账号</el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="未获取到用户信息" />
    </el-card>

    <!-- 编辑资料 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="440px">
      <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="11 位手机号" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 更换头像 -->
    <el-dialog v-model="avatarVisible" title="更换头像" width="440px">
      <el-form label-width="80px">
        <el-form-item label="头像地址">
          <el-input v-model="avatarValue" placeholder="头像图片 URL（后端仅存 URL）" clearable />
        </el-form-item>
        <el-form-item label="预览">
          <el-avatar :size="64" :src="avatarValue || ''">
            {{ (user?.username || '?').charAt(0).toUpperCase() }}
          </el-avatar>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="avatarVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAvatar">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="440px">
      <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="原密码" prop="old_pwd">
          <el-input v-model="pwdForm.old_pwd" type="password" show-password clearable />
        </el-form-item>
        <el-form-item label="新密码" prop="new_pwd">
          <el-input
            v-model="pwdForm.new_pwd"
            type="password"
            show-password
            placeholder="5-16 位非空白字符"
            clearable
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="re_pwd">
          <el-input v-model="pwdForm.re_pwd" type="password" show-password clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPwd">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 860px;
  margin: 0 auto;
}
.title {
  margin: 4px 0 12px;
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}
.big-avatar {
  background: #409eff;
  color: #fff;
  font-size: 28px;
  flex-shrink: 0;
}
.uname {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.time {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
.actions {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
