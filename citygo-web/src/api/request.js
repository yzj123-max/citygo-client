/**
 * 全局后端地址与通用请求配置。
 *
 * 【USB adb reverse 真机调试约定】
 * - 后端地址固定写 http://127.0.0.1:8080，禁止写 localhost 或局域网 IP；
 *   （adb reverse tcp:8080 tcp:8080 转发的是 127.0.0.1，部分 WebView/系统对 localhost
 *    解析行为不一致，统一用 127.0.0.1 可避免偶发连接失败）
 * - 真机通过 USB 连接电脑并执行：adb reverse tcp:8080 tcp:8080
 *   此时手机浏览器/页面内对 127.0.0.1:8080 的请求会被 USB 隧道转发到电脑的 8080；
 * - 跨域：后端 SpringBoot 已开启全局 CORS，前端不需要配置 Vite 代理。
 */
export const BASE_URL = 'http://127.0.0.1:8080'

/** 统一 JSON 请求头（仅在需要 JSON 的 POST 请求上单独使用，不做全局固定） */
export const JSON_HEADERS = {
  'Content-Type': 'application/json',
  Accept: 'text/event-stream, application/json, text/plain;q=0.9, */*;q=0.8',
}
