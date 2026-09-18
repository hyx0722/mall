<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createCoupon, listCoupons, updateCouponStatus } from '../api/admin'

const loading = ref(false)
const keyword = ref('')
const rows = ref([])

const dialog = ref(false)
const submitting = ref(false)

const TYPE_THRESHOLD = 1
const TYPE_DISCOUNT = 2

// 适用范围：0-全场通用（不写 coupon_scope），1-指定商品，2-指定分类
const form = reactive({
  name: '',
  couponType: TYPE_THRESHOLD,
  thresholdAmount: 100,
  discountAmount: 10,
  discountRate: 0.85,
  maxDiscountAmount: null,
  totalCount: 100,
  range: [null, null],
  scopeType: 0,
  scopeIds: '',
})

const fmt = (v) => (v == null ? '-' : Number(v).toFixed(2))
const day = (s) => (s ? String(s).slice(0, 16).replace('T', ' ') : '-')

function rule(c) {
  if (Number(c.couponType) === TYPE_DISCOUNT) {
    return `${Number((Number(c.discountRate || 1) * 10).toFixed(1))}折`
  }
  return `满${fmt(c.thresholdAmount)}减${fmt(c.discountAmount)}`
}

async function load() {
  loading.value = true
  try {
    rows.value = (await listCoupons(keyword.value || undefined)) || []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    name: '',
    couponType: TYPE_THRESHOLD,
    thresholdAmount: 100,
    discountAmount: 10,
    discountRate: 0.85,
    maxDiscountAmount: null,
    totalCount: 100,
    range: [null, null],
    scopeType: 0,
    scopeIds: '',
  })
  dialog.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写券名称')
    return
  }
  if (!form.range?.[0] || !form.range?.[1]) {
    ElMessage.warning('请选择生效起止时间')
    return
  }
  // 指定商品/分类时把逗号分隔的 id 展开成 scopes 数组
  let scopes = []
  if (Number(form.scopeType) !== 0) {
    const ids = String(form.scopeIds)
      .split(',')
      .map((s) => Number(s.trim()))
      .filter((n) => Number.isFinite(n) && n > 0)
    if (!ids.length) {
      ElMessage.warning('请填写至少一个商品 / 分类 ID')
      return
    }
    scopes = ids.map((id) => ({ scopeType: Number(form.scopeType), scopeId: id }))
  }

  submitting.value = true
  try {
    await createCoupon({
      name: form.name.trim(),
      couponType: Number(form.couponType),
      thresholdAmount: Number(form.couponType) === TYPE_THRESHOLD ? form.thresholdAmount : 0,
      discountAmount: Number(form.couponType) === TYPE_THRESHOLD ? form.discountAmount : 0,
      discountRate: Number(form.couponType) === TYPE_DISCOUNT ? form.discountRate : 1,
      maxDiscountAmount: form.maxDiscountAmount || null,
      totalCount: form.totalCount,
      startTime: form.range[0],
      endTime: form.range[1],
      scopes,
    })
    ElMessage.success('创建成功')
    dialog.value = false
    await load()
  } catch {
    // 错误提示由 api 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(c) {
  const next = Number(c.status) === 1 ? 0 : 1
  try {
    await updateCouponStatus(c.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    await load()
  } catch {
    // 忽略
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="bar">
      <el-input
        v-model="keyword"
        placeholder="按券名称搜索"
        clearable
        style="width: 240px"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button type="danger" @click="openCreate">新建优惠券</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="券名称" min-width="140" />
      <el-table-column label="规则" width="140">
        <template #default="{ row }">{{ rule(row) }}</template>
      </el-table-column>
      <el-table-column label="折扣封顶" width="100">
        <template #default="{ row }">{{ row.maxDiscountAmount == null ? '不封顶' : fmt(row.maxDiscountAmount) }}</template>
      </el-table-column>
      <el-table-column label="已领 / 总量" width="110">
        <template #default="{ row }">{{ row.receivedCount }} / {{ row.totalCount }}</template>
      </el-table-column>
      <el-table-column label="有效期" min-width="220">
        <template #default="{ row }">{{ day(row.startTime) }} ~ {{ day(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
            {{ Number(row.status) === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleStatus(row)">
            {{ Number(row.status) === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新建优惠券" width="560px">
      <el-form label-width="110px">
        <el-form-item label="券名称">
          <el-input v-model="form.name" placeholder="如：新客立减券" maxlength="100" />
        </el-form-item>
        <el-form-item label="券类型">
          <el-radio-group v-model="form.couponType">
            <el-radio-button :value="TYPE_THRESHOLD">满减</el-radio-button>
            <el-radio-button :value="TYPE_DISCOUNT">折扣</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="Number(form.couponType) === TYPE_THRESHOLD">
          <el-form-item label="门槛金额">
            <el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="抵扣金额">
            <el-input-number v-model="form.discountAmount" :min="0.01" :precision="2" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="折扣率">
            <el-input-number v-model="form.discountRate" :min="0.01" :max="0.99" :step="0.05" :precision="2" />
            <span class="hint">{{ Number((Number(form.discountRate) * 10).toFixed(1)) }} 折</span>
          </el-form-item>
          <el-form-item label="折扣封顶">
            <el-input-number v-model="form.maxDiscountAmount" :min="0" :precision="2" />
            <span class="hint">留空即不封顶</span>
          </el-form-item>
        </template>

        <el-form-item label="发放总量">
          <el-input-number v-model="form.totalCount" :min="1" />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="form.scopeType" style="width: 140px">
            <el-option :value="0" label="全场通用" />
            <el-option :value="1" label="指定商品" />
            <el-option :value="2" label="指定分类" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="Number(form.scopeType) !== 0" label="ID 列表">
          <el-input v-model="form.scopeIds" placeholder="逗号分隔，如 1,2,3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 4px;
}
.bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}
.spacer {
  flex: 1;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
