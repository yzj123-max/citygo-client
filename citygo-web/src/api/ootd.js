/**
 * OOTD 穿搭 AI 分析接口封装。
 *
 * 接口：POST /api/ai/ootd-stream
 * 请求体：{ base64: string, prompt: string }
 *   - base64：纯 base64 字符串，【不带 data:image/...;base64, 前缀】，由页面截取逗号后内容；
 *   - prompt：用户输入的提示词，默认「帮我分析这套穿搭」。
 *
 * 【为什么不能用 EventSource】
 * EventSource 是浏览器内置 SSE 客户端，但它只支持 GET 请求、不能自定义请求体；
 * 本接口是 POST + JSON body，因此必须使用 fetch + ReadableStream + TextDecoder
 * 手动读取分块响应，实现「服务端推一段、前端解析一段、页面追加一段」的流式效果。
 */
import { BASE_URL, JSON_HEADERS } from './request'
import { getToken } from '../utils/storage'

/** SSE 结束标记（部分后端会在最后一个 data 帧下发 [DONE]） */
const SSE_DONE_FLAG = '[DONE]'

/**
 * data 帧负载为 JSON 时，可能承载正文片段的字段名（按优先级取第一个非空值）。
 * 兼容不同后端写法：{content} / {delta} / {text} / {message} / {answer} / {result}
 * 以及 { data: { content: '...' } } / { data: '...' }。
 */
const TEXT_FIELD_CANDIDATES = ['content', 'delta', 'text', 'message', 'answer', 'result']

/**
 * 从一段 SSE data 负载中提取「本次新增的文字」。
 * 不是 JSON 时按纯文本原样返回；无法识别的 JSON 不把原始 JSON 打到页面上。
 * @param {string} payload
 * @returns {{ done: boolean, text: string } | null}
 */
function extractPayload(payload) {
  if (payload === SSE_DONE_FLAG) return { done: true, text: '' }

  // 尝试按 JSON 解析（Spring SseEmitter 常下发 data:{"content":"xxx"}）
  let json = null
  try {
    json = JSON.parse(payload)
  } catch {
    // 非 JSON：整段就是正文片段
    return { done: false, text: payload }
  }

  if (json && typeof json === 'object') {
    // 业务错误结构（后端可能返回 { code: 500, message/msg: '...' }），交给上层展示
    const code = json.code ?? json.status
    if (typeof code === 'number' && code !== 0 && code !== 200) {
      const errMsg = json.message || json.msg || `服务异常（code=${code}）`
      throw new Error(errMsg)
    }
    for (const key of TEXT_FIELD_CANDIDATES) {
      const value = json[key]
      if (typeof value === 'string' && value.length > 0) return { done: false, text: value }
    }
    // 兼容 { data: {...} } / { data: "..." }
    const data = json.data
    if (typeof data === 'string' && data.length > 0) return { done: false, text: data }
    if (data && typeof data === 'object') {
      for (const key of TEXT_FIELD_CANDIDATES) {
        const value = data[key]
        if (typeof value === 'string' && value.length > 0) return { done: false, text: value }
      }
    }
    // JSON 但没有可识别的文本字段：忽略该帧，避免把 JSON 原文渲染到结果区
    return { done: false, text: '' }
  }

  return { done: false, text: '' }
}

/**
 * 发起 OOTD 流式分析。
 *
 * @param {Object} params
 * @param {string} params.base64       纯 base64（不含 data:image 前缀）
 * @param {string} params.prompt       用户提示词
 * @param {AbortSignal} [params.signal] 可选中断信号（重新选择/离开页面时取消请求）
 * @param {(text: string) => void} params.onChunk 每解析出一段正文时回调（页面逐字追加）
 * @returns {Promise<void>} 流正常结束（含服务端关闭连接）时 resolve；异常时 reject(Error)
 */
