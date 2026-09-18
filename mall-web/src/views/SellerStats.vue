<script setup>
// 我的商店数据：商家视角的经营看板。
// 数据全部来自既有接口，不新增后端：listSellerOrders（含本店商品的订单 + 本店明细行）、
// findMyProducts（自己发布的商品，带 total）。前端按天聚合后交给折线图。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listSellerOrders } from '../api/order'
import { findMyProducts } from '../api/product'
import LineChart from '../components/LineChart.vue'

const router = useRouter()

// 序列色：取自已通过 CVD / 对比度校验的分类色板前三位
const SERIES_1 = '#2a78d6' // 蓝
const SERIES_2 = '#eb6834' // 橙
const SERIES_3 = '#1baf7a' // 青

const RANGE_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

const loading = ref(true)
const orders = ref([])
const productStats = ref({ total: 0, onSale: 0, counted: 0, capped: false })
const range = ref(30)
const showTable = ref(false)

// 订单状态：4-已取消、6-已退款。两者不计入销售额与销量口径。
const STATUS_CANCELLED = 4
const STATUS_REFUNDED = 6
const STATUS_PENDING_SHIP = 1

const orderStatus = (vo) => Number(vo?.order?.orderStatus)

// 统计口径：排除已取消 / 已退款
const isCounted = (vo) => ![STATUS_CANCELLED, STATUS_REFUNDED].includes(orderStatus(vo))

// 本店在这笔订单中的金额与件数（混单时只算属于本店的明细行）
function sellerMetrics(vo) {
  let amount = 0
  let qty = 0
  for (const it of vo?.items || []) {
    const q = Number(it.quantity) || 0
    // total_price 理论上已落库；为兼容历史数据兜底用单价 × 数量
    const t = it.totalPrice != null ? Number(it.totalPrice) : Number(it.productPrice) * q
    amount += Number.isFinite(t) ? t : 0
    qty += q
  }
  return { amount, qty }
}

// ---------- 日期工具（一律用本地时区，避免 toISOString 的 UTC 偏移把日期挪一天） ----------

const pad = (n) => String(n).padStart(2, '0')
const keyOf = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

// 后端 LocalDateTime 形如 2026-09-18T20:55:00，取前 10 位即日期
const dayKey = (t) => (t ? String(t).slice(0, 10) : '')

function shiftDay(key, delta) {
  const [y, m, d] = key.split('-').map(Number)
  const dt = new Date(y, m - 1, d)
  dt.setDate(dt.getDate() + delta)
  return keyOf(dt)
}

// x 轴：连续日期，空缺日补 0，折线才不会跳过没下单的日子
const dayKeys = computed(() => {
  const end = keyOf(new Date())
  const keys = []
  for (let i = range.value - 1; i >= 0; i--) keys.push(shiftDay(end, -i))
  return keys
})

const daily = computed(() => {
  const keys = dayKeys.value
  const idx = new Map(keys.map((k, i) => [k, i]))
  const orderCount = new Array(keys.length).fill(0)
  const amount = new Array(keys.length).fill(0)
  const qty = new Array(keys.length).fill(0)

  for (const vo of orders.value) {
    if (!isCounted(vo)) continue
    const i = idx.get(dayKey(vo.order?.createdTime))
    if (i === undefined) continue // 落在所选区间之外
    orderCount[i] += 1
    const m = sellerMetrics(vo)
    amount[i] += m.amount
    qty[i] += m.qty
  }
  // 金额保留两位，避免浮点累加出现 12.340000000000002
  return { orderCount, amount: amount.map((v) => Number(v.toFixed(2))), qty }
})

// 概览卡：全量口径（不随时间范围变化），与图表口径保持一致
const totals = computed(() => {
  let amount = 0
  let qty = 0
  let count = 0
  let pending = 0
  for (const vo of orders.value) {
    const st = orderStatus(vo)
    // 待发货＝等商家处理，属于待办，不受统计口径影响
    if (st === STATUS_PENDING_SHIP && !vo.shipInfo) pending += 1
    if (!isCounted(vo)) continue
    count += 1
    const m = sellerMetrics(vo)
    amount += m.amount
    qty += m.qty
  }
  return { amount: Number(amount.toFixed(2)), qty, count, pending }
})

