<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { couponCenter, myCoupons, receiveCoupon } from '../api/coupon'
import { money } from '../utils/format'

const tab = ref('center')
const centerList = ref([])
const mineList = ref([])
const loading = ref(true)
const receiving = ref(false)

const MINE_TABS = [
  { key: 0, label: '未使用' },
  { key: 1, label: '已使用' },
  { key: 2, label: '已过期' },
]
const mineStatus = ref(0)

// 券的展示文案：满减写「满100减20」，折扣写「8.5折」
function rule(c) {
  if (!c) return ''
  if (Number(c.couponType) === 2) {
    const rate = Number(c.discountRate || 1) * 10
    return `${Number(rate.toFixed(1))}折`
  }
  return `满${money(c.thresholdAmount)}减${money(c.discountAmount)}`
}

// 已领取的券不展示「领取」按钮，避免用户点了才被告知重复
const receivedIds = ref(new Set())

function day(s) {
  return s ? String(s).slice(0, 10) : ''
}

async function loadMine() {
  mineList.value = (await myCoupons(mineStatus.value)) || []
  if (mineStatus.value === 0) {
    receivedIds.value = new Set(mineList.value.map((uc) => String(uc.couponId)))
  }
}

async function loadCenter() {
  centerList.value = (await couponCenter()) || []
  // 券中心只返回「还能领」的券，与已领取集合求交即可标出「已领取」
  const mine = (await myCoupons(0)) || []
  receivedIds.value = new Set(mine.map((uc) => String(uc.couponId)))
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadCenter(), loadMine()])
  } catch {
    centerList.value = []
    mineList.value = []
  } finally {
    loading.value = false
  }
}

async function onReceive(c) {
  receiving.value = true
  try {
    await receiveCoupon(c.id)
    ElMessage.success('领取成功')
    await load()
  } catch {
    // 错误提示由 api 拦截器统一处理（含「已领完」「您已领取过」）
  } finally {
    receiving.value = false
  }
}

async function onMineTab(key) {
  mineStatus.value = key
  try {
    await loadMine()
  } catch {
    mineList.value = []
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="券中心" name="center">
        <div v-loading="loading" class="grid">
          <el-empty v-if="!loading && !centerList.length" description="暂无可领取的优惠券" />
          <el-card v-for="c in centerList" :key="c.id" class="coupon" shadow="hover">
            <div class="left">
              <div class="value">{{ rule(c) }}</div>
              <div class="name">{{ c.name }}</div>
              <div class="time">{{ day(c.startTime) }} ~ {{ day(c.endTime) }}</div>
            </div>
            <div class="right">
              <div class="stock">剩余 {{ c.totalCount - c.receivedCount }} 张</div>
              <el-button
                v-if="receivedIds.has(String(c.id))"
                size="small"
                disabled
              >
                已领取
              </el-button>
              <el-button
                v-else
                type="danger"
                size="small"
                :loading="receiving"
                @click="onReceive(c)"
              >
                立即领取
              </el-button>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的券" name="mine">
        <el-radio-group v-model="mineStatus" class="mine-tabs" @change="onMineTab">
          <el-radio-button v-for="t in MINE_TABS" :key="t.key" :value="t.key">
            {{ t.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="grid">
          <el-empty v-if="!mineList.length" description="这里还没有券" />
          <el-card v-for="uc in mineList" :key="uc.id" class="coupon" shadow="never">
            <div class="left">
              <div class="value">{{ rule(uc.coupon) }}</div>
              <div class="name">{{ uc.coupon?.name }}</div>
              <div class="time">有效期至 {{ day(uc.coupon?.endTime) }}</div>
            </div>
            <div class="right">
              <el-tag v-if="Number(uc.status) === 0" type="success" size="small">未使用</el-tag>
              <el-tag v-else-if="Number(uc.status) === 1" type="info" size="small">已使用</el-tag>
              <el-tag v-else type="warning" size="small">已过期</el-tag>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page {
  max-width: 860px;
  margin: 0 auto;
}
.grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}
.mine-tabs {
  margin-bottom: 12px;
}
.coupon {
  border-left: 4px solid #f56c6c;
}
.coupon :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.value {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}
.name {
  margin-top: 4px;
  color: #303133;
}
.time {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.stock {
  font-size: 12px;
  color: #909399;
}
</style>
