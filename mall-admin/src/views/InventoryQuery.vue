<script setup>
import { onMounted, ref } from 'vue'
import { listAllInventory } from '../api/admin'
import RefCell from '../components/RefCell.vue'

const loading = ref(true)
const list = ref([])
const productId = ref(null)

async function load() {
  loading.value = true
  try {
    list.value = (await listAllInventory(productId.value || undefined)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onQuery() {
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <h3 class="title">库存查询</h3>
      <div class="tools">
        <el-input v-model="productId" class="kw" placeholder="商品ID（留空查全部）" clearable @keyup.enter="onQuery" @clear="onQuery" />
        <el-button type="primary" @click="onQuery">查询</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="id" label="记录ID" width="90" />
        <el-table-column label="商品" min-width="160">
          <template #default="{ row }">
            <RefCell :main="row.productName" :id="row.productId" />
          </template>
        </el-table-column>
        <el-table-column label="卖家" min-width="140">
          <template #default="{ row }">
            <RefCell :main="row.sellerName" :id="row.userId" />
          </template>
        </el-table-column>
        <el-table-column prop="totalStock" label="总库存" width="100" />
        <el-table-column prop="lockedStock" label="锁定库存" width="100" />
        <el-table-column prop="availableStock" label="可用库存" width="100" />
        <el-table-column prop="salesCount" label="销量" width="90" />
        <el-table-column prop="updatedTime" label="更新时间" min-width="180">
          <template #default="{ row }">{{ (row.updatedTime || '').replace('T', ' ') }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无库存记录" />
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
.tools {
  display: flex;
  gap: 8px;
}
.kw {
  width: 200px;
}
</style>
