<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOtherUser } from '../api/user'
import { findProductsByUsername } from '../api/product'
import { myCoupons, receiveCoupon, storeCoupons } from '../api/coupon'
import { money } from '../utils/format'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const username = route.params.username

const loading = ref(true)
const seller = ref(null)
const list = ref([])
const page = ref(1)
const size = ref(12)

// 本店可领的券；已领取的用已领集合标记（后端 limit 为每人每券 1 张）
const coupons = ref([])
const receivedIds = ref(new Set())
const receiving = ref(false)

async function loadCoupons() {
  if (!username) return
  try {
    const [store, mine] = await Promise.all([storeCoupons(username), myCoupons(0)])
    coupons.value = store || []
    receivedIds.value = new Set((mine || []).map((uc) => String(uc.couponId)))
  } catch {
    coupons.value = []
  }
}

function face(c) {
  if (Number(c.couponType) === 2) {
    return `${Number((Number(c.discountRate || 1) * 10).toFixed(1))}折`
  }
  return `满${money(c.thresholdAmount)}减${money(c.discountAmount)}`
}

async function onClaim(c) {
  receiving.value = true
  try {
    await receiveCoupon(c.id)
    ElMessage.success('领取成功，可在「背包」中查看')
    await loadCoupons()
  } catch {
    // 错误提示由 api 拦截器统一处理（含「已领完」「您已领取过」）
  } finally {
    receiving.value = false
  }
}

async function loadSeller() {
  if (!username) return
  try {
    seller.value = (await getOtherUser(username)) || null
  } catch {
    seller.value = null
  }
}

async function load() {
  if (!username) return
  loading.value = true
  try {
    list.value = (await findProductsByUsername(page.value, size.value, username)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}

function nextPage() {
  page.value += 1
  load()
}

function isNormal() {
  return Number(seller.value?.status) === 1
}

onMounted(async () => {
  await loadSeller()
  loadCoupons()
  load()
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <el-button link type="primary" @click="router.back()">← 返回</el-button>
    </div>

    <el-card class="shop-card" shadow="never">
      <div class="shop-info">
        <el-avatar :size="56" :src="seller?.avatar || ''" class="shop-avatar">
          {{ (username || '?').charAt(0).toUpperCase() }}
        </el-avatar>
        <div class="shop-meta">
          <div class="shop-name">
            {{ username }}
            <el-tag v-if="isNormal()" type="success" size="small" class="st">正常</el-tag>
            <el-tag v-else-if="seller" type="danger" size="small" class="st">停用</el-tag>
          </div>
          <div class="shop-sub">卖家店铺 · 共 {{ list.length }} 件在售（本页）</div>
        </div>
      </div>
    </el-card>

    <!-- 店铺券：只在店铺页露出，券中心看不到（券中心只列平台券） -->
    <el-card v-if="coupons.length" class="coupon-card" shadow="never">
      <template #header>
        <div class="cc-head">
          <span class="cc-title">本店优惠券</span>
          <span class="cc-sub">下单时可在结算页选择使用</span>
        </div>
      </template>
      <div class="cc-list">
        <div v-for="c in coupons" :key="c.id" class="cc-item">
          <div class="cc-face">{{ face(c) }}</div>
          <div class="cc-meta">
            <div class="cc-name">{{ c.name }}</div>
            <div class="cc-stock">剩余 {{ c.totalCount - c.receivedCount }} 张</div>
          </div>
          <el-button v-if="receivedIds.has(String(c.id))" size="small" disabled>已领取</el-button>
          <el-button
            v-else
            type="danger"
            size="small"
            :loading="receiving"
            @click="onClaim(c)"
          >
            领取
          </el-button>
        </div>
      </div>
    </el-card>

    <div v-loading="loading" class="grid-wrap">
      <el-row v-if="list.length" :gutter="16">
        <el-col
          v-for="p in list"
          :key="p.id"
          :xs="12"
          :sm="12"
          :md="8"
          :lg="6"
          :xl="4"
        >
          <ProductCard :product="p" />
        </el-col>
      </el-row>
      <el-empty v-else-if="!loading" description="该店铺暂无在售商品" />
    </div>

    <div v-if="list.length" class="pager">
      <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
      <span class="pno">第 {{ page }} 页</span>
      <el-button @click="nextPage">下一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.shop-card {
  margin-bottom: 16px;
}
.shop-info {
  display: flex;
  align-items: center;
  gap: 16px;
}
.shop-avatar {
  background: #67c23a;
  color: #fff;
  font-size: 22px;
  flex-shrink: 0;
}
.shop-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.st {
  margin-left: 8px;
  vertical-align: 2px;
}
.shop-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.coupon-card {
  margin-bottom: 16px;
}
.cc-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.cc-title {
  font-weight: 600;
  color: #303133;
}
.cc-sub {
  color: #909399;
  font-size: 12px;
}
.cc-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}
.cc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-left: 4px solid #f56c6c;
  border-radius: 6px;
}
.cc-face {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
  white-space: nowrap;
}
.cc-meta {
  flex: 1;
  min-width: 0;
}
.cc-name {
  color: #303133;
  font-size: 14px;
}
.cc-stock {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.grid-wrap {
  min-height: 200px;
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
</style>
