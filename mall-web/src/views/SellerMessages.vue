<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createStoreMessage, deleteStoreMessage, sellerMessages } from '../api/store'

const router = useRouter()

const MAX_LEN = 500

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const publishing = ref(false)

const dialog = ref(false)
const content = ref('')

function fmtTime(v) {
  if (!v) return ''
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

async function load() {
  loading.value = true
  try {
    const res = await sellerMessages(page.value, size.value)
    list.value = res?.items || []
    total.value = Number(res?.total || 0)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  page.value = p
  load()
}

function openDialog() {
  content.value = ''
  dialog.value = true
}

async function publish() {
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('公告内容不能为空')
    return
  }
  publishing.value = true
  try {
    await createStoreMessage(text)
    ElMessage.success('公告已发布，订阅者已收到通知')
    dialog.value = false
    page.value = 1
    load()
  } catch {
    // 错误提示由 api 拦截器统一处理
  } finally {
    publishing.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      '删除这条公告？已经推送给订阅者的消息不会撤回。',
      '删除公告',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  try {
    await deleteStoreMessage(row.id)
    ElMessage.success('公告已删除')
    if (list.value.length === 1 && page.value > 1) page.value -= 1
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="left">
        <el-button link type="primary" @click="router.push('/seller')">← 返回我的商品</el-button>
        <h3 class="title">店铺公告</h3>
      </div>
      <el-button type="primary" @click="openDialog">发布公告</el-button>
    </div>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="发布后，所有订阅了你店铺的买家会立刻在「我的消息」里收到这条公告；公告本身也会显示在你的店铺页。"
    />

    <el-card v-loading="loading" shadow="never">
      <el-table :data="list" style="width: 100%">
        <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="还没有发布过公告" />

      <div v-if="total > size" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="onPage"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialog" title="发布店铺公告" width="520px">
      <el-input
        v-model="content"
        type="textarea"
        :rows="5"
        :maxlength="MAX_LEN"
        show-word-limit
        placeholder="例如：本周全场满 199 包邮，新品已到货。"
      />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
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
.left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  margin: 0;
  color: #303133;
}
.tip {
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
