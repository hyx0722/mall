<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '../api/product'
import { listAddresses } from '../api/address'
import { createOrder } from '../api/order'
import { money } from '../utils/format'

const route = useRoute()
const router = useRouter()

const productId = route.query.productId
const qty = Math.max(1, Number(route.query.qty) || 1)

const loading = ref(true)
const product = ref(null)
const addresses = ref([])
const selectedAddressId = ref(null)
const remark = ref('')
const submitting = ref(false)

const addressText = (a) =>
  [a.province, a.city, a.district, a.detailAddress].filter(Boolean).join(' ') ||
  '（地址不完整）'

const defaultAddress = computed(() => addresses.value.find((a) => Number(a.isDefault) === 1))
const totalPrice = () => money(Number(product.value?.price || 0) * qty)

async function load() {
  loading.value = true
  try {
    const [prod, addrList] = await Promise.all([
      productId ? getProductDetail(productId) : Promise.resolve(null),
      listAddresses(),
    ])
    product.value = prod || null
    addresses.value = addrList || []
    // 优先默认地址
    if (defaultAddress.value) selectedAddressId.value = defaultAddress.value.id
    else if (addresses.value.length) selectedAddressId.value = addresses.value[0].id
  } catch {
    product.value = null
    addresses.value = []
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!product.value) {
    ElMessage.error('商品信息加载失败')
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
      items: [{ productId: Number(productId), quantity: qty }],
    })
    if (order?.id) {
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
      <template v-if="product">
        <el-card class="box" shadow="never">
          <template #header><span class="t">商品信息</span></template>
          <div class="row">
            <el-image v-if="product.mainImage" :src="product.mainImage" fit="cover" class="thumb" />
            <div class="meta">
              <div class="name">{{ product.name }}</div>
              <div class="price">¥{{ money(product.price) }} × {{ qty }}</div>
              <div class="total">合计：<b>¥{{ totalPrice() }}</b></div>
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

        <div class="footer">
          <el-button type="danger" size="large" :loading="submitting" @click="submit">
            提交订单
          </el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="商品不存在或未指定" />
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
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.row {
  display: flex;
  gap: 16px;
}
.thumb {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  flex-shrink: 0;
  background: #f5f7fa;
}
.meta {
  flex: 1;
}
.name {
  font-size: 16px;
  color: #303133;
}
.price {
  margin-top: 8px;
  color: #909399;
}
.total {
  margin-top: 12px;
  color: #303133;
}
.total b {
  color: #f56c6c;
  font-size: 20px;
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
  justify-content: flex-end;
}
</style>
