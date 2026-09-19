<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOrderDetail,
  cancelOrder,
  confirmReceive,
  listShippings,
  applyRefund,
  getRefundDetail,
} from '../api/order'
import { listOrderItems } from '../api/review'
import { money, orderStatusTag, refundStatusTag } from '../utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const order = ref(null)
const shippings = ref([])
const refund = ref(null)
// 订单明细（含每行能否评价）。此前该页完全不展示买了什么，一并补上
const items = ref([])

// 可申请退款：待发货 / 待收货 / 已完成（与后端 OrderStatus.refundable 保持一致）
const canRefund = computed(() => order.value && [1, 2, 3].includes(Number(order.value.orderStatus)))

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

/**
 * 跳到商品详情页。评价的写入口在详情页的评价区（那里能看到已有的评价和星级），
 * 订单页只负责把用户送过去，不重复实现一套弹框。
 */
function goReview(productId) {
  router.push('/product/' + productId)
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
  // 物流列表与退款进度都尽力而为：失败不影响订单详情展示
  if (order.value) {
    try {
      shippings.value = (await listShippings(order.value.id)) || []
    } catch {
      shippings.value = []
    }
    try {
      refund.value = (await getRefundDetail(order.value.id)) || null
    } catch {
      refund.value = null
    }
    try {
      items.value = (await listOrderItems(order.value.id)) || []
    } catch {
      items.value = []
    }
  } else {
    shippings.value = []
    refund.value = null
    items.value = []
  }
  loading.value = false
}

async function doRefund() {
  if (!order.value) return
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `整单全额退款 ¥${money(order.value.totalAmount)}，原因选填：`,
      '申请退款',
      {
        confirmButtonText: '提交申请',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：不想要了 / 商品与描述不符',
        inputValidator: (v) => (v && v.length > 255 ? '退款原因过长（最多 255 字）' : true),
      },
    )
    reason = value || ''
  } catch {
    return // 用户取消
  }
  try {
    await applyRefund(order.value.id, reason || undefined)
    ElMessage.success('退款申请已提交，等待卖家或管理员审核')
    load()
  } catch {
    // 拦截器已提示
  }
}

// 退款进度展示行（仅在存在退款申请时渲染）
const refundRows = () => {
  if (!refund.value) return []
  const r = refund.value
  return [
    { label: '退款单号', value: r.refundNo },
    { label: '退款状态', value: refundStatusTag(r.refundStatus).text },
    { label: '退款金额', value: `¥${money(r.refundAmount)}` },
    { label: '退款原因', value: r.refundReason || '-' },
    { label: '驳回原因', value: r.rejectReason || '-' },
    { label: '申请时间', value: fmtTime(r.createdTime) },
  ]
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
            <el-button v-if="canRefund" type="warning" plain @click="doRefund">申请退款</el-button>
          </div>
          <div v-if="canRefund && Number(order.orderStatus) !== 2" class="actions">
            <el-button type="warning" plain @click="doRefund">申请退款</el-button>
          </div>
        </div>
        <el-descriptions :column="2" border class="desc">
          <el-descriptions-item v-for="r in descriptions()" :key="r.label" :label="r.label">
            {{ r.value }}
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="items.length">
          <h4 class="sub-title">商品清单</h4>
          <el-table :data="items" style="width: 100%">
            <el-table-column prop="productName" label="商品" min-width="220" show-overflow-tooltip />
            <el-table-column label="单价" width="110">
              <template #default="{ row }">¥{{ money(row.productPrice) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="小计" width="110">
              <template #default="{ row }">¥{{ money(row.totalPrice) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <!-- 已完成且未评价 -> 可评价；已评价 -> 去看那条评价 -->
                <el-button
                  v-if="row.canReview"
                  link
                  type="primary"
                  @click="goReview(row.productId)"
                >
                  评价
                </el-button>
                <el-button
                  v-else-if="row.reviewId"
                  link
                  type="success"
                  @click="goReview(row.productId)"
                >
                  查看评价
                </el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-if="refund">
          <h4 class="sub-title">
            退款进度
            <el-tag :type="refundStatusTag(refund.refundStatus).type" size="small">
              {{ refundStatusTag(refund.refundStatus).text }}
            </el-tag>
          </h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-for="r in refundRows()" :key="r.label" :label="r.label">
              {{ r.value }}
            </el-descriptions-item>
          </el-descriptions>
        </template>

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
/* 不可评价的明细行占位符，保持操作列不出现空白跳动 */
.muted {
  color: #c0c4cc;
}

</style>