const charts = computed(() => [
  { title: '每日订单量', unit: '单', valueType: 'count', color: SERIES_1, data: daily.value.orderCount },
  { title: '每日销售额', unit: '元', valueType: 'money', color: SERIES_2, data: daily.value.amount },
  { title: '每日销售件数', unit: '件', valueType: 'count', color: SERIES_3, data: daily.value.qty },
])

// 折线图的等价表格视图：图上的每个值都能逐日读到
const tableRows = computed(() =>
  dayKeys.value.map((k, i) => ({
    day: k,
    orderCount: daily.value.orderCount[i],
    amount: daily.value.amount[i],
    qty: daily.value.qty[i],
  })),
)

function grouped(value) {
  const n = Number(value) || 0
  const s = n.toFixed(2)
  const [int, dec] = s.split('.')
  return `${int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${dec}`
}

// 加载时间：日期工具已按本地时区拼装，直接复用，避免 new Date() 的 toString 格式不可控
const loadedAt = ref('')
const nowText = () => {
  const d = new Date()
  return `${keyOf(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---------- 加载 ----------

const PRODUCT_PAGE = 100 // 后端 normSize 上限
const MAX_PRODUCT_PAGES = 10

// 商品总数由接口 total 给出；「在售」需要翻完全部商品才能统计。
// 超过分页上限时如实标注「≥」，不做静默截断。
async function loadProductStats() {
  const first = await findMyProducts(1, PRODUCT_PAGE)
  const total = Number(first?.total || 0)
  if (total === 0) return { total: 0, onSale: 0, counted: 0, capped: false }

  let items = first?.items || []
  const totalPages = Math.ceil(total / PRODUCT_PAGE)
  const pages = Math.min(totalPages, MAX_PRODUCT_PAGES)
  for (let p = 2; p <= pages; p++) {
    const res = await findMyProducts(p, PRODUCT_PAGE)
    items = items.concat(res?.items || [])
  }
  return {
    total,
    onSale: items.filter((p) => Number(p.status) === 1).length,
    counted: items.length,
    capped: totalPages > MAX_PRODUCT_PAGES,
  }
}

async function load() {
  loading.value = true
  // 商品统计失败不该拖垮整个看板，降级为 0 并继续渲染订单部分
  const [orderRes, productRes] = await Promise.allSettled([
    listSellerOrders(),
    loadProductStats(),
  ])
  orders.value = orderRes.status === 'fulfilled' ? orderRes.value || [] : []
  productStats.value =
    productRes.status === 'fulfilled'
      ? productRes.value
      : { total: 0, onSale: 0, counted: 0, capped: false }
  loadedAt.value = nowText()
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">我的商店数据</h3>
      <div class="head-actions">
        <el-button @click="router.push('/seller/orders')">查看商品订单</el-button>
        <el-button @click="router.push('/seller')">← 返回我的商品</el-button>
      </div>
    </div>

    <!-- 筛选行：一行统管下方所有图表 -->
    <div class="filter-bar">
      <span class="fb-label">时间范围</span>
      <el-radio-group v-model="range" size="small">
        <el-radio-button v-for="o in RANGE_OPTIONS" :key="o.value" :value="o.value">
          {{ o.label }}
        </el-radio-button>
      </el-radio-group>
      <span class="fb-hint">口径：排除已取消 / 已退款订单；销售额与件数按本店商品明细合计</span>
    </div>

    <div v-loading="loading" class="body">
      <!-- 概览：累计销售额为全页唯一的主数字 -->
      <div class="tiles">
        <el-card shadow="never" class="tile tile-hero">
          <div class="tile-label">累计销售额</div>
          <div class="hero-value">¥{{ grouped(totals.amount) }}</div>
          <div class="tile-sub">全部时间 · 已排除取消与退款</div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">订单总数</div>
          <div class="tile-value">{{ totals.count }}</div>
          <div class="tile-sub">含本店商品的订单</div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">销售件数</div>
          <div class="tile-value">{{ totals.qty }}</div>
          <div class="tile-sub">本店商品明细合计</div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">待发货</div>
          <div class="tile-value">{{ totals.pending }}</div>
          <div class="tile-sub">等待你处理</div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">商品总数</div>
          <div class="tile-value">{{ productStats.total }}</div>
          <div class="tile-sub">
            <template v-if="productStats.total">
              在售 {{ productStats.capped ? '≥ ' : '' }}{{ productStats.onSale }} 件
            </template>
            <template v-else>还没发布过商品</template>
          </div>
        </el-card>
      </div>

      <el-alert
        v-if="!loading && !orders.length"
        type="info"
        show-icon
        :closable="false"
        class="empty-tip"
        title="还没有买家下过你商品的订单"
        description="发布商品并被下单后，这里的订单量与销售额折线就会有数据。"
      />

      <!-- 订单维度折线：三个量纲不同，各自一张图，不做双 Y 轴 -->
      <el-card v-for="c in charts" :key="c.title" shadow="never" class="chart-card">
        <template #header>
          <div class="card-hdr">
            <span class="ct">{{ c.title }}</span>
            <span class="ct-sub">{{ c.unit }} · 近 {{ range }} 天</span>
          </div>
        </template>
        <LineChart
          :labels="dayKeys"
          :series="[{ name: c.title, color: c.color, data: c.data }]"
          :value-type="c.valueType"
        />
      </el-card>

      <!-- 表格视图：折线图背后的逐日数值 -->
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-hdr">
            <span class="ct">按日期明细</span>
            <span class="ct-sub">近 {{ range }} 天</span>
            <el-button link type="primary" class="ct-action" @click="showTable = !showTable">
              {{ showTable ? '收起表格' : '展开表格' }}
            </el-button>
          </div>
        </template>
        <el-table v-if="showTable" :data="tableRows" size="small" style="width: 100%">
          <el-table-column prop="day" label="日期" min-width="120" />
          <el-table-column label="订单量（单）" min-width="110" align="right">
            <template #default="{ row }">{{ row.orderCount }}</template>
          </el-table-column>
          <el-table-column label="销售额（元）" min-width="130" align="right">
            <template #default="{ row }">¥{{ grouped(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="销售件数（件）" min-width="120" align="right">
            <template #default="{ row }">{{ row.qty }}</template>
          </el-table-column>
        </el-table>
        <div v-else class="ct-hint">折线图背后的逐日数值，展开可逐条核对。</div>
      </el-card>

      <div v-if="!loading" class="foot-hint">
        数据口径与上方折线一致（排除已取消 / 已退款）；本次加载：{{ loadedAt || '-' }}
      </div>
    </div>
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
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  margin: 0;
}
.head-actions {
  display: flex;
  gap: 8px;
}

/* 筛选行在图表之上，统一作用域 */
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 6px;
}
.fb-label {
  color: #52514e;
  font-size: 13px;
}
.fb-hint {
  color: #898781;
  font-size: 12px;
}

.body {
  min-height: 240px;
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.tile {
  border-radius: 6px;
}
.tile-hero {
  grid-column: span 2;
}
.tile-label {
  color: #52514e;
  font-size: 13px;
  margin-bottom: 8px;
}
/* 大号数字用比例字形，tabular-nums 只留给需要竖向对齐的表格 */
.hero-value {
  font-size: 34px;
  font-weight: 600;
  line-height: 1.15;
  color: #0b0b0b;
  word-break: break-all;
}
.tile-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: #0b0b0b;
}
.tile-sub {
  margin-top: 6px;
  color: #898781;
  font-size: 12px;
}

.empty-tip {
  margin-bottom: 16px;
}

.chart-card {
  margin-bottom: 16px;
  border-radius: 6px;
}
.card-hdr {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.ct {
  font-weight: 600;
  color: #0b0b0b;
}
.ct-sub {
  color: #898781;
  font-size: 12px;
}
.ct-action {
  margin-left: auto;
}
.ct-hint {
  color: #898781;
  font-size: 13px;
}

.foot-hint {
  color: #898781;
  font-size: 12px;
  text-align: right;
  padding-bottom: 8px;
}

@media (max-width: 720px) {
  .tile-hero {
    grid-column: span 1;
  }
}
</style>
