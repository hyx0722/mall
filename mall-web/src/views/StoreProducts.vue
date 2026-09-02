<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOtherUser } from '../api/user'
import { findProductsByUsername } from '../api/product'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()
const username = route.params.username

const loading = ref(true)
const seller = ref(null)
const list = ref([])
const page = ref(1)
const size = ref(12)

async function loadSeller() {
  if (!username) return
  try {
    seller.value = (await getOtherUser(username)) || null
  } catch {
    seller.value = null
  }
}

async function load() {
  if (!username) return
  loading.value = true
  try {
    list.value = (await findProductsByUsername(page.value, size.value, username)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}

function nextPage() {
  page.value += 1
  load()
}

function isNormal() {
  return Number(seller.value?.status) === 1
}

onMounted(async () => {
  await loadSeller()
  load()
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <el-button link type="primary" @click="router.back()">← 返回</el-button>
    </div>

    <el-card class="shop-card" shadow="never">
      <div class="shop-info">
        <el-avatar :size="56" :src="seller?.avatar || ''" class="shop-avatar">
          {{ (username || '?').charAt(0).toUpperCase() }}
        </el-avatar>
        <div class="shop-meta">
          <div class="shop-name">
            {{ username }}
            <el-tag v-if="isNormal()" type="success" size="small" class="st">正常</el-tag>
            <el-tag v-else-if="seller" type="danger" size="small" class="st">停用</el-tag>
          </div>
          <div class="shop-sub">卖家店铺 · 共 {{ list.length }} 件在售（本页）</div>
        </div>
      </div>
    </el-card>

    <div v-loading="loading" class="grid-wrap">
      <el-row v-if="list.length" :gutter="16">
        <el-col
          v-for="p in list"
          :key="p.id"
          :xs="12"
          :sm="12"
          :md="8"
          :lg="6"
          :xl="4"
        >
          <ProductCard :product="p" />
        </el-col>
      </el-row>
      <el-empty v-else-if="!loading" description="该店铺暂无在售商品" />
    </div>

    <div v-if="list.length" class="pager">
      <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
      <span class="pno">第 {{ page }} 页</span>
      <el-button @click="nextPage">下一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.shop-card {
  margin-bottom: 16px;
}
.shop-info {
  display: flex;
  align-items: center;
  gap: 16px;
}
.shop-avatar {
  background: #67c23a;
  color: #fff;
  font-size: 22px;
  flex-shrink: 0;
}
.shop-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.st {
  margin-left: 8px;
  vertical-align: 2px;
}
.shop-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.grid-wrap {
  min-height: 200px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 12px 0 24px;
}
.pno {
  color: #606266;
  font-size: 14px;
}
</style>
