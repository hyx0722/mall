<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder, confirmReceive, listShippings } from '../api/order'
import { money, orderStatusTag } from '../utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const order = ref(null)
const shippings = ref([])

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

const descriptions = () => {
  if (!order.value) return []
  const o = order.value
  const rows = [
    { label: '订单编号', value: o.orderNo },
    { label: '订单状态', value: orderStatusTag(o.orderStatus).text },
    { label: '订单金额', value: `¥${money(o.totalAmount)}` },
    { label: '收货人', value: `${o.receiverName || '-'} ${o.receiverPhone || ''}` },
    { label: '收货地址', value: o.receiverAddress || '-' },
    { label: '备注', value: o.remark || '-' },
    { label: '下单时间', value: fmtTime(o.createdTime) },
    { label: '发货时间', value: fmtTime(o.shippingTime) },
    { label: '完成时间', value: fmtTime(o.completeTime) },
    { label: '取消时间', value: fmtTime(o.cancelTime) },
  ]
  return rows
}

async function load() {
  loading.value = true
  try {
    order.value = (await getOrderDetail(route.params.id)) || null
  } catch {
    order.value = null
  }
  // 物流列表尽力而为：失败不影响订单详情展示
  if (order.value) {
    try {
      shippings.value = (await listShippings(order.value.id)) || []
    } catch {
      shippings.value = []
    }
  } else {
    shippings.value = []
  }
  loading.value = false
}

async function cancel() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm(
      `确定取消订单「${order.value.orderNo}」？取消后已锁定的库存会被释放。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  try {
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    load()
  } catch {
    // 拦截器已提示
  }
}

async function receive() {
  if (!order.value) return
  try {
    await ElMessageBox.confirm(
      `确认已收到订单「${order.value.orderNo}」的商品？`,
      '确认收货',
      { type: 'warning', confirmButtonText: '确认收货', cancelButtonText: '再等等' },
    )
  } catch {
    return
  }
  try {
    await confirmReceive(order.value.id)
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
    <div class="crumb">
      <el-button link type="primary" @click="router.push('/orders')">← 我的订单</el-button>
    </div>
    <el-card v-loading="loading" shadow="never">
      <el-empty v-if="!loading && !order" description="订单不存在" />
      <template v-else-if="order">
        <div class="hdr">
          <el-tag :type="orderStatusTag(order.orderStatus).type" size="large">
            {{ orderStatusTag(order.orderStatus).text }}
          </el-tag>
          <div v-if="Number(order.orderStatus) === 0" class="actions">
            <el-button type="danger" class="pay-btn" @click="router.push({ path: '/pay', query: { orderId: order.id } })">
              去支付 ¥{{ money(order.totalAmount) }}
            </el-button>
            <el-button @click="cancel">取消订单</el-button>
          </div>
          <div v-if="Number(order.orderStatus) === 2" class="actions">
            <el-button type="success" @click="receive">确认收货</el-button>
          </div>
        </div>
        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item v-for="r in descriptions()" :key="r.label" :label="r.label">
            {{ r.value }}
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="shippings.length">
          <h4 class="sub-title">物流信息</h4>
          <el-table :data="shippings" style="width: 100%">
            <el-table-column prop="shipNo" label="发货单号" min-width="200" show-overflow-tooltip />
            <el-table-column prop="logisticsCompany" label="物流公司" min-width="120" />
            <el-table-column prop="trackingNo" label="物流单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="发货时间" width="180">
              <template #default="{ row }">{{ fmtTime(row.createdTime) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.pay-btn {
  font-weight: 600;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sub-title {
  margin: 20px 0 10px;
}

</style>
