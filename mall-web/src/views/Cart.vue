<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCart, updateCartItem, removeCartItem, clearCart } from '../api/cart'
import { money } from '../utils/format'

const router = useRouter()

const loading = ref(true)
const items = ref([])
const selected = ref([])
const tableRef = ref(null)

// 只允许勾选仍可购买（存在且在售）的条目
const selectable = (row) => !!row.available

const selectedAvailable = computed(() => selected.value.filter((r) => r.available))

const selectedTotal = computed(() =>
  selectedAvailable.value.reduce((sum, r) => sum + Number(r.price || 0) * Number(r.quantity || 0), 0),
)

const selectedCount = computed(() =>
  selectedAvailable.value.reduce((sum, r) => sum + Number(r.quantity || 0), 0),
)

async function load() {
  loading.value = true
  try {
    items.value = (await listCart()) || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function changeQty(row, quantity) {
  if (quantity == null) return
  try {
    await updateCartItem(row.productId, quantity)
    await load()
  } catch {
    // 拦截器已提示；回滚显示由重新加载兜底
    load()
  }
}

async function removeOne(row) {
  try {
    await removeCartItem(row.productId)
    ElMessage.success('已移出购物车')
    load()
  } catch {
    // 拦截器已提示
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空购物车？', '清空购物车', {
      type: 'warning',
      confirmButtonText: '确定清空',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }
  try {
    await clearCart()
    ElMessage.success('购物车已清空')
    load()
  } catch {
    // 拦截器已提示
  }
}

// 结算：把选中项编码进 query 交给结算页（productId:qty 逗号分隔），
// 刷新页面也不会丢选择，结算页无需再回查购物车。
function checkout() {
  if (!selectedAvailable.value.length) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  const encoded = selectedAvailable.value
    .map((r) => `${r.productId}:${r.quantity}`)
    .join(',')
  router.push({ path: '/checkout', query: { items: encoded } })
}

function goProduct(row) {
  router.push(`/product/${row.productId}`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">购物车</h3>
      <el-button v-if="items.length" link type="danger" @click="clearAll">清空购物车</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-empty v-if="!loading && !items.length" description="购物车还是空的">
        <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
      </el-empty>

      <template v-else>
        <el-table
          ref="tableRef"
          :data="items"
          style="width: 100%"
          @selection-change="selected = $event"
        >
          <el-table-column type="selection" width="46" :selectable="selectable" />
          <el-table-column label="商品" min-width="320">
            <template #default="{ row }">
              <div class="prod">
                <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" class="thumb" />
                <div v-else class="thumb thumb-fallback">无图</div>
                <div class="prod-meta">
                  <div class="prod-name" :class="{ off: !row.available }" @click="goProduct(row)">
                    {{ row.name || '商品已不存在' }}
                  </div>
                  <div v-if="row.subtitle" class="prod-sub">{{ row.subtitle }}</div>
                  <el-tag v-if="!row.available" type="info" size="small">已下架/已删除</el-tag>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <span v-if="row.price != null">¥{{ money(row.price) }}</span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template #default="{ row }">
              <el-input-number
                :model-value="row.quantity"
                :min="1"
                :max="999"
                :disabled="!row.available"
                size="small"
                @change="(v) => changeQty(row, v)"
              />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="120">
            <template #default="{ row }">
              <span v-if="row.subtotal != null" class="subtotal">¥{{ money(row.subtotal) }}</span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeOne(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="footer">
          <div class="summary">
            已选 <b>{{ selectedCount }}</b> 件，合计
            <span class="amount">¥{{ money(selectedTotal) }}</span>
          </div>
          <el-button type="danger" size="large" :disabled="!selectedAvailable.length" @click="checkout">
            去结算
          </el-button>
        </div>
      </template>
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
.prod {
  display: flex;
  gap: 12px;
  align-items: center;
}
.thumb {
  width: 64px;
  height: 64px;
  border-radius: 4px;
  flex-shrink: 0;
  background: #f5f7fa;
}
.thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
}
.prod-meta {
  min-width: 0;
}
.prod-name {
  color: #303133;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.prod-name:hover {
  color: #409eff;
}
.prod-name.off {
  color: #c0c4cc;
  cursor: default;
}
.prod-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.subtotal {
  color: #f56c6c;
  font-weight: 600;
}
.muted {
  color: #c0c4cc;
}
.footer {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
}
.summary {
  color: #606266;
}
.summary b {
  color: #f56c6c;
}
.amount {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}
</style>
