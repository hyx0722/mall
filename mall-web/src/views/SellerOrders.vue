<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSellerOrders, sellerCancelOrder } from '../api/order'
import { money, orderStatusTag } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

// 明细摘要：如「java辅导书 ×2」换行拼接（一个订单可能是多件/多行商品）
const itemsText = (items) =>
  (items || []).map((it) => `${it.productName} ×${it.quantity}`).join('\n') || '（无明细）'

async function load() {
  loading.value = true
  try {
    orders.value = (await listSellerOrders()) || []
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function cancel(row) {
  const hint = row.cancellable
    ? `订单包含：\n${itemsText(row.items)}\n\n确定取消这笔待付款订单？取消后库存会释放。`
    : '只有待付款且全部为本店商品的订单才能取消。'
  try {
    await ElMessageBox.confirm(hint, '取消订单', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      confirmButtonDisabled: !row.cancellable,
    })
  } catch {
    return
  }
  try {
    await sellerCancelOrder(row.order.id)
    ElMessage.success('订单已取消')
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
      <h3 class="title">卖家中心 · 商品订单</h3>
      <el-button @click="router.push('/seller')">← 返回我的商品</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="orders" style="width: 100%">
        <el-table-column prop="order.orderNo" label="订单号" min-width="190" show-overflow-tooltip />
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div class="items" :title="itemsText(row.items)">{{ itemsText(row.items) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="买家" min-width="150">
          <template #default="{ row }">
            {{ row.order.buyerName || ('用户#' + row.order.userId) }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ money(row.order.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.order.orderStatus).type" size="small">
              {{ orderStatusTag(row.order.orderStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.order.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              :disabled="!row.cancellable"
              @click="cancel(row)"
            >
              取消订单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !orders.length" description="还没有买家下过你商品的订单" />
    </el-card>
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
}
.title {
  margin: 0;
}
.items {
  white-space: pre-line;
  color: #303133;
  line-height: 1.6;
}
</style>
