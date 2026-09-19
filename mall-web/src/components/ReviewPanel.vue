<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createReview, listReviews, myReviewableOrders, reviewStat } from '../api/review'
import { username } from '../stores/auth'

const props = defineProps({
  productId: { type: [Number, String], required: true },
})

const loading = ref(false)
const stat = ref(null)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(5)

// 待评价的订单（我的已完成订单中尚未评过这个商品的）
const reviewable = ref([])
const dialog = ref(false)
const submitting = ref(false)
const form = ref({ orderId: null, rating: 5, content: '' })

// 无评价时 avgRating 为 null —— 此时整块汇总不渲染。
// 显示「0.0 分」会把「没人评过」误报成「差评」。
const hasReviews = computed(() => Number(total.value) > 0)
const avgText = computed(() => (stat.value?.avgRating == null ? '—' : Number(stat.value.avgRating).toFixed(1)))

/** 星级分布：固定 1-5 档（后端已预填 0），按 5→1 倒序展示更符合直觉 */
const buckets = computed(() => {
  const dist = stat.value?.distribution || {}
  return [5, 4, 3, 2, 1].map((star) => {
    const count = Number(dist[star] || 0)
    const pct = total.value > 0 ? Math.round((count / total.value) * 100) : 0
    return { star, count, pct }
  })
})

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  loading.value = true
  try {
    const [s, res] = await Promise.all([
      reviewStat(props.productId),
      listReviews(props.productId, page.value, size.value),
    ])
    stat.value = s || null
    list.value = res?.items || []
    total.value = Number(res?.total || 0)
  } catch {
    // 错误提示由 api 拦截器统一处理；失败时按「无评价」展示而不是让整页崩掉
    stat.value = null
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

/**
 * 打开写评价弹框。先拉「可评价的订单」列表——可能有**多张**（分两次买过同一商品），
 * 必须让用户自己选是哪一次，静默取第一条会让第二次评价实际上做不到。
 */
async function openDialog() {
  try {
    reviewable.value = (await myReviewableOrders(props.productId)) || []
  } catch {
    reviewable.value = []
  }
  if (!reviewable.value.length) {
    ElMessage.info('没有可评价的订单：只有已完成的订单才能评价，且每张订单每个商品只能评一次')
    return
  }
  form.value = {
    orderId: reviewable.value[0].orderId,
    rating: 5,
    content: '',
  }
  dialog.value = true
}

async function submit() {
  if (!form.value.orderId) {
    ElMessage.warning('请选择要评价的订单')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请填写评价内容')
    return
  }
  submitting.value = true
  try {
    await createReview({
      orderId: form.value.orderId,
      productId: Number(props.productId),
      rating: form.value.rating,
      content: form.value.content.trim(),
    })
    ElMessage.success('评价成功')
    dialog.value = false
    page.value = 1
    await load()
  } catch {
    // 错误提示由拦截器统一处理（含「你已经评价过这个商品了」）
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card class="review-card" shadow="never">
    <template #header>
      <div class="rv-head">
        <span class="dc-title">商品评价</span>
        <el-button v-if="username" size="small" type="primary" @click="openDialog">写评价</el-button>
      </div>
    </template>

    <div v-loading="loading">
      <!-- 汇总：只在有评价时渲染 -->
      <div v-if="hasReviews" class="summary">
        <div class="score">
          <div class="avg">{{ avgText }}</div>
          <el-rate :model-value="Number(stat?.avgRating || 0)" disabled allow-half />
          <div class="cnt">{{ total }} 条评价</div>
        </div>
        <div class="bars">
          <div v-for="b in buckets" :key="b.star" class="bar-row">
            <span class="star-label">{{ b.star }} 星</span>
            <div class="bar"><div class="fill" :style="{ width: b.pct + '%' }" /></div>
            <span class="bar-cnt">{{ b.count }}</span>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !list.length" description="还没有评价" />

      <div v-for="r in list" :key="r.id" class="item">
        <div class="item-head">
          <span class="buyer">{{ r.buyerName || '匿名用户' }}</span>
          <el-rate :model-value="Number(r.rating)" disabled size="small" />
          <span class="time">{{ fmtTime(r.createdTime) }}</span>
        </div>
        <div class="content">{{ r.content }}</div>
        <div v-if="r.replyContent" class="reply">
          <span class="reply-label">商家回复</span>
          <span class="reply-text">{{ r.replyContent }}</span>
          <span v-if="r.replyTime" class="reply-time">{{ fmtTime(r.replyTime) }}</span>
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
    </div>

    <el-dialog v-model="dialog" title="写评价" width="520px">
      <el-form label-width="72px">
        <el-form-item label="评价订单">
          <!-- 订单选择器：分两次买过同一商品时，这里会有两条可选 -->
          <el-select v-model="form.orderId" placeholder="选择要评价的订单" style="width: 100%">
            <el-option
              v-for="o in reviewable"
              :key="o.orderId"
              :label="`${o.orderNo}（完成于 ${fmtTime(o.completeTime)}）`"
              :value="o.orderId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="form.rating" />
        </el-form-item>
        <el-form-item label="评价">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            :maxlength="500"
            show-word-limit
            placeholder="说说这件商品怎么样，可以帮到其他买家"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.review-card {
  margin-top: 16px;
}
.rv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dc-title {
  font-weight: 600;
  color: #303133;
}
.summary {
  display: flex;
  gap: 40px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}
.score {
  text-align: center;
  min-width: 120px;
}
.avg {
  font-size: 32px;
  font-weight: 600;
  color: #f56c6c;
  line-height: 1.2;
}
.cnt {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.bars {
  flex: 1;
  max-width: 420px;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.star-label {
  width: 40px;
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}
.bar {
  flex: 1;
  height: 8px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
}
.fill {
  height: 100%;
  background: #f7ba2a;
  border-radius: 4px;
  transition: width 0.2s;
}
.bar-cnt {
  width: 32px;
  text-align: right;
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}
.item {
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}
.item:last-child {
  border-bottom: none;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.buyer {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.time {
  margin-left: auto;
  color: #a8abb2;
  font-size: 12px;
}
.content {
  margin-top: 6px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.reply {
  margin-top: 8px;
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
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
