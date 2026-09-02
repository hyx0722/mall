<script setup>
import { onMounted, ref } from 'vue'
import { listAllOrders, getAdminOrderItems } from '../api/admin'
import { money, orderStatusTag } from '../utils/format'
import RefCell from '../components/RefCell.vue'

const loading = ref(true)
const list = ref([])
const status = ref(null)

const detailVisible = ref(false)
const detail = ref(null)
const items = ref([])

async function load() {
  loading.value = true
  try {
    list.value = (await listAllOrders(status.value)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  load()
}

async function openDetail(row) {
  detail.value = row
  items.value = []
  detailVisible.value = true
  try {
    items.value = (await getAdminOrderItems(row.id)) || []
  } catch {
    items.value = []
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">订单管理</h3>
      <el-select v-model="status" placeholder="全部状态" clearable class="sel" @change="onStatusChange">
        <el-option label="待付款" :value="0" />
        <el-option label="待发货" :value="1" />
        <el-option label="已发货" :value="2" />
        <el-option label="已完成" :value="3" />
        <el-option label="已取消" :value="4" />
      </el-select>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="id" label="订单ID" width="90" />
        <el-table-column prop="orderNo" label="订单号" min-width="220" />
        <el-table-column label="买家" min-width="150">
          <template #default="{ row }">
            <RefCell :main="row.buyerName" :id="row.userId" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">¥{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.orderStatus).type" size="small">
              {{ orderStatusTag(row.orderStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="下单时间" width="180">
          <template #default="{ row }">{{ (row.createdTime || '').replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无订单" />
    </el-card>

    <el-dialog v-model="detailVisible" title="订单详情" width="720px" top="6vh">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="desc">
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="买家">
            <RefCell :main="detail.buyerName" :id="detail.userId" />
          </el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ money(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="orderStatusTag(detail.orderStatus).type" size="small">
              {{ orderStatusTag(detail.orderStatus).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="items" style="width: 100%; margin-top: 12px">
          <el-table-column prop="productName" label="商品" min-width="200" />
          <el-table-column label="商品ID" min-width="180">
            <template #default="{ row }">
              <RefCell :main="row.productName" :id="row.productId" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">¥{{ money(row.productPrice) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="小计" width="100">
            <template #default="{ row }">¥{{ money(row.totalPrice) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
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
.sel {
  width: 160px;
}
.desc {
  margin-bottom: 4px;
}
</style>
