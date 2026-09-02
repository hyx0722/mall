<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '../api/product'
import { money } from '../utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const product = ref(null)
const qty = ref(1)

async function load() {
  loading.value = true
  try {
    product.value = (await getProductDetail(route.params.id)) || null
  } catch {
    product.value = null
  } finally {
    loading.value = false
  }
}

const hasImage = () => !!product.value?.mainImage
const isOnShelf = () => Number(product.value?.status) === 1
const totalPrice = () => money(Number(product.value?.price || 0) * Number(qty.value || 1))

function buy() {
  if (!product.value) return
  if (!isOnShelf()) {
    ElMessage.warning('商品已下架，无法购买')
    return
  }
  router.push({
    path: '/checkout',
    query: { productId: product.value.id, qty: qty.value || 1 },
  })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="crumb">
      <el-button link type="primary" @click="router.push('/')">← 返回首页</el-button>
    </div>
    <div v-loading="loading" class="wrap">
      <el-empty v-if="!loading && !product" description="商品不存在或已被删除" />
      <template v-else-if="product">
        <div class="main-box">
          <div class="img-box">
            <el-image v-if="hasImage()" :src="product.mainImage" fit="contain" class="img">
              <template #error><div class="img-fallback">图片加载失败</div></template>
            </el-image>
            <div v-else class="img-fallback">暂无图片</div>
          </div>
          <div class="info">
            <div class="name">{{ product.name }}</div>
            <div v-if="product.subtitle" class="subtitle">{{ product.subtitle }}</div>
            <div class="prices">
              <span class="price">¥{{ money(product.price) }}</span>
              <span v-if="Number(product.originalPrice) > Number(product.price)" class="original">
                ¥{{ money(product.originalPrice) }}
              </span>
              <el-tag v-if="!isOnShelf()" type="info" size="small">已下架</el-tag>
            </div>
            <div class="buy-row">
              <span class="qty-label">数量</span>
              <el-input-number v-model="qty" :min="1" :max="999" />
              <span class="total">合计 ¥{{ totalPrice() }}</span>
            </div>
            <div class="actions">
              <el-button type="danger" size="large" :disabled="!isOnShelf()" @click="buy">
                立即购买
              </el-button>
            </div>
          </div>
        </div>
        <el-card class="detail-card" shadow="never">
          <template #header><span class="dc-title">商品详情</span></template>
          <div class="detail-text">{{ product.detail || '暂无详情描述' }}</div>
        </el-card>
      </template>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.wrap {
  min-height: 200px;
}
.main-box {
  display: flex;
  gap: 28px;
  background: #fff;
  border-radius: 6px;
  padding: 24px;
}
.img-box {
  width: 380px;
  height: 380px;
  flex-shrink: 0;
  background: #f5f7fa;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.img {
  max-width: 100%;
  max-height: 100%;
}
.img-fallback {
  color: #c0c4cc;
  font-size: 13px;
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  line-height: 1.5;
}
.subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 14px;
}
.prices {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fef0f0;
  border-radius: 4px;
  padding: 12px 16px;
}
.price {
  font-size: 30px;
  font-weight: 700;
  color: #f56c6c;
}
.original {
  font-size: 14px;
  color: #c0c4cc;
  text-decoration: line-through;
}
.buy-row {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.qty-label {
  color: #606266;
}
.total {
  margin-left: auto;
  color: #f56c6c;
  font-size: 16px;
  font-weight: 600;
}
.actions {
  margin-top: 24px;
}
.detail-card {
  margin-top: 16px;
}
.dc-title {
  font-weight: 600;
}
.detail-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #606266;
}
</style>
