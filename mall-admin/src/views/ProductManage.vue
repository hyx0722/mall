<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAllProducts, shelfProduct } from '../api/admin'
import { money } from '../utils/format'
import RefCell from '../components/RefCell.vue'

const loading = ref(true)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const data = (await listAllProducts({ page: page.value, size: size.value, keyword: keyword.value })) || { total: 0, items: [] }
    list.value = data.items || []
    total.value = Number(data.total) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggleShelf(row) {
  const on = Number(row.status) === 1
  const next = on ? 0 : 1
  try {
    await ElMessageBox.confirm(
      on ? `下架「${row.name}」？下架后买家将不可见。` : `上架「${row.name}」？`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await shelfProduct(row.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    load()
  } catch {
    // 拦截器已提示
  }
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}
function nextPage() {
  if (page.value * size.value >= total.value) return
  page.value += 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">商品管理</h3>
      <div class="tools">
        <el-input v-model="keyword" class="kw" placeholder="商品名称" clearable @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="prod">
              <el-image v-if="row.mainImage" :src="row.mainImage" fit="cover" class="thumb">
                <template #error><span class="tf">图</span></template>
              </el-image>
              <div v-else class="thumb tf">图</div>
              <span class="pname" :title="row.name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品ID" min-width="180">
          <template #default="{ row }">
            <RefCell :main="row.name" :id="row.id" />
          </template>
        </el-table-column>
        <el-table-column label="卖家" min-width="160">
          <template #default="{ row }">
            <RefCell :main="row.sellerName" :id="row.userId" />
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">¥{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="Number(row.status) === 1" type="success" size="small">在售</el-tag>
            <el-tag v-else type="info" size="small">下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link :type="Number(row.status) === 1 ? 'danger' : 'success'" @click="toggleShelf(row)">
              {{ Number(row.status) === 1 ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无商品" />
    </el-card>

    <div v-if="total > 0" class="pager">
      <el-button :disabled="page <= 1" @click="prevPage">上一页</el-button>
      <span class="pno">第 {{ page }} 页 / 共 {{ total }} 条</span>
      <el-button :disabled="page * size >= total" @click="nextPage">下一页</el-button>
    </div>
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
.tools {
  display: flex;
  gap: 8px;
}
.kw {
  width: 220px;
}
.prod {
  display: flex;
  align-items: center;
  gap: 10px;
}
.thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  background: #f5f7fa;
  flex-shrink: 0;
}
.tf {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
}
.pname {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
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
