<script setup>
import { computed, onMounted, ref } from 'vue'
import { myCoupons } from '../api/coupon'
import { money } from '../utils/format'

// 背包：当前只有优惠券一种持有物，但按「分类 + 物品格」的结构组织，
// 后续加卡券/道具时不用重排页面。
const loading = ref(true)
const items = ref([])

const GROUPS = [
  { key: 0, label: '可用', hint: '下单时可在结算页选择' },
  { key: 1, label: '已使用', hint: '已核销到订单' },
  { key: 2, label: '已过期', hint: '已超出有效期' },
]

const grouped = computed(() =>
  GROUPS.map((g) => ({
    ...g,
    list: items.value.filter((it) => Number(it.status) === g.key),
  })),
)

// 券面文案：满减写「满100减20」，折扣写「8.5折」
function face(c) {
  if (!c) return ''
  if (Number(c.couponType) === 2) {
    return `${Number((Number(c.discountRate || 1) * 10).toFixed(1))}折`
  }
  return `满${money(c.thresholdAmount)}减${money(c.discountAmount)}`
}

const day = (s) => (s ? String(s).slice(0, 10) : '-')

// 未过期的可用券提示：背包一打开就该知道「有几张能马上用」
const usableCount = computed(() => grouped.value[0].list.length)

async function load() {
  loading.value = true
  try {
    items.value = (await myCoupons()) || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">我的背包</h3>
      <span class="sub">优惠券 · 可用 {{ usableCount }} 张</span>
    </div>

    <div v-loading="loading">
      <div v-for="g in grouped" :key="g.key" class="group">
        <div class="group-head">
          <span class="g-label">{{ g.label }}</span>
          <span class="g-count">{{ g.list.length }}</span>
          <span class="g-hint">{{ g.hint }}</span>
        </div>
        <el-empty v-if="!g.list.length" description="空" :image-size="50" />
        <div v-else class="slots">
          <div
            v-for="uc in g.list"
            :key="uc.id"
            class="slot"
            :class="{ dim: Number(uc.status) !== 0 }"
          >
            <div class="face">{{ face(uc.coupon) }}</div>
            <div class="cname">{{ uc.coupon?.name }}</div>
            <div class="time">有效期至 {{ day(uc.coupon?.endTime) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
}
.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 4px 0 14px;
}
.title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}
.sub {
  color: #909399;
  font-size: 13px;
}
.group {
  margin-bottom: 20px;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.g-label {
  font-weight: 600;
  color: #303133;
}
.g-count {
  color: #909399;
  font-size: 13px;
}
.g-hint {
  color: #c0c4cc;
  font-size: 12px;
}
.slots {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.slot {
  border: 1px solid #ebeef5;
  border-left: 4px solid #f56c6c;
  border-radius: 6px;
  padding: 12px;
  background: #fff;
}
/* 已用/已过期的格子压暗，但仍占位——背包里东西还在，只是不可用 */
.slot.dim {
  opacity: 0.5;
  border-left-color: #c0c4cc;
  background: #fafafa;
}
.face {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}
.slot.dim .face {
  color: #909399;
}
.cname {
  margin-top: 6px;
  color: #303133;
  font-size: 14px;
}
.time {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
