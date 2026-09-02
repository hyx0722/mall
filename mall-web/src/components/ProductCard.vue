<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  product: { type: Object, required: true },
})

const router = useRouter()

const hasImage = computed(() => !!props.product.mainImage)
const price = computed(() => fmt(props.product.price))
const originalPrice = computed(() => fmt(props.product.originalPrice))
const showOriginal = computed(
  () => Number(props.product.originalPrice) > Number(props.product.price),
)

function fmt(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : ''
}

function goDetail() {
  router.push(`/product/${props.product.id}`)
}
</script>

<template>
  <el-card class="card" shadow="hover" :body-style="{ padding: '0' }" @click="goDetail">
    <div class="img-box">
      <el-image v-if="hasImage" :src="product.mainImage" fit="cover" class="img" lazy>
        <template #error>
          <div class="img-fallback">图片加载失败</div>
        </template>
      </el-image>
      <div v-else class="img-fallback">暂无图片</div>
    </div>
    <div class="body">
      <div class="name" :title="product.name">{{ product.name }}</div>
      <div v-if="product.subtitle" class="subtitle" :title="product.subtitle">
        {{ product.subtitle }}
      </div>
      <div class="prices">
        <span class="price">¥{{ price || '--' }}</span>
        <span v-if="showOriginal" class="original">¥{{ originalPrice }}</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.card {
  margin-bottom: 16px;
  cursor: pointer;
}
.img-box {
  height: 180px;
  background: #f5f7fa;
  overflow: hidden;
}
.img {
  width: 100%;
  height: 100%;
  display: block;
}
.img-fallback {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
}
.body {
  padding: 10px 12px 14px;
}
.name {
  font-size: 14px;
  color: #303133;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.prices {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.price {
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}
.original {
  font-size: 12px;
  color: #c0c4cc;
  text-decoration: line-through;
}
</style>
