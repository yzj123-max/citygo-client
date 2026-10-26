/**
 * 景点穿搭 Stylist AI 对话接口封装。
 *
 * 接口：POST http://127.0.0.1:8080/api/stylist/chat/sse
 * 请求体：{ question: string, imageBase64: string }
 *   - imageBase64：景点照片的纯 base64 字符串，【不带 data:image/...;base64, 前缀】，由页面截取逗号后内容；
 *   - question：用户输入的提示词，默认「根据这个景点，推荐合适穿搭」。
 *
 * POST 类型 SSE，不能用 EventSource（仅支持 GET）；
 * 流式解析与 OOTD 完全一致，统一复用 src/api/sse.js 的 postJsonStream。
 */
import { BASE_URL } from './request'
import { postJsonStream } from './sse'

/**
 * 发起景点穿搭流式推荐。
 *
 * @param {Object} params
 * @param {string} params.base64        景点照片纯 base64（不含 data:image 前缀），映射为入参 imageBase64
 * @param {string} params.prompt        用户提示词，映射为入参 question
 * @param {AbortSignal} [params.signal] 可选中断信号（重新选择/离开页面时取消请求）
 * @param {(text: string) => void} params.onChunk 每解析出一段正文时回调（页面逐字追加）
 * @returns {Promise<void>} 流正常结束（含服务端关闭连接）时 resolve；异常时 reject(Error)
 */
export function streamStylistRecommendation({ base64, prompt, signal, onChunk }) {
  return postJsonStream({
    // 统一走 BASE_URL（http://127.0.0.1:8080），路径与后端契约严格一致
    path: `${BASE_URL}/api/stylist/chat/sse`,
    // 按后端契约组装 JSON：question（文字）+ imageBase64（纯 base64 图片）
    body: { question: prompt, imageBase64: base64 },
    signal,
    onChunk,
  })
}
