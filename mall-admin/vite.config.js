import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端统一入口（Spring Cloud Gateway）。后端地址变了只需改这一处。
const gatewayTarget = process.env.VITE_GATEWAY || 'http://localhost:9999'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      // 管理员内部系统只需业务前缀；/user 登录与用户管理、/product /order /inventory
      '/user': { target: gatewayTarget, changeOrigin: true },
      '/product': { target: gatewayTarget, changeOrigin: true },
      '/order': { target: gatewayTarget, changeOrigin: true },
      '/inventory': { target: gatewayTarget, changeOrigin: true },
    },
  },
})
