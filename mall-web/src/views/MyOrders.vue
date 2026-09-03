<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOrders, cancelOrder, confirmReceive } from '../api/order'
import { money, orderStatusTag } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    orders.value = (await listOrders()) || []
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/order/${id}`)
}

function goPay(id) {
  router.push({ path: '/pay', query: { orderId: id } })
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消订单「${row.orderNo}」？取消后已锁定的库存会被释放。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  try {
    await cancelOrder(row.id)
    ElMessage.success('订单已取消')
    load()
  } catch {
    // 拦截器已提示
  }
}

async function receive(row) {
  try {
    await ElMessageBox.confirm(
      `确认已收到订单「${row.orderNo}」的商品？`,
      '确认收货',
      { type: 'warning', confirmButtonText: '确认收货', cancelButtonText: '再等等' },
    )
  } catch {
    return
  }
  try {
    await confirmReceive(row.id)
    ElMessage.success('已确认收货')
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
      <h3 class="title">我的订单</h3>
    </div>
    <el-card v-loading="loading" shadow="never">
      <el-table :data="orders" style="width: 100%">
        <el-table-column prop="orderNo" label="订单号" min-width="200" show-overflow-tooltip />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">¥{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.orderStatus).type">
              {{ orderStatusTag(row.orderStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收货人" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.receiverName }} {{ row.receiverPhone }}
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
            <template v-if="Number(row.orderStatus) === 0">
              <el-button link type="danger" @click="goPay(row.id)">去支付</el-button>
              <el-button link @click="cancel(row)">取消订单</el-button>
            </template>
            <el-button v-if="Number(row.orderStatus) === 2" link type="success" @click="receive(row)">
              确认收货
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !orders.length" description="暂无订单" />
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}
.head {
  margin: 4px 0 12px;
}
.title {
  margin: 0;
}
</style>
