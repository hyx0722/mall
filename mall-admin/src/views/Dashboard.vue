<script setup>
// 管理后台数据看板：order 侧聚合 + user/product/inventory 各自的 stats，
// 前端并行拉取后组合。任一服务失败不影响其余卡片渲染。
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getInventoryStats,
  getOrderDashboard,
  getProductStats,
  getUserStats,
} from '../api/dashboard'
import { orderStatusTag } from '../utils/format'
import Chart from '../components/Chart.vue'

const router = useRouter()

const RANGE_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

const loading = ref(true)
const range = ref(7)
const loadedAt = ref('')

const order = ref(null)
const users = ref(null)
const products = ref(null)
const inventory = ref(null)

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

const int = (v) => num(v).toLocaleString('zh-CN')

// 金额千分位 + 两位小数；看板主数字不保留分位噪声时另用 int
const yuan = (v) => {
  const s = num(v).toFixed(2)
  const [i, d] = s.split('.')
  return `¥${i.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${d}`
}

const summary = computed(() => order.value?.summary || {})
const refund = computed(() => order.value?.refund || {})
const userSummary = computed(() => users.value?.summary || {})
const productStats = computed(() => products.value || {})
const inventoryStats = computed(() => inventory.value || {})

const trendRows = computed(() => order.value?.trend || [])
const labels = computed(() => trendRows.value.map((r) => String(r.day || '').slice(5)))
const orderCounts = computed(() => trendRows.value.map((r) => num(r.orderCount)))
const gmvSeries = computed(() => trendRows.value.map((r) => num(r.gmv)))
const userCounts = computed(() => {
  const byDay = new Map((users.value?.trend || []).map((r) => [r.day, num(r.count)]))
  return trendRows.value.map((r) => byDay.get(r.day) ?? 0)
})

const axisCommon = {
  axisTick: { show: false },
  axisLine: { lineStyle: { color: '#dcdfe6' } },
  axisLabel: { color: '#909399', fontSize: 12 },
}

const yAxisCommon = {
  type: 'value',
  minInterval: 1,
  axisLine: { show: false },
  axisTick: { show: false },
  splitLine: { lineStyle: { color: '#ebeef5' } },
  axisLabel: { color: '#909399', fontSize: 12 },
}

const activityOption = computed(() => ({
  color: ['#409eff', '#67c23a'],
  grid: { left: 10, right: 20, top: 38, bottom: 4, containLabel: true },
  tooltip: { trigger: 'axis' },
  legend: {
    top: 0,
    right: 0,
    icon: 'roundRect',
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { color: '#606266', fontSize: 12 },
  },
  xAxis: { type: 'category', boundaryGap: false, data: labels.value, ...axisCommon },
  yAxis: yAxisCommon,
  series: [
    {
      name: '订单量',
      type: 'line',
      data: orderCounts.value,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: labels.value.length <= 31,
      lineStyle: { width: 2 },
      areaStyle: { color: 'rgba(64,158,255,0.10)' },
    },
    {
      name: '新增用户',
      type: 'line',
      data: userCounts.value,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: labels.value.length <= 31,
      lineStyle: { width: 2 },
    },
  ],
}))

const gmvOption = computed(() => ({
  color: ['#e6a23c'],
  grid: { left: 10, right: 24, top: 20, bottom: 4, containLabel: true },
  tooltip: { trigger: 'axis', valueFormatter: (v) => yuan(v) },
  xAxis: { type: 'category', boundaryGap: false, data: labels.value, ...axisCommon },
  yAxis: { ...yAxisCommon, axisLabel: { ...yAxisCommon.axisLabel, formatter: (v) => `¥${v}` } },
  series: [
    {
      name: '销售额',
      type: 'line',
      data: gmvSeries.value,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: labels.value.length <= 31,
      lineStyle: { width: 2 },
      itemStyle: { color: '#e6a23c' },
      areaStyle: { color: 'rgba(230,162,60,0.10)' },
    },
  ],
}))

const statusRows = computed(() =>
  (order.value?.statusDistribution || []).filter((r) => num(r.count) > 0),
)

const statusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 单（{d}%）' },
  legend: { bottom: 0, icon: 'circle', itemWidth: 9, itemHeight: 9, textStyle: { color: '#606266', fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { color: '#606266', formatter: '{b}\n{c}' },
      data: statusRows.value.map((r) => ({ name: r.label, value: num(r.count) })),
    },
  ],
}))

const topRows = computed(() => order.value?.topProducts || [])

const topOption = computed(() => {
  const rows = [...topRows.value].reverse()
  return {
    grid: { left: 10, right: 52, top: 12, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = params?.[0]
        const row = rows[p?.dataIndex]
        if (!row) return ''
        return `${row.productName}<br/>销量：${int(row.quantity)} 件<br/>金额：${yuan(row.amount)}`
      },
    },
    xAxis: { ...yAxisCommon },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.productName),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#606266', fontSize: 12, width: 110, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => num(r.quantity)),
        barMaxWidth: 18,
        itemStyle: { color: '#67c23a', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: '#606266', fontSize: 12 },
      },
    ],
  }
})

const recent = computed(() => order.value?.recentOrders || [])
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

async function load() {
  loading.value = true
  const results = await Promise.allSettled([
    getOrderDashboard(range.value, true),
    getUserStats(range.value, true),
    getProductStats(true),
    getInventoryStats(true),
  ])

  order.value = results[0].status === 'fulfilled' ? results[0].value : null
  users.value = results[1].status === 'fulfilled' ? results[1].value : null
  products.value = results[2].status === 'fulfilled' ? results[2].value : null
  inventory.value = results[3].status === 'fulfilled' ? results[3].value : null

  const failed = results.filter((r) => r.status === 'rejected').length
  if (failed > 0) {
    // 拦截器已按 _silent 静默，这里只提示一次，避免 4 个服务各弹一个 toast
    ElMessage.warning(`有 ${failed} 项统计数据暂不可用，已展示其余数据`)
  }

  loadedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  loading.value = false
}

