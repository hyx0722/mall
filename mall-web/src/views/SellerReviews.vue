<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSellerReviews, replyReview } from '../api/review'

const route = useRoute()
const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const onlyUnreplied = ref(false)

// 从站内通知跳过来时带 focus=<reviewId>，把那条高亮出来
const focusId = ref(route.query.focus ? String(route.query.focus) : '')

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  loading.value = true
  try {
    const res = await listSellerReviews(onlyUnreplied.value, page.value, size.value)
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

function onFilter() {
  page.value = 1
  load()
}

async function onReply(row) {
  let text
  try {
    const { value } = await ElMessageBox.prompt(
      `回复「${row.buyerName || '匿名用户'}」对「${row.productName}」的评价：`,
      '回复评价',
      {
        inputType: 'textarea',
        inputPlaceholder: '最多 500 字，回复后不可修改',
        inputValidator: (v) => (v && v.trim() ? true : '回复内容不能为空'),
        confirmButtonText: '提交回复',
        cancelButtonText: '取消',
      },
    )
    text = value
  } catch {
    return
  }
  try {
    await replyReview(row.id, text.trim())
    ElMessage.success('回复成功')
    load()
  } catch {
    // 错误提示由 api 拦截器统一处理（含「你已经回复过了」）
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="left">
        <el-button link type="primary" @click="router.push('/seller')">← 返回我的商品</el-button>
        <h3 class="title">我的评价</h3>
      </div>
      <el-radio-group v-model="onlyUnreplied" @change="onFilter">
        <el-radio-button :value="false">全部</el-radio-button>
        <el-radio-button :value="true">待回复</el-radio-button>
      </el-radio-group>
    </div>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="买家只有在自己订单完成后才能评价你的商品，且每张订单每个商品只能评一条。回复后买家会收到站内通知。"
    />

    <el-card v-loading="loading" shadow="never">
      <el-empty v-if="!loading && !list.length" :description="onlyUnreplied ? '没有待回复的评价' : '还没有收到评价'" />

      <div
        v-for="r in list"
        :key="r.id"
        class="item"
        :class="{ focus: focusId === String(r.id) }"
      >
        <div class="item-head">
          <span class="product">{{ r.productName }}</span>
          <el-rate :model-value="Number(r.rating)" disabled size="small" />
          <span class="buyer">{{ r.buyerName || '匿名用户' }}</span>
          <span class="time">{{ fmtTime(r.createdTime) }}</span>
        </div>
        <div class="content">{{ r.content }}</div>

        <div v-if="r.replyContent" class="reply">
          <span class="reply-label">我的回复</span>
          <span class="reply-text">{{ r.replyContent }}</span>
          <span v-if="r.replyTime" class="reply-time">{{ fmtTime(r.replyTime) }}</span>
        </div>
        <div v-else class="actions">
          <el-button size="small" type="primary" @click="onReply(r)">回复</el-button>
        </div>
      </div>

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
.item {
  padding: 14px 12px;
  border-bottom: 1px solid #ebeef5;
  border-left: 3px solid transparent;
}
/* 从站内通知跳进来时高亮目标那条 */
.item.focus {
  border-left-color: #409eff;
  background: #ecf5ff;
}
.item:last-child {
  border-bottom: none;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.product {
  font-weight: 600;
  color: #303133;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.buyer {
  color: #909399;
  font-size: 13px;
}
.time {
  margin-left: auto;
  color: #a8abb2;
  font-size: 12px;
}
.content {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.reply {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
}
.reply-label {
  color: #409eff;
  margin-right: 8px;
  font-weight: 500;
}
.reply-text {
  color: #606266;
  word-break: break-word;
}
.reply-time {
  margin-left: 8px;
  color: #a8abb2;
  font-size: 12px;
}
.actions {
  margin-top: 10px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
