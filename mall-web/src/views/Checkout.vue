<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '../api/product'
import { listAddresses } from '../api/address'
import { createOrder } from '../api/order'
import { removeCartItems } from '../api/cart'
import { usableCoupons } from '../api/coupon'
import { money } from '../utils/format'

const route = useRoute()
const router = useRouter()

// 结算页支持两种进入方式（都不需要再回查购物车，刷新页面也不丢）：
//  1) 商品详情「立即购买」：?productId=1&qty=2
//  2) 购物车「去结算」：    ?items=1:2,3:1（productId:quantity 逗号分隔）
const itemsParam = String(route.query.items || '')
const fromCart = !!itemsParam

// 解析出「要结算什么」——只有 id 和数量，价格稍后从服务端实时拉取
const requested = (() => {
  if (itemsParam) {
    return itemsParam
      .split(',')
      .map((seg) => {
        const [pid, q] = seg.split(':')
        return { productId: Number(pid), quantity: Math.max(1, Number(q) || 1) }
      })
      .filter((it) => Number.isFinite(it.productId) && it.productId > 0)
  }
  const pid = Number(route.query.productId)
  if (!pid) return []
  return [{ productId: pid, quantity: Math.max(1, Number(route.query.qty) || 1) }]
})()

const loading = ref(true)
const lines = ref([]) // { productId, quantity, product }
const addresses = ref([])
const selectedAddressId = ref(null)
const remark = ref('')
const submitting = ref(false)

// 优惠券：coupons 由后端一次性评估本单的可用性与抵扣额（规则只在 user 服务实现一份）
const coupons = ref([])
const selectedCouponId = ref(null)

// 传给后端的商品快照明细——categoryId 是「指定分类限定」券判定的依据
const couponLines = computed(() =>
  lines.value
    .filter((l) => l.product)
    .map((l) => ({
      productId: l.productId,
      categoryId: l.product.categoryId ?? null,
      lineTotal: Number(l.product.price) * l.quantity,
    })),
)

const selectedCoupon = computed(() =>
  coupons.value.find((c) => c.userCouponId === selectedCouponId.value) || null,
)

// 抵扣额以**后端返回值**为准，前端不自己算券规则
const discountAmount = computed(() => Number(selectedCoupon.value?.deduction || 0))

// 实付 = 合计 - 抵扣（与后端 payment 侧的 payableAmount 同一口径）
const payableAmount = computed(() => Math.max(0, totalAmount.value - discountAmount.value))

const addressText = (a) =>
  [a.province, a.city, a.district, a.detailAddress].filter(Boolean).join(' ') ||
  '（地址不完整）'

const defaultAddress = computed(() => addresses.value.find((a) => Number(a.isDefault) === 1))

// 合计以服务端返回的实时价格计算，前端不存价格副本
const totalAmount = computed(() =>
  lines.value.reduce((sum, l) => sum + Number(l.product?.price || 0) * l.quantity, 0),
)

// 有商品已下架/被删除时禁止提交，避免下单才报错
const unavailableLines = computed(() => lines.value.filter((l) => !l.product))

async function load() {
  loading.value = true
  try {
    const [addrList, ...products] = await Promise.all([
      listAddresses(),
      ...requested.map((it) => getProductDetail(it.productId).catch(() => null)),
    ])
    addresses.value = addrList || []
    lines.value = requested.map((it, i) => ({
      productId: it.productId,
      quantity: it.quantity,
      product: products[i] || null,
    }))
    if (defaultAddress.value) selectedAddressId.value = defaultAddress.value.id
    else if (addresses.value.length) selectedAddressId.value = addresses.value[0].id
    await loadCoupons()
  } catch {
    addresses.value = []
    lines.value = []
  } finally {
    loading.value = false
  }
}

// 券列表失败不影响结算本身：拉不到就当没券可选
async function loadCoupons() {
  if (!couponLines.value.length) {
    coupons.value = []
    return
  }
  try {
    coupons.value = (await usableCoupons(couponLines.value)) || []
    // 默认不加券——抵扣多少由用户自己决定，不替用户做主
    selectedCouponId.value = null
  } catch {
    coupons.value = []
  }
}

