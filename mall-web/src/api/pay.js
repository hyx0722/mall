import request from './request'

// 支付（mall-service-payment PayController，网关前缀 /pay）
// alipay/wx notify 是第三方服务端异步回调，前端绝不调用，这里不封装。

// 发起支付：body { orderId, paymentMethod }，paymentMethod 1=支付宝 2=微信
// 返回 CreatePayOrderVO { payOrder, payParams: { contentType, content, message } }
export function createPay(payload) {
  return request.post('/pay/create', payload)
}

// 本地/演示环境的模拟支付成功：body { payNo }（payNo 取 createPay 返回的 payOrder.payNo）
export function mockPaySuccess(payload) {
  return request.post('/pay/mock/success', payload)
}
