<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMessage, listMessages, markAllRead, markRead } from '../api/message'
import { reviewDetail } from '../api/review'

const router = useRouter()

// tab -> 后端 category。'unread' 不是分类而是「未读筛选」，故单列
const TABS = [
  { name: 'all', label: '全部', category: 'all', isRead: undefined },
  { name: 'unread', label: '未读', category: 'all', isRead: 0 },
  { name: 'order', label: '订单', category: 'order', isRead: undefined },
  { name: 'store', label: '商店', category: 'store', isRead: undefined },
  { name: 'review', label: '评价', category: 'review', isRead: undefined },
]

// 与后端 Notification 的 type 常量一一对应（1-6 订单，7-9 商店，10-11 评价）
// ⚠️ 新增类型忘了加进这里不会报错：meta() 会静默回落到 { text: '消息', tag: 'info' }，
//    只是标签写错，没有任何提示。
const TYPE_META = {
  1: { text: '下单', tag: 'primary' },
  2: { text: '支付', tag: 'success' },
  3: { text: '发货', tag: 'warning' },
  4: { text: '完成', tag: 'success' },
  5: { text: '取消', tag: 'info' },
  6: { text: '退款', tag: 'info' },
  7: { text: '公告', tag: 'primary' },
  8: { text: '上新', tag: 'success' },
  9: { text: '新券', tag: 'danger' },
  10: { text: '新评价', tag: 'warning' },
  11: { text: '评价回复', tag: 'success' },
}

const tab = ref('all')
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const unreadOnly = computed(() => TABS.find((t) => t.name === tab.value)?.isRead === 0)

function meta(type) {
  return TYPE_META[Number(type)] || { text: '消息', tag: 'info' }
}

function fmtTime(v) {
  if (!v) return ''
  // 后端返回的是 LocalDateTime 的 ISO 串（2026-09-19T14:30:00），换成「09-19 14:30」
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(5, 16) : s
}

async function load() {
  loading.value = true
  const conf = TABS.find((t) => t.name === tab.value) || TABS[0]
  try {
    const res = await listMessages({
      category: conf.category,
      isRead: conf.isRead,
      page: page.value,
      size: size.value,
    })
    list.value = res?.items || []
    total.value = Number(res?.total || 0)
  } catch {
    // 错误提示由 api 拦截器统一处理
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onTab(name) {
  tab.value = name
  page.value = 1
  load()
}

function onPage(p) {
  page.value = p
  load()
}

/**
 * 跳转目标。后端存的是 refType + refId，由这里映射成路由——
 * 这样后端不必知道前端的路由长什么样，改路由也不用动库里的数据。
 *
 * ⚠️ 优惠券指向**店铺页**而不是 /coupons：商家券只在自家店铺页可领，
 * 券中心只列平台券（见 docs/api.md）。指到券中心会让用户找不到那张券。
 *
 * ⚠️ 评价类通知的 refId 是 **reviewId**（不是 productId/orderId——那样会在
 * 去重键上撞车导致通知被静默吞掉，见后端 Notification.REF_REVIEW）。
 * 所以「商家回复」这类发给买家的通知要多一次查询才能换出商品页地址。
 */
async function linkOf(n) {
  const refType = n.refType
  if (refType === 'ORDER') return `/order/${n.refId}`
  if (refType === 'PRODUCT') return `/product/${n.refId}`
  if (refType === 'COUPON' || refType === 'STORE') {
    // 店铺链接需要用户名（店铺路由是 /store/:username），由列表接口批量补全
    return n.storeUsername ? `/store/${n.storeUsername}` : null
  }
  if (refType === 'REVIEW') {
    // 类型 10「你的商品收到新评价」是发给**商家**的 -> 商家评价页并高亮该条
    if (Number(n.type) === 10) return `/seller/reviews?focus=${n.refId}`
    // 类型 11「商家回复了你的评价」是发给**买家**的 -> 换出商品，跳商品详情页看那条评价
    try {
      const r = await reviewDetail(n.refId)
      return r?.productId ? `/product/${r.productId}` : null
    } catch {
      return null
    }
  }
  return null
}

async function open(vo) {
  const n = vo.notification
  // 先标已读再跳转：跳走了这个组件就卸载了，回来时列表已经刷新
  if (Number(n.isRead) === 0) {
    try {
      await markRead(n.id)
    } catch {
      // 标已读失败不该挡住跳转——用户的目标是看详情，不是改状态
    }
  }
  const to = await linkOf(n)
  if (to) {
    router.push(to)
  } else {
    // 没有可跳转的目标（订单类通知理论上都有；商店被注销时会补不出用户名）
    ElMessage.info('该消息没有可跳转的详情')
    load()
  }
}

async function onReadAll() {
  try {
    const n = await markAllRead()
    ElMessage.success(n ? `已将 ${n} 条标记为已读` : '没有未读消息')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onDelete(vo) {
  try {
    await ElMessageBox.confirm('删除这条消息？删除后不可恢复。', '删除消息', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }
  try {
    await deleteMessage(vo.notification.id)
    // 删掉当前页最后一条时回退一页，否则会停在空页上
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
      <h3 class="title">我的消息</h3>
      <el-button size="small" @click="onReadAll">全部已读</el-button>
    </div>

    <el-card shadow="never">
      <el-tabs :model-value="tab" @tab-change="onTab">
        <el-tab-pane v-for="t in TABS" :key="t.name" :label="t.label" :name="t.name" />
      </el-tabs>

      <div v-loading="loading" class="list">
        <el-empty
          v-if="!loading && !list.length"
          :description="unreadOnly ? '没有未读消息' : '还没有消息'"
        />

        <div
          v-for="vo in list"
          :key="vo.notification.id"
          class="item"
          :class="{ unread: Number(vo.notification.isRead) === 0 }"
          @click="open(vo)"
        >
          <span class="dot" aria-hidden="true" />

          <div class="body">
            <div class="row">
              <el-tag :type="meta(vo.notification.type).tag" size="small" effect="light">
                {{ meta(vo.notification.type).text }}
              </el-tag>
              <span class="ititle">{{ vo.notification.title }}</span>
              <span class="time">{{ fmtTime(vo.notification.createdTime) }}</span>
            </div>
            <div class="content">{{ vo.notification.content }}</div>
            <div v-if="vo.storeUsername" class="store">
              来自店铺：{{ vo.storeUsername }}
            </div>
          </div>

          <el-button
            class="del"
            link
            type="danger"
            size="small"
            @click.stop="onDelete(vo)"
          >
            删除
          </el-button>
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
  max-width: 900px;
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
  color: #303133;
}
.list {
  min-height: 120px;
}
.item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 4px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
}
.item:hover {
  background: #fafcff;
}
.item:last-child {
  border-bottom: none;
}
/* 未读圆点：已读时留位但透明，避免整行文字左右跳动 */
.dot {
  width: 8px;
  height: 8px;
  margin-top: 8px;
  border-radius: 50%;
  background: #409eff;
  flex-shrink: 0;
}
.item:not(.unread) .dot {
  background: transparent;
}
.body {
  flex: 1;
  min-width: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ititle {
  font-size: 14px;
  color: #303133;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 未读加粗标题，与圆点一起构成「新消息」的视觉信号 */
.item.unread .ititle {
  font-weight: 600;
}
.time {
  color: #a8abb2;
  font-size: 12px;
  flex-shrink: 0;
}
.content {
  margin-top: 4px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}
.store {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.del {
  flex-shrink: 0;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