async function submit() {
  if (!lines.value.length) {
    ElMessage.error('结算商品为空')
    return
  }
  if (unavailableLines.value.length) {
    ElMessage.error('部分商品已下架或不存在，请返回购物车调整')
    return
  }
  if (!selectedAddressId.value) {
    ElMessage.warning('请先选择收货地址')
    return
  }
  submitting.value = true
  try {
    const order = await createOrder({
      addressId: selectedAddressId.value,
      remark: remark.value || undefined,
      // 券由后端校验并核销；抵扣额以后端试算结果为准
      userCouponId: selectedCouponId.value || undefined,
      items: lines.value.map((l) => ({ productId: l.productId, quantity: l.quantity })),
    })
    if (order?.id) {
      // 购物车结算成功后清理已下单的条目；清理失败不影响下单结果（尽力而为）
      if (fromCart) {
        try {
          await removeCartItems(lines.value.map((l) => l.productId))
        } catch {
          // 忽略：订单已创建成功，购物车残留可由用户手动清理
        }
      }
      ElMessage.success('下单成功，等待支付')
      router.replace({ path: '/pay', query: { orderId: order.id } })
    }
  } catch {
    // 错误提示由 api 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="crumb">
      <el-button link type="primary" @click="router.back()">← 返回</el-button>
    </div>
    <div v-loading="loading" class="wrap">
      <template v-if="lines.length">
        <el-card class="box" shadow="never">
          <template #header>
            <div class="hdr">
              <span class="t">商品信息</span>
              <span class="count">共 {{ lines.length }} 种商品</span>
            </div>
          </template>
          <div v-for="l in lines" :key="l.productId" class="row">
            <el-image
              v-if="l.product?.mainImage"
              :src="l.product.mainImage"
              fit="cover"
              class="thumb"
            />
            <div v-else class="thumb thumb-fallback">无图</div>
            <div class="meta">
              <div class="name">{{ l.product?.name || '商品已下架或不存在' }}</div>
              <el-tag v-if="!l.product" type="info" size="small">不可购买</el-tag>
              <div class="price">
                <template v-if="l.product">¥{{ money(l.product.price) }} × {{ l.quantity }}</template>
                <template v-else>-</template>
              </div>
            </div>
            <div v-if="l.product" class="line-total">
              ¥{{ money(Number(l.product.price) * l.quantity) }}
            </div>
          </div>
        </el-card>

        <el-card class="box" shadow="never">
          <template #header>
            <div class="hdr">
              <span class="t">选择收货地址</span>
              <el-button link type="primary" @click="router.push('/address')">
                管理收货地址
              </el-button>
            </div>
          </template>
          <el-empty
            v-if="!addresses.length"
            description="暂无收货地址，请先添加"
            :image-size="80"
          >
            <el-button type="primary" @click="router.push('/address')">去添加地址</el-button>
          </el-empty>
          <el-radio-group v-else v-model="selectedAddressId" class="addr-group">
            <el-radio v-for="a in addresses" :key="a.id" :value="a.id" class="addr-item">
              <div class="addr-line">
                <span class="who">{{ a.receiverName }} {{ a.receiverPhone }}</span>
                <el-tag v-if="Number(a.isDefault) === 1" size="small" type="danger">默认</el-tag>
              </div>
              <div class="addr-detail">{{ addressText(a) }}</div>
            </el-radio>
          </el-radio-group>
        </el-card>

        <el-card class="box" shadow="never">
          <template #header><span class="t">订单备注</span></template>
          <el-input
            v-model="remark"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="选填，给卖家留言"
          />
        </el-card>

        <el-card v-if="coupons.length" class="box" shadow="never">
          <template #header>
            <div class="hdr">
              <span class="t">优惠券</span>
              <el-button link type="primary" @click="router.push('/coupons')">
                去领券中心
              </el-button>
            </div>
          </template>
          <el-radio-group v-model="selectedCouponId" class="coupon-group">
            <el-radio :value="null" class="coupon-item">
              <span class="c-name">不使用优惠券</span>
            </el-radio>
            <el-radio
              v-for="c in coupons"
              :key="c.userCouponId"
              :value="c.userCouponId"
              :disabled="!c.usable"
              class="coupon-item"
            >
              <div class="c-line">
                <span class="c-rule">{{ c.rule }}</span>
                <span class="c-name">{{ c.name }}</span>
                <!-- 不可用的券也列出来并说明原因，而不是让它凭空消失 -->
                <span v-if="!c.usable" class="c-reason">{{ c.reason }}</span>
                <span v-else class="c-save">可省 ¥{{ money(c.deduction) }}</span>
              </div>
            </el-radio>
          </el-radio-group>
        </el-card>

        <div class="footer">
          <div class="summary">
            <div>
              合计：<span class="amount">¥{{ money(totalAmount) }}</span>
            </div>
            <template v-if="discountAmount > 0">
              <div class="discount">优惠：-¥{{ money(discountAmount) }}</div>
              <div>
                实付：<span class="amount">¥{{ money(payableAmount) }}</span>
              </div>
            </template>
          </div>
          <el-button
            type="danger"
            size="large"
            :loading="submitting"
            :disabled="!!unavailableLines.length"
            @click="submit"
          >
            提交订单
          </el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="没有要结算的商品" />
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 860px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.wrap {
  min-height: 200px;
}
.box {
  margin-bottom: 16px;
}
.t {
  font-weight: 600;
}
.count {
  color: #909399;
  font-size: 13px;
}
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.row {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}
.row:last-child {
  border-bottom: none;
}
.thumb {
  width: 84px;
  height: 84px;
  border-radius: 6px;
  flex-shrink: 0;
  background: #f5f7fa;
}
.thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
}
.meta {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 16px;
  color: #303133;
}
.price {
  margin-top: 8px;
  color: #909399;
}
.line-total {
  color: #f56c6c;
  font-weight: 600;
}
.addr-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
}
.addr-item {
  height: auto;
  margin-right: 0;
  padding: 10px 4px;
  border-bottom: 1px solid #f0f2f5;
  white-space: normal;
}
.addr-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.who {
  font-weight: 600;
  color: #303133;
}
.addr-detail {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
  padding-left: 0;
}
.footer {
  margin: 8px 0 24px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
}
.summary {
  color: #606266;
}
.amount {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}
.summary {
  text-align: right;
  line-height: 1.9;
}
.discount {
  color: #67c23a;
  font-size: 13px;
}
.coupon-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}
.coupon-item {
  height: auto;
  margin-right: 0;
  padding: 10px 4px;
  border-bottom: 1px solid #f0f2f5;
  white-space: normal;
}
.coupon-item:last-child {
  border-bottom: none;
}
.c-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.c-rule {
  font-weight: 600;
  color: #f56c6c;
}
.c-name {
  color: #303133;
}
.c-reason {
  font-size: 12px;
  color: #909399;
}
.c-save {
  font-size: 12px;
  color: #67c23a;
}
</style>
