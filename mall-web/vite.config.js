import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端统一入口（Spring Cloud Gateway）。后端地址变了只需改这一处。
const gatewayTarget = process.env.VITE_GATEWAY || 'http://localhost:9999'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 网关按 /user /product /order /inventory /pay 前缀路由，这里原样转发即可，无需 rewrite。
      '/user': { target: gatewayTarget, changeOrigin: true },
      '/product': { target: gatewayTarget, changeOrigin: true },
      '/order': { target: gatewayTarget, changeOrigin: true },
      '/inventory': { target: gatewayTarget, changeOrigin: true },
      '/pay': { target: gatewayTarget, changeOrigin: true },
    },
  },
})
