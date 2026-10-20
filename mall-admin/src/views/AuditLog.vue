<script setup>
// 管理员操作审计：每个服务在自己的库里有 audit_log，这里按服务查询；
// 「全部服务」是在前端把各服务最近 100 条合并后的聚合视图。
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { AUDIT_SERVICES, listAuditLogs } from '../api/audit'

const services = AUDIT_SERVICES
const service = ref('all')
const action = ref('')
const resultFilter = ref('')
const dateRange = ref([])
const page = ref(1)
const size = 20
const loading = ref(false)
const items = ref([])
const total = ref(0)
const aggregated = ref(false)

const SERVICE_LABEL = {
  'mall-service-user': '用户服务',
  'mall-service-product': '商品服务',
  'mall-service-order': '订单服务',
  'mall-service-inventory': '库存服务',
  'mall-service-payment': '支付服务',
}

const RESULT_OPTIONS = [
  { label: '成功', value: 1 },
  { label: '失败', value: 0 },
]

function buildParams(pageNo, pageSize) {
  const params = { page: pageNo, size: pageSize }
  if (action.value.trim()) params.action = action.value.trim()
  if (resultFilter.value !== '' && resultFilter.value !== null) params.result = resultFilter.value
  if (Array.isArray(dateRange.value) && dateRange.value.length === 2) {
    params.from = dateRange.value[0]
    params.to = dateRange.value[1]
  }
  return params
}

const sortKey = (row) =>
  `${row.createdTime || ''}#${String(row.id ?? '').padStart(20, '0')}`

async function load() {
  loading.value = true
  try {
    if (service.value === 'all') {
      aggregated.value = true
      // 全量聚合不追求精确分页：各服务取最近 100 条，合并后前端分页
      const results = await Promise.allSettled(
        services.map((s) => listAuditLogs(s.key, buildParams(1, 100))),
      )
      const merged = []
      let failed = 0
      for (const r of results) {
        if (r.status === 'fulfilled') {
          merged.push(...(r.value?.items || []))
        } else {
          failed += 1
        }
      }
      if (failed > 0) {
        ElMessage.warning(`有 ${failed} 个服务的审计日志暂不可用`)
      }
      merged.sort((a, b) => sortKey(b).localeCompare(sortKey(a)))
      total.value = merged.length
      const start = (page.value - 1) * size
      items.value = merged.slice(start, start + size)
    } else {
      aggregated.value = false
      const data = await listAuditLogs(service.value, buildParams(page.value, size))
      items.value = data?.items || []
      total.value = Number(data?.total) || 0
    }
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  service.value = 'all'
  action.value = ''
  resultFilter.value = ''
  dateRange.value = []
  page.value = 1
  load()
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}

function nextPage() {
  if (page.value * size >= total.value) return
  page.value += 1
  load()
}

const serviceLabel = (name) => SERVICE_LABEL[name] || name || '-'

const time = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

const targetLabel = (row) => {
  if (!row.targetId) return row.targetType || '-'
  return `${row.targetType || '对象'} #${row.targetId}`
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div>
        <h3 class="title">审计日志</h3>
        <p class="subtitle">管理员写操作的留痕：谁、在什么时候、对什么对象做了什么</p>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filters">
        <el-select v-model="service" class="w150" @change="search">
          <el-option label="全部服务" value="all" />
          <el-option v-for="s in services" :key="s.key" :label="s.label" :value="s.key" />
        </el-select>
        <el-input
          v-model="action"
          class="w200"
          placeholder="操作名，如：审核退款"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="resultFilter" class="w120" placeholder="全部结果" clearable @change="search">
          <el-option v-for="o in RESULT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="w260"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
      <p class="tip">
        只记录写操作（POST/PUT/PATCH/DELETE），GET 查询不记录；请求体不落库，查询参数中的密码/令牌已脱敏。
        <span v-if="aggregated">「全部服务」为各服务最近 100 条的聚合视图，选择具体服务可分页查看全部。</span>
      </p>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="items" style="width: 100%">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <div><span class="k">请求</span>{{ row.method }} {{ row.path }}</div>
              <div><span class="k">参数</span>{{ row.params || '（无查询参数）' }}</div>
              <div v-if="row.errorMsg"><span class="k">失败原因</span>{{ row.errorMsg }}</div>
              <div><span class="k">User-Agent</span>{{ row.userAgent || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ time(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="服务" width="100">
          <template #default="{ row }">{{ serviceLabel(row.service) }}</template>
        </el-table-column>
        <el-table-column prop="action" label="操作" min-width="140" />
        <el-table-column label="对象" min-width="150">
          <template #default="{ row }">{{ targetLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="操作人" min-width="120">
          <template #default="{ row }">
            {{ row.operatorName || '未知' }}
            <span class="uid">#{{ row.operatorId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.result) === 1 ? 'success' : 'danger'" size="small">
              {{ Number(row.result) === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <template #empty>
          <el-empty description="暂无审计记录" :image-size="70" />
        </template>
      </el-table>

      <div v-if="total > 0" class="pager">
        <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
        <span class="pno">第 {{ page }} 页 / 共 {{ total }} 条</span>
        <el-button :disabled="page * size >= total" @click="nextPage">下一页</el-button>
      </div>
    </el-card>
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
  margin: 4px 0 12px;
}
.title {
  margin: 0;
}
.subtitle {
  margin: 4px 0 0;
  color: #909399;
  font-size: 13px;
}
.filter-card {
  margin-bottom: 14px;
  border-radius: 6px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.w120 {
  width: 120px;
}
.w150 {
  width: 150px;
}
.w200 {
  width: 200px;
}
.w260 {
  width: 260px;
}
.tip {
  margin: 10px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.detail {
  padding: 4px 12px 4px 48px;
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
  word-break: break-all;
}
.detail .k {
  display: inline-block;
  width: 88px;
  color: #909399;
}
.uid {
  color: #c0c4cc;
  font-size: 12px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 14px 0 4px;
}
.pno {
  color: #606266;
  font-size: 14px;
}
</style>
