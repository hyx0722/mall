<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAddresses, addAddress, updateAddress, deleteAddress } from '../api/address'

const loading = ref(true)
const list = ref([])

const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: 0,
})

const rules = {
  receiverName: [{ required: true, message: '请输入收货人姓名', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  detailAddress: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
}

const fullAddress = (a) =>
  [a.province, a.city, a.district, a.detailAddress].filter(Boolean).join(' ') || '-'

async function load() {
  loading.value = true
  try {
    list.value = (await listAddresses()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editingId.value = null
  Object.assign(form, {
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    isDefault: 0,
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    receiverName: row.receiverName || '',
    receiverPhone: row.receiverPhone || '',
    province: row.province || '',
    city: row.city || '',
    district: row.district || '',
    detailAddress: row.detailAddress || '',
    isDefault: Number(row.isDefault) === 1 ? 1 : 0,
  })
  dialogVisible.value = true
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const payload = {
    receiverName: form.receiverName,
    receiverPhone: form.receiverPhone,
    province: form.province,
    city: form.city,
    district: form.district,
    detailAddress: form.detailAddress,
    isDefault: Number(form.isDefault),
  }
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, payload)
      ElMessage.success('地址已更新')
    } else {
      await addAddress(payload)
      ElMessage.success('地址已添加')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除收货人「${row.receiverName}」的地址吗？`,
      '删除地址',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteAddress(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">收货地址</h3>
      <el-button type="primary" @click="openAdd">新增地址</el-button>
    </div>
    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="receiverName" label="收货人" width="120" />
        <el-table-column prop="receiverPhone" label="手机号" width="140" />
        <el-table-column label="地址" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ fullAddress(row) }}</template>
        </el-table-column>
        <el-table-column label="默认" width="90">
          <template #default="{ row }">
            <el-tag v-if="Number(row.isDefault) === 1" type="danger" size="small">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无收货地址" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑地址' : '新增地址'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="form.receiverName" placeholder="收货人姓名" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="receiverPhone">
          <el-input v-model="form.receiverPhone" placeholder="11 位手机号" clearable />
        </el-form-item>
        <el-form-item label="省份" prop="province">
          <el-input v-model="form.province" placeholder="如：广东省" clearable />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" placeholder="如：深圳市" clearable />
        </el-form-item>
        <el-form-item label="区/县" prop="district">
          <el-input v-model="form.district" placeholder="如：南山区" clearable />
        </el-form-item>
        <el-form-item label="详细地址" prop="detailAddress">
          <el-input v-model="form.detailAddress" placeholder="街道、门牌号等" clearable />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1000px;
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
</style>
