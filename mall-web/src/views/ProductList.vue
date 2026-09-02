<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listProducts, listCategories } from '../api/product'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()

const categories = ref([])
const keyword = ref('')
const storeUser = ref('')
const categoryId = ref(null)
const sort = ref('newest')
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)

function goStore() {
  const u = storeUser.value.trim()
  if (!u) return
  router.push(`/store/${encodeURIComponent(u)}`)
}

const sortOptions = [
  { label: '综合', value: 'newest' },
  { label: '价格升序', value: 'price_asc' },
  { label: '价格降序', value: 'price_desc' },
]

async function loadCategories() {
  try {
    categories.value = (await listCategories()) || []
  } catch {
    categories.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, sort: sort.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (categoryId.value) params.categoryId = categoryId.value
    const data = (await listProducts(params)) || { total: 0, items: [] }
    list.value = data.items || []
    total.value = Number(data.total) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleCategoryChange() {
  page.value = 1
  load()
}

function handleSortChange() {
  page.value = 1
  load()
}

function handleSizeChange(newSize) {
  size.value = newSize
  page.value = 1
  load()
}

function handleCurrentChange(newPage) {
  page.value = newPage
  load()
}

onMounted(async () => {
  await loadCategories()
  load()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        class="kw"
        placeholder="搜索商品名称"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="categoryId"
        class="sel"
        placeholder="全部分类"
        clearable
        @change="handleCategoryChange"
      >
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="sort" class="sel" @change="handleSortChange">
        <el-option v-for="o in sortOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-divider direction="vertical" />
      <el-input
        v-model="storeUser"
        class="kw"
        placeholder="输入卖家用户名进店逛逛"
        clearable
        @keyup.enter="goStore"
      />
      <el-button @click="goStore">进店</el-button>
    </div>

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
      <el-empty v-else-if="!loading" description="暂无商品" />
    </div>

    <div v-if="total > 0" class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="page"
        :page-size="size"
        :page-sizes="[12, 24, 48]"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px;
  background: #fff;
  border-radius: 6px;
}
.kw {
  width: 240px;
}
.sel {
  width: 160px;
}
.grid-wrap {
  min-height: 200px;
}
.pager {
  margin: 16px 0 24px;
  display: flex;
  justify-content: center;
}
</style>