onMounted(load)
watch(range, load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div>
        <h3 class="title">数据看板</h3>
        <p class="subtitle">交易、用户、商品与库存的实时概览</p>
      </div>
      <div class="tools">
        <el-radio-group v-model="range" size="small">
          <el-radio-button v-for="o in RANGE_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="body">
      <div class="tiles">
        <el-card shadow="never" class="tile tile-hero">
          <div class="tile-label">累计 GMV</div>
          <div class="hero-value">{{ yuan(summary.gmv) }}</div>
          <div class="tile-sub">
            今日 {{ yuan(summary.todayGmv) }} · 已支付订单 {{ int(summary.paidOrders) }} 单
          </div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">订单总数</div>
          <div class="tile-value">{{ int(summary.totalOrders) }}</div>
          <div class="tile-sub">今日新增 {{ int(summary.todayOrders) }} 单</div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">用户总数</div>
          <div class="tile-value">{{ int(userSummary.totalUsers) }}</div>
          <div class="tile-sub">今日注册 {{ int(userSummary.todayNew) }} 人</div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">商品总数</div>
          <div class="tile-value">{{ int(productStats.totalProducts) }}</div>
          <div class="tile-sub">
            在售 {{ int(productStats.onSale) }} · 分类 {{ int(productStats.categoryCount) }}
          </div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">待发货</div>
          <div class="tile-value">{{ int(summary.waitShip) }}</div>
          <div class="tile-sub">待收货 {{ int(summary.waitReceive) }} 单</div>
        </el-card>
        <el-card shadow="never" class="tile clickable" @click="router.push('/refunds')">
          <div class="tile-label">退款待审核</div>
          <div class="tile-value">{{ int(refund.pending) }}</div>
          <div class="tile-sub">累计已退 {{ yuan(refund.refundedAmount) }}</div>
        </el-card>
        <el-card shadow="never" class="tile clickable" @click="router.push('/inventory')">
          <div class="tile-label">库存预警</div>
          <div class="tile-value">{{ int(inventoryStats.lowStock) }}</div>
          <div class="tile-sub">
            缺货 {{ int(inventoryStats.outOfStock) }} · 可用 {{ int(inventoryStats.availableStock) }}
          </div>
        </el-card>
        <el-card shadow="never" class="tile">
          <div class="tile-label">待付款</div>
          <div class="tile-value">{{ int(summary.waitPay) }}</div>
          <div class="tile-sub">已取消 {{ int(summary.cancelled) }} 单</div>
        </el-card>
      </div>

      <div class="chart-grid">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-hdr">
              <span>订单量与新增用户</span>
              <span class="hint">近 {{ range }} 天</span>
            </div>
          </template>
          <Chart :option="activityOption" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-hdr">
              <span>销售额趋势</span>
              <span class="hint">实付口径 · 近 {{ range }} 天</span>
            </div>
          </template>
          <Chart :option="gmvOption" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-hdr">
              <span>订单状态分布</span>
              <span class="hint">全部订单</span>
            </div>
          </template>
          <Chart v-if="statusRows.length" :option="statusOption" height="300px" />
          <el-empty v-else description="暂无订单数据" :image-size="80" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-hdr">
              <span>热销商品 Top 5</span>
              <span class="hint">按销量 · 近 {{ range }} 天</span>
            </div>
          </template>
          <Chart v-if="topRows.length" :option="topOption" height="300px" />
          <el-empty v-else description="暂无已支付订单" :image-size="80" />
        </el-card>
      </div>

      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-hdr">
            <span>最近订单</span>
            <el-button link type="primary" class="more" @click="router.push('/orders')">查看全部</el-button>
          </div>
        </template>
        <el-table :data="recent" size="small" style="width: 100%" @row-click="() => router.push('/orders')">
          <el-table-column prop="orderNo" label="订单号" min-width="190" />
          <el-table-column label="实付金额" width="120" align="right">
            <template #default="{ row }">{{ yuan(Number(row.totalAmount) - Number(row.discountAmount)) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="orderStatusTag(row.orderStatus).type" size="small">
                {{ orderStatusTag(row.orderStatus).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createdTime) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无订单" :image-size="60" />
          </template>
        </el-table>
      </el-card>

      <div class="foot-hint">
        GMV 与销售额按「原价 − 优惠」的实付口径统计，仅含已支付状态（待发货/待收货/已完成/退款中/已退款）；
        本次加载：{{ loadedAt || '-' }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1180px;
  margin: 0 auto;
}
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin: 4px 0 14px;
}
.title {
  margin: 0;
}
.subtitle {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}
.tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.body {
  min-height: 320px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.tile {
  border-radius: 6px;
}
.tile-hero {
  grid-column: span 2;
}
.clickable {
  cursor: pointer;
}
.clickable:hover {
  border-color: #409eff;
}
.tile-label {
  color: #606266;
  font-size: 13px;
  margin-bottom: 8px;
}
.hero-value {
  font-size: 32px;
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
  color: #909399;
  font-size: 12px;
}
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.chart-card {
  border-radius: 6px;
}
.card-hdr {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}
.hint {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
.more {
  font-weight: 400;
}
.table-card {
  border-radius: 6px;
  margin-bottom: 10px;
}
.table-card :deep(.el-table__row) {
  cursor: pointer;
}
.foot-hint {
  color: #909399;
  font-size: 12px;
  text-align: right;
  padding-bottom: 8px;
}

@media (max-width: 860px) {
  .tile-hero {
    grid-column: span 1;
  }
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
