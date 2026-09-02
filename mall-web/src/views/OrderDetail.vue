<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder } from '../api/order'
import { money, orderStatusTag } from '../utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const order = ref(null)

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
  } finally {
    loading.value = false
  }
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
        </div>
        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item v-for="r in descriptions()" :key="r.label" :label="r.label">
            {{ r.value }}
          </el-descriptions-item>
        </el-descriptions>
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
</style>
