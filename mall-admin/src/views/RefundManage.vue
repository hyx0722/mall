<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRefunds, auditRefund } from '../api/refund'
import { money, orderStatusTag, refundStatusTag } from '../utils/format'
import RefCell from '../components/RefCell.vue'

const loading = ref(true)
const list = ref([])
const refundStatus = ref(null)

async function load() {
  loading.value = true
  try {
    list.value = (await listRefunds(refundStatus.value)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// 通过：二次确认后提交，通过后由 payment 异步打款，订单最终变为 6已退款
async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `确定通过退款单「${row.refundNo}」？通过后将异步打款，订单变为已退款。`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await auditRefund({ refundNo: row.refundNo, approve: true })
    ElMessage.success('已通过，退款打款处理中')
    load()
  } catch {
    // 拦截器已提示
  }
}

// 驳回：必填驳回原因，订单回退到申请前的状态
async function reject(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(`退款单「${row.refundNo}」，订单号 ${row.orderNo}`, '驳回退款申请', {
      type: 'warning',
      confirmButtonText: '确定驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入驳回原因（必填）',
      inputErrorMessage: '驳回原因不能为空',
      inputValidator: (v) => {
        const s = (v || '').trim()
        if (!s) return '驳回原因不能为空'
        if (s.length > 255) return '驳回原因不能超过 255 字'
        return true
      },
    })
    reason = (value || '').trim()
  } catch {
    return
  }
  try {
    await auditRefund({ refundNo: row.refundNo, approve: false, rejectReason: reason })
    ElMessage.success('已驳回')
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
      <h3 class="title">退款审核</h3>
      <div class="tools">
        <el-select v-model="refundStatus" placeholder="全部状态" clearable class="sel" @change="load">
          <el-option label="待审核" :value="0" />
          <el-option label="退款中" :value="1" />
          <el-option label="已退款" :value="2" />
          <el-option label="已驳回" :value="3" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="refundNo" label="退款单号" min-width="200" />
        <el-table-column prop="orderNo" label="订单号" min-width="200" />
        <el-table-column label="买家" min-width="150">
          <template #default="{ row }">
            <RefCell :main="row.buyerName" :id="row.userId" />
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="110">
          <template #default="{ row }">¥{{ money(row.refundAmount) }}</template>
        </el-table-column>
        <el-table-column label="退款原因" min-width="180">
          <template #default="{ row }">{{ row.refundReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdTime" label="申请时间" width="170">
          <template #default="{ row }">{{ (row.createdTime || '').replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tooltip v-if="Number(row.refundStatus) === 3 && row.rejectReason" :content="row.rejectReason" placement="top">
              <el-tag :type="refundStatusTag(row.refundStatus).type" size="small">
                {{ refundStatusTag(row.refundStatus).text }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="refundStatusTag(row.refundStatus).type" size="small">
              {{ refundStatusTag(row.refundStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前订单状态" width="120">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.orderStatus).type" size="small">
              {{ orderStatusTag(row.orderStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <template v-if="Number(row.refundStatus) === 0">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <span v-else class="done">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无退款申请" />
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
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
.tools {
  display: flex;
  gap: 8px;
}
.sel {
  width: 160px;
}
.done {
  color: #a8abb2;
  font-size: 13px;
}
</style>
