import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：开发服务器通过代理转发后端接口，避免浏览器跨域问题
// 前端请求 /travel/xxx 时，由 Vite 开发服务器代理到 http://localhost:8080/travel/xxx
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 代理前缀严格为 /travel，匹配提交任务与路径变量查询两个接口
      '/travel': {
        target: 'http://localhost:8080', // SpringBoot 后端地址
        changeOrigin: true, // 修改请求头 Host 为目标地址，避免后端 Host 校验问题
      },
    },
  },
})
