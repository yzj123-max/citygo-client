/**
 * 全局 axios 实例 + 请求 / 响应拦截器。
 *
 * 约定：
 * 1. 所有业务接口统一走本实例，baseURL 固定 http://localhost:8080（USB adb reverse 真机调试，
 *    电脑需先执行 adb reverse tcp:8080 tcp:8080），不写任何局域网 IP；
 * 2. 请求拦截器：自动在 header 携带 Authorization: Bearer <token>；
 * 3. 响应拦截器：统一解包后端 { code, message/msg, data } 结构；
 *    - 业务成功（code 为 200 / 0 或无 code 字段）→ 把 data 返回给调用方；
 *    - HTTP 401 或业务 code=401（token 过期 / 未登录）→ 清登录态并跳登录页（带 redirect）；
 *    - 其他错误 → ElMessage 弹窗提示；个别接口可在调用时传 { silent: true } 关闭弹窗自行处理。
 * 4. FormData 上传不要手动设置 Content-Type，交给浏览器自动生成 multipart boundary。
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { BASE_URL } from './request'
import { clearToken, clearUserInfo, getToken } from '../utils/storage'

/** 后端业务成功码（不同项目常见 200 / 0） */
const SUCCESS_CODES = [200, 0]

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
})

/* ------------------------------ 请求拦截器：统一携带 token ------------------------------ */
http.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // FormData（如头像上传）：删除可能被误设的 Content-Type，
    // 让浏览器自动补 multipart/form-data; boundary=xxx，否则后端无法解析文件
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    } else if (!config.headers['Content-Type']) {
      // 普通 JSON 请求默认 JSON 头（不影响显式指定过其他类型的请求）
      config.headers['Content-Type'] = 'application/json'
    }
    return config
  },
  (error) => Promise.reject(error),
)

/* ------------------------------ 响应拦截器：统一解包 / 401 / 错误弹窗 ------------------------------ */

/**
 * token 失效后的统一处理：清本地登录态 → 提示 → 跳登录页并携带 redirect，
 * 登录成功后可回到原页面。已在登录 / 注册页时不重复跳转，避免死循环。
 */
function handleTokenExpired(message = '登录已过期，请重新登录') {
  clearToken()
  clearUserInfo()

  const onAuthPage =
    window.location.pathname.startsWith('/login') ||
    window.location.pathname.startsWith('/register')

  // 无论是否在登录页都给出提示（如账号密码错误返回 401 时）；
  // 仅在非登录页跳转，避免重定向死循环
  ElMessage.warning(message)
  if (!onAuthPage) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    // 使用整页跳转，避免 http 层反向依赖 router 造成循环引用
    window.location.href = `/login?redirect=${redirect}`
  }
}

http.interceptors.response.use(
  (response) => {
    const config = response.config
    const body = response.data

    // 非 JSON 结构（如纯文本 / blob）直接原样返回
    if (body === null || typeof body !== 'object') {
      return body
    }

    // 后端没有统一包装（无 code / success 字段）：整包返回
    const hasBizEnvelope = 'code' in body || 'success' in body
    if (!hasBizEnvelope) {
      return body
    }

    const code = body.code
    const bizSuccess =
      code === undefined ? body.success !== false : SUCCESS_CODES.includes(Number(code))

    if (bizSuccess) {
      // 成功：优先返回 data 字段；没有 data 时返回整个 body，调用方按需取用
      return body.data !== undefined ? body.data : body
    }

    // 业务 code=401：token 过期 / 未登录
    if (Number(code) === 401) {
      handleTokenExpired(body.message || body.msg)
      return Promise.reject(new Error(body.message || body.msg || '登录已过期'))
    }

    const errMsg = body.message || body.msg || `请求失败（code=${code}）`
    if (!config.silent) ElMessage.error(errMsg)
    return Promise.reject(new Error(errMsg))
  },
  (error) => {
    const config = error.config || {}
    const status = error.response?.status

    if (status === 401) {
      // 提示与（必要时）跳转已在 handleTokenExpired 内完成，这里直接 reject，避免重复弹窗
      handleTokenExpired(error.response?.data?.message || error.response?.data?.msg)
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    // 网络层 / 服务端错误：统一友好文案并弹窗（silent 请求由调用方自行处理）
    let message
    if (error.code === 'ERR_NETWORK') {
      message =
        '网络连接失败：请确认已执行 adb reverse tcp:8080 tcp:8080，且后端服务已启动'
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    } else {
      message =
        error.response?.data?.message ||
        error.response?.data?.msg ||
        error.message ||
        '服务异常，请稍后重试'
    }
    if (!config.silent) ElMessage.error(message)
    // 统一以普通 Error 抛出（避免部分环境下 axios error 的 message 只读导致赋值失败）
    return Promise.reject(new Error(message))
  },
)

export default http