export async function streamOotdAnalysis({ base64, prompt, signal, onChunk }) {
  let response
  // 追加时间戳参数，禁用 WebView / 浏览器缓存，避免一直加载旧响应
  const baseRequestUrl = `${BASE_URL}/api/ai/ootd-stream`
  const requestUrl = `${baseRequestUrl}?_t=${Date.now()}`
  try {
    console.log('[OOTD] POST', requestUrl)
    response = await fetch(requestUrl, {
      method: 'POST', // 明确是 POST，EventSource 做不到，只能 fetch
      // 流式接口走原生 fetch（不经 axios 拦截器），这里手动兜底携带登录 token；
      // 未登录时不带该头（OOTD 页本身为公开页）
      headers: {
        ...JSON_HEADERS,
        ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
      },
      body: JSON.stringify({ base64, prompt }),
      signal,
    })
  } catch (networkError) {
    // 用户主动取消不算错误
    if (networkError?.name === 'AbortError') throw networkError
    // 详细网络异常打印：完整 RequestURL 方便 chrome://inspect 排查
    console.error('[OOTD] 网络异常', { requestUrl, error: networkError })
    throw new Error(
      '网络连接失败：请确认手机已通过 USB 连接电脑，并已在电脑执行 ' +
        'adb reverse tcp:8080 tcp:8080，且后端服务已启动。',
    )
  }

  // HTTP 状态码非 2xx：读取错误描述并抛出，由页面友好提示
  if (!response.ok) {
    const errText = await response.text().catch(() => '')
    // 详细打印状态码 + 响应体，方便定位 404 / 500 等问题
    console.error('[OOTD] HTTP 非 2xx', { requestUrl, status: response.status, statusText: response.statusText, body: errText })
    throw new Error(`接口报错（HTTP ${response.status}）${errText ? '：' + errText : ''}`)
  }
  if (!response.body) {
    // 极个别旧浏览器不返回 ReadableStream
    throw new Error('当前浏览器不支持流式读取（ReadableStream），请更换浏览器或升级 WebView。')
  }

  const reader = response.body.getReader()
  // TextDecoder 以 UTF-8 增量解码；stream:true 保证多字节中文被 chunk 截断时不会乱码
  const decoder = new TextDecoder('utf-8')

  // 接收缓冲：SSE 以换行分帧，最后一个分块可能只有半行，先缓存到下一轮拼接
  let buffer = ''
  // 是否已观察到标准 SSE 帧（data: 开头）；没观察到时按「后端直接吐纯文本流」处理
  let seenSseFrame = false

  /**
   * 处理一行文本：标准 SSE 取 data: 负载；纯文本模式直接当正文。
   * @returns {boolean} 是否收到 [DONE]
   */
  const handleLine = (rawLine) => {
    const line = rawLine.replace(/\r$/, '')
    const trimmed = line.trim()
    if (!trimmed) return false
    // SSE 注释行
    if (trimmed.startsWith(':')) return false
    // SSE 元信息帧（事件名/ id / 重试间隔），正文不用关心
    if (/^(event|id|retry)\s*:/i.test(trimmed)) return false

    if (trimmed.startsWith('data:')) {
      seenSseFrame = true
      const payload = trimmed.slice(5).trim()
      if (!payload) return false
      const parsed = extractPayload(payload)
      if (parsed.done) return true
      if (parsed.text) onChunk(parsed.text)
      return false
    }

    // 标准 SSE 模式下未知帧忽略；纯文本流模式下整行即正文（补回换行保持排版）
    if (!seenSseFrame) onChunk(`${line}\n`)
    return false
  }

  // 主读取循环：服务端每推一块就解码、分帧、回调一次
  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    // 最后一段可能是不完整的半行，留到下一轮拼接
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      const finished = handleLine(line)
      if (finished) {
        // 收到 [DONE]：主动释放读取器并结束
        await reader.cancel().catch(() => {})
        return
      }
    }
  }

  // 流结束后冲刷缓冲区里剩余的最后一行
  if (buffer.trim()) handleLine(buffer)
  // flush TextDecoder 尾部
  const tail = decoder.decode()
  if (tail.trim()) handleLine(tail)
}
