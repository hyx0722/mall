<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSellerCoupon, sellerCoupons, updateSellerCouponStatus } from '../api/coupon'
import { findMyProducts } from '../api/product'
import { money } from '../utils/format'

const router = useRouter()

const TYPE_THRESHOLD = 1
const TYPE_DISCOUNT = 2

const loading = ref(true)
const rows = ref([])

// 可发券的商品：一次拉自己的商品列表供勾选。
// 后端还会**逐个向商品服务再校验一次归属**，所以这里即使被绕过也发不出别人的券。
const myProducts = ref([])
const productsLoading = ref(false)

const dialog = ref(false)
const submitting = ref(false)

const form = reactive({
  name: '',
  couponType: TYPE_THRESHOLD,
  thresholdAmount: 100,
  discountAmount: 10,
  discountRate: 0.85,
  maxDiscountAmount: null,
  totalCount: 100,
  range: [null, null],
  productIds: [],
})

const rule = (c) =>
  Number(c.couponType) === TYPE_DISCOUNT
    ? `${Number((Number(c.discountRate || 1) * 10).toFixed(1))}折`
    : `满${money(c.thresholdAmount)}减${money(c.discountAmount)}`

const day = (s) => (s ? String(s).slice(0, 16).replace('T', ' ') : '-')

const onShelfProducts = computed(() => myProducts.value.filter((p) => Number(p.status) === 1))

async function load() {
  loading.value = true
  try {
    rows.value = (await sellerCoupons()) || []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  productsLoading.value = true
  try {
    const res = await findMyProducts(1, 100)
    myProducts.value = res?.items || []
  } catch {
    myProducts.value = []
  } finally {
    productsLoading.value = false
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
    productIds: [],
  })
  dialog.value = true
  if (!myProducts.value.length) loadProducts()
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写券名称')
    return
  }
  if (!form.productIds.length) {
    ElMessage.warning('请至少选择一个商品——商家券只能作用于自己的商品')
    return
  }
  if (!form.range?.[0] || !form.range?.[1]) {
    ElMessage.warning('请选择生效起止时间')
    return
  }
  submitting.value = true
  try {
    await createSellerCoupon({
      name: form.name.trim(),
      couponType: Number(form.couponType),
      thresholdAmount: Number(form.couponType) === TYPE_THRESHOLD ? form.thresholdAmount : 0,
      discountAmount: Number(form.couponType) === TYPE_THRESHOLD ? form.discountAmount : 0,
      discountRate: Number(form.couponType) === TYPE_DISCOUNT ? form.discountRate : 1,
      maxDiscountAmount: form.maxDiscountAmount || null,
      totalCount: form.totalCount,
      startTime: form.range[0],
      endTime: form.range[1],
      // 商家券只允许「指定商品」，scopeType 恒为 1
      scopes: form.productIds.map((id) => ({ scopeType: 1, scopeId: id })),
    })
    ElMessage.success('发券成功，买家可在你的店铺页领取')
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
    await updateSellerCouponStatus(c.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    await load()
  } catch {
    // 忽略
  }
}

onMounted(async () => {
  await Promise.all([load(), loadProducts()])
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <el-button link type="primary" @click="router.push('/seller')">← 返回我的商品</el-button>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="hdr">
          <div>
            <span class="t">我的优惠券</span>
            <span class="sub">券只能作用于你发布的商品，买家在你的店铺页领取</span>
          </div>
          <el-button type="danger" @click="openCreate">发布优惠券</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="券名称" min-width="140" />
        <el-table-column label="规则" width="140">
          <template #default="{ row }">{{ rule(row) }}</template>
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
      <el-empty v-if="!loading && !rows.length" description="还没有发过券" :image-size="70" />
    </el-card>

    <el-dialog v-model="dialog" title="发布优惠券" width="620px" top="6vh">
      <el-form label-width="110px">
        <el-form-item label="券名称">
          <el-input v-model="form.name" placeholder="如：本店满减券" maxlength="100" />
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

        <el-form-item label="适用商品">
          <el-select
            v-model="form.productIds"
            multiple
            filterable
            :loading="productsLoading"
            placeholder="从我的商品中选择（至少一个）"
            style="width: 100%"
          >
            <el-option
              v-for="p in onShelfProducts"
              :key="p.id"
              :value="p.id"
              :label="`${p.name}（¥${money(p.price)}）`"
            />
          </el-select>
        </el-form-item>
        <div class="tip">
          只列出<b>在售</b>的商品；已下架的商品即便发了券买家也用不了，故不提供选择。
        </div>
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
  max-width: 1100px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.t {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}
.sub {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.tip {
  margin-left: 110px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
