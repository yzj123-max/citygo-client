import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// ============================================================================
// Vite 配置
// ----------------------------------------------------------------------------
// 【跨域说明】
// 1. 本项目【不配置任何 dev 代理】：后端 SpringBoot 已配置全局 CORS，
//    浏览器可直接跨域请求 http://localhost:8080，前端无需再写 proxy。
// 2. 后端地址固定写 http://localhost:8080（见 src/api/request.js），
//    不要写死任何局域网 IP（如 192.168.x.x / 10.x.x.x）。
// 3. USB 真机 adb reverse 调试：
//    - 电脑先执行 adb reverse tcp:8080 tcp:8080，
//      手机对 http://localhost:8080 的访问会经 USB 转发到电脑的 8080 端口；
//    - 如果手机上还要直接打开 Vite 开发页面，再执行一次
//      adb reverse tcp:5173 tcp:5173，然后用手机浏览器访问 http://localhost:5173。
// ============================================================================
export default defineConfig({
  plugins: [vue()],
  server: {
    // host=true 时电脑浏览器与 USB 转发均可访问；端口固定便于 adb reverse
    host: true,
    port: 5173,
  },
})
