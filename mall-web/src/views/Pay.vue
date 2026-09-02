<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPay, mockPaySuccess } from '../api/pay'
import { money, payMethodTag } from '../utils/format'

const route = useRoute()
const router = useRouter()

const orderId = route.query.orderId
const method = ref(1) // 1=支付宝 2=微信
const loading = ref(false)
const paying = ref(false)
const payOrder = ref(null)
const payParams = ref(null)
const settling = ref(false)

async function doPay() {
  if (!orderId) {
    ElMessage.warning('缺少订单号')
    return
  }
  paying.value = true
  try {
    const vo = await createPay({ orderId: Number(orderId), paymentMethod: Number(method.value) })
    if (vo?.payOrder) {
      payOrder.value = vo.payOrder
      payParams.value = vo.payParams || null
      if (Number(vo.payOrder.paymentStatus) === 1) {
        ElMessage.info('该订单已支付成功')
      }
    }
  } catch {
    // 业务错误（如订单非待付款/无权）已由拦截器提示
  } finally {
    paying.value = false
  }
}

async function mockSuccess() {
  if (!payOrder.value?.payNo) return
  settling.value = true
  try {
    await mockPaySuccess({ payNo: payOrder.value.payNo })
    ElMessage.success('模拟支付成功（本地演示）')
    payOrder.value.paymentStatus = 1
  } catch {
    // 拦截器已提示
  } finally {
    settling.value = false
  }
}

function goOrder() {
  router.push(`/order/${payOrder.value?.orderId || orderId}`)
}

onMounted(() => {
  if (!orderId) {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="crumb">
      <el-button link type="primary" @click="router.back()">← 返回</el-button>
    </div>
    <el-card v-loading="loading" shadow="never">
      <template v-if="!orderId">
        <el-empty description="缺少订单号，无法发起支付" />
      </template>

      <!-- 第一步：选择支付方式 -->
      <template v-else-if="!payOrder">
        <h3 class="title">收银台</h3>
        <div class="method-row">
          <el-radio-group v-model="method">
            <el-radio-button :value="1">支付宝</el-radio-button>
            <el-radio-button :value="2">微信支付</el-radio-button>
          </el-radio-group>
        </div>
        <div class="pay-bar">
          <el-button type="danger" size="large" :loading="paying" @click="doPay">
            确认支付方式并发起支付
          </el-button>
        </div>
        <p class="tip">
          本地演示环境请选择任意方式后，使用「模拟支付成功」完成支付。
        </p>
      </template>

      <!-- 第二步：展示支付参数 -->
      <template v-else>
        <div class="vo-head">
          <div class="amount">
            <span class="a-label">需支付</span>
            <span class="a-num">¥{{ money(payOrder.payAmount) }}</span>
          </div>
          <div class="vo-meta">
            <div>订单号：{{ payOrder.orderId }}</div>
            <div>支付单号：{{ payOrder.payNo }}</div>
            <div>
              支付方式：{{ payMethodTag(payOrder.paymentMethod).text }} · 状态：{{
                Number(payOrder.paymentStatus) === 1 ? '已支付' : '待支付'
              }}
            </div>
          </div>
        </div>

        <el-alert
          v-if="payOrder.paymentStatus !== 1"
          title="本地演示环境不接入真实支付渠道，点击下方按钮即可模拟支付成功。"
          type="info"
          :closable="false"
          show-icon
          class="demo-alert"
        />

        <!-- FORM：后端拼好的表单，直接内嵌渲染 -->
        <div v-if="payParams?.contentType === 'FORM'" class="form-box">
          <div v-html="payParams.content"></div>
          <p class="tip">若上表单向第三方渠道发起，请在真实网关下使用；本地演示请直接用「模拟支付成功」。</p>
        </div>

        <!-- CODE_URL：内容为二维码/图片地址 -->
        <div v-else-if="payParams?.contentType === 'CODE_URL'" class="qr-box">
          <el-image
            v-if="payParams.content"
            :src="payParams.content"
            fit="contain"
            class="qr"
            style="width: 220px; height: 220px"
          />
          <el-empty v-else description="无二维码内容" :image-size="60" />
          <p class="tip">使用对应 App 扫码支付（真实环境）；本地演示请直接用「模拟支付成功」。</p>
        </div>

        <!-- PLACEHOLDER / 未知类型：仅展示提示语 -->
        <el-alert
          v-else
          :title="payParams?.message || '支付参数已生成，可直接发起模拟支付。'"
          type="warning"
          :closable="false"
          show-icon
          class="demo-alert"
        />

        <div class="pay-bar">
          <el-button
            v-if="payOrder.paymentStatus !== 1"
            type="success"
            size="large"
            :loading="settling"
            @click="mockSuccess"
          >
            模拟支付成功
          </el-button>
          <el-button type="primary" size="large" @click="goOrder">查看订单</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
  margin: 0 auto;
}
.crumb {
  margin: 4px 0 10px;
}
.title {
  margin: 4px 0 20px;
}
.method-row {
  margin-bottom: 24px;
}
.pay-bar {
  margin: 20px 0 8px;
}
.tip {
  color: #909399;
  font-size: 13px;
}
.vo-head {
  background: #fdf6ec;
  border-radius: 6px;
  padding: 16px 20px;
  margin-bottom: 16px;
}
.amount {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.a-label {
  color: #606266;
}
.a-num {
  color: #f56c6c;
  font-size: 34px;
  font-weight: 700;
}
.vo-meta {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
  line-height: 1.8;
}
.demo-alert {
  margin-bottom: 12px;
}
.form-box {
  border: 1px dashed #e0e0e0;
  border-radius: 6px;
  padding: 16px;
  overflow: auto;
}
.qr-box {
  text-align: center;
  padding: 12px 0;
}
.qr {
  border: 1px solid #ebeef5;
}
</style>
