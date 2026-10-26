<script setup>
/**
 * OOTD 穿搭 AI 分析页（移动端优先，垂直布局）。
 *
 * 功能：
 * 1. 拍照 / 相册选择图片，选中后即时预览；
 * 2. FileReader 读取为 DataURL，JS 截取逗号之后的纯 base64（不带 data:image 前缀）；
 * 3. 自定义提示词输入框（默认：帮我分析这套穿搭）；
 * 4. 提交后 fetch POST /api/ai/ootd-stream，请求期间按钮 loading 置灰；
 * 5. ReadableStream + TextDecoder 逐块读取 SSE，再用打字机方式逐字追加渲染；
 * 6. 支持清空结果、重新选图重试；网络异常 / 接口报错页面内友好提示，不白屏崩溃。
 *
 * 跨域：不使用 Vite 代理，依赖后端 SpringBoot 全局 CORS；详见 src/api/request.js。
 */
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { streamOotdAnalysis } from '../api/ootd'

/* ------------------------------ 常量配置 ------------------------------ */

/** 提示词默认值（输入框 placeholder 使用同一句，提交空内容时也回退到它） */
const DEFAULT_PROMPT = '帮我分析这套穿搭'
/** 允许上传的图片 MIME 前缀 */
const IMAGE_MIME_PREFIX = 'image/'
/** 图片大小上限：10MB（base64 体积约再膨胀 1/3，过大容易被后端拒绝） */
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
/** 打字机定时间隔（ms）与每次吐出的字符数：间隔越小/字数越多，出字越快 */
const TYPE_INTERVAL_MS = 24
const TYPE_CHARS_PER_TICK = 2

/* ------------------------------ 响应式状态 ------------------------------ */

/** 隐藏的「拍照」file input（capture 在手机上会直接调起摄像头） */
const cameraInputRef = ref(null)
/** 隐藏的「相册」file input */
const albumInputRef = ref(null)

/** 当前选中的图片文件、预览地址（blob object URL）、纯 base64 */
const selectedFile = ref(null)
const previewUrl = ref('')
const imageBase64 = ref('')

/** 用户输入的提示词 */
const promptText = ref('')
/** 是否请求中（上传/流式读取阶段），按钮 loading + 置灰 */
const loading = ref(false)
/** 打字机是否仍在把已接收文本吐完 */
const typing = ref(false)
/** 流式分析结果全文（打字机逐字追加到这里） */
const resultText = ref('')
/** 错误提示文案（空串表示无错误） */
const errorMessage = ref('')
/** 状态条文案（等待响应 / 接收中） */
const statusText = ref('')

/** 已接收但尚未被打字机吐出的字符队列 */
let pendingChars = ''
/** 服务端流是否已结束（结束后等队列吐完再关 loading） */
let streamFinished = false
/** 打字机定时器 id */
let typeTimer = null
/** 当前请求的中断控制器（重新选图 / 离开页面时取消 fetch） */
let abortController = null

/** 结果展示区元素引用，用于流式输出时自动滚到底部 */
const resultBoxRef = ref(null)

/** 提交按钮是否可点：必须已选图且非忙碌态 */
const canSubmit = computed(() => !!imageBase64.value && !loading.value)

/* ------------------------------ 图片选择与读取 ------------------------------ */

/** 触发拍照 input */
function pickFromCamera() {
  cameraInputRef.value?.click()
}

/** 触发相册 input */
function pickFromAlbum() {
  albumInputRef.value?.click()
}

/**
 * 处理原生 file input 的选择结果。
 * @param {Event} event input change 事件
 */
async function handleFileChange(event) {
  const file = event.target.files?.[0]
  // 允许重复选择同一张图：清空 input 值，保证再次选择同名文件仍触发 change
  event.target.value = ''
  if (!file) return

  if (!file.type.startsWith(IMAGE_MIME_PREFIX)) {
    errorMessage.value = '请选择图片文件（拍照或从相册选择）'
    return
  }
  if (file.size > MAX_IMAGE_SIZE) {
    errorMessage.value = '图片不能超过 10MB，请重新选择'
    return
  }

  // 新一轮选图：取消旧请求、清空旧结果与错误
  resetRunningState({ keepImage: false })
  errorMessage.value = ''

  try {
    const { dataUrl, pureBase64 } = await readImageAsBase64(file)
    selectedFile.value = file
    // 释放旧预览地址，避免内存泄漏
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = dataUrl
    imageBase64.value = pureBase64
  } catch (e) {
    errorMessage.value = '图片读取失败，请换一张再试'
  }
}

/**
 * 用 FileReader 把图片读成 DataURL，并截取「逗号之后」的纯 base64。
 * DataURL 形如：data:image/jpeg;base64,/9j/4AAQSk...
 * 后端要求只传逗号后的部分，严禁携带 data:image/...;base64, 前缀。
 * @param {File} file
 * @returns {Promise<{dataUrl: string, pureBase64: string}>}
 */
function readImageAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const dataUrl = String(reader.result || '')
      const commaIndex = dataUrl.indexOf(',')
      // 正常 DataURL 一定含逗号；异常情况下兜底为整个字符串
      const pureBase64 = commaIndex >= 0 ? dataUrl.slice(commaIndex + 1) : dataUrl
      resolve({ dataUrl, pureBase64 })
    }
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

/* ------------------------------ 提交与流式读取 ------------------------------ */

/** 点击「开始分析」 */
async function handleSubmit() {
  if (!imageBase64.value) {
    errorMessage.value = '请先拍照或从相册选择一张穿搭图片'
    return
  }

  // 重置上一轮结果
  resultText.value = ''
  pendingChars = ''
  streamFinished = false
  errorMessage.value = ''
  loading.value = true
  typing.value = false
  stopTypeTimer()

  abortController = new AbortController()
  const prompt = promptText.value.trim() || DEFAULT_PROMPT
  statusText.value = '正在连接 AI 服务…'

  try {
    // fetch + ReadableStream 逐块接收；每解析出一段文本就压入打字机队列
    await streamOotdAnalysis({
      base64: imageBase64.value,
      prompt,
      signal: abortController.signal,
      onChunk: (chunk) => {
        statusText.value = 'AI 正在分析…'
        pendingChars += chunk
        // 首次收到内容时启动打字机（不在提交瞬间启动，避免空闪光标）
        ensureTypeTimerStarted()
      },
    })

    // 流正常结束（服务端关闭连接属正常现象）；等打字机把队列吐完再关 loading
    streamFinished = true
    statusText.value = pendingChars ? 'AI 正在逐字输出…' : '分析完成'
    if (!pendingChars) finishLoading()
  } catch (error) {
    // 用户主动取消（重新选图 / 离开页面）：静默处理（调用方已复位 loading）
    if (error?.name === 'AbortError') {
      return
    }
    // 异常：停止打字机，页面内展示友好错误，App 不崩溃、不白屏
    stopTypeTimer()
    typing.value = false
    loading.value = false
    errorMessage.value = error?.message || '分析失败，请稍后重试'
    statusText.value = ''
  } finally {
    abortController = null
  }
}

/** 懒启动打字机定时器 */
function ensureTypeTimerStarted() {
  if (typeTimer !== null) return
  typing.value = true
  typeTimer = setInterval(typeTick, TYPE_INTERVAL_MS)
}

/** 打字机每一拍：从队列头部取若干字符追加到结果区，并自动滚动 */
function typeTick() {
  if (pendingChars.length > 0) {
    const piece = pendingChars.slice(0, TYPE_CHARS_PER_TICK)
    pendingChars = pendingChars.slice(TYPE_CHARS_PER_TICK)
    resultText.value += piece
    scrollResultToBottom()
  }

  // 队列已空：流结束则收尾；否则保持定时器等待后续分块（避免文字中途停顿闪烁）
  if (pendingChars.length === 0 && streamFinished) {
    stopTypeTimer()
    typing.value = false
    statusText.value = '分析完成'
    finishLoading()
  }
}

/** 结果区跟随打字进度滚动到底部 */
function scrollResultToBottom() {
  nextTick(() => {
    const el = resultBoxRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/**
 * 结束忙碌态：仅当「服务端流已结束」且「打字机不在运行」时关闭 loading。
 * 打字机仍在吐字时由 [typeTick] 在队列清空后自行调用本方法收尾。
 */
function finishLoading() {
  if (streamFinished && typeTimer === null) {
    loading.value = false
    typing.value = false
  }
}

/* ------------------------------ 清空 / 重选 / 生命周期 ------------------------------ */

/** 清空分析结果（保留已选图片与提示词，可直接再次提交） */
function handleClearResult() {
  abortRunningRequest()
  resultText.value = ''
  pendingChars = ''
  streamFinished = false
  errorMessage.value = ''
  statusText.value = ''
  loading.value = false
  typing.value = false
  stopTypeTimer()
}

/** 重新上传新图：取消请求 + 清空图片与结果，并直接调起相册 */
function handleRetake() {
  abortRunningRequest()
  selectedFile.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  imageBase64.value = ''
  resultText.value = ''
  pendingChars = ''
  errorMessage.value = ''
  statusText.value = ''
  loading.value = false
  typing.value = false
  stopTypeTimer()
  pickFromAlbum()
}

/**
 * 中断进行中的请求与打字机。
 * @param {{keepImage?: boolean}} options
 */
function resetRunningState() {
  abortRunningRequest()
  stopTypeTimer()
  pendingChars = ''
  streamFinished = false
  loading.value = false
  typing.value = false
  resultText.value = ''
  statusText.value = ''
}

/** 中断当前 fetch（若有） */
function abortRunningRequest() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
}

/** 停止打字机定时器 */
function stopTypeTimer() {
  if (typeTimer !== null) {
    clearInterval(typeTimer)
    typeTimer = null
  }
}

/** 组件卸载：取消请求、清理定时器与 object URL，防止泄漏与后台报错 */
onBeforeUnmount(() => {
  abortRunningRequest()
  stopTypeTimer()
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})

/** 关闭错误条（el-alert 的关闭回调） */
function dismissError() {
  errorMessage.value = ''
}

/** 复制分析结果 */
async function copyResult() {
  if (!resultText.value) return
  try {
    await navigator.clipboard.writeText(resultText.value)
    ElMessage.success('分析结果已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制')
  }
}
</script>

<template>
  <section class="ootd-page">
    <!-- ① 图片选择与预览 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel__header">
          <span>① 选择穿搭照片</span>
          <el-tag v-if="selectedFile" size="small" type="success">已选择</el-tag>
        </div>
      </template>

      <!-- 已选图片预览 -->
      <div v-if="previewUrl" class="preview-wrap">
        <img class="preview-img" :src="previewUrl" alt="穿搭照片预览" />
        <div class="preview-actions">
          <el-button size="small" @click="pickFromCamera">重新拍照</el-button>
          <el-button size="small" @click="pickFromAlbum">相册重选</el-button>
          <el-button size="small" type="danger" plain @click="handleRetake">换一张重试</el-button>
        </div>
      </div>

      <!-- 未选图片时的两个入口 -->
      <div v-else class="pickers">
        <el-button type="primary" class="picker-btn" @click="pickFromCamera">
          拍照
        </el-button>
        <el-button plain type="primary" class="picker-btn" @click="pickFromAlbum">
          从相册选择
        </el-button>
        <p class="picker-tip">图片仅用于本次穿搭分析，支持 JPG / PNG，大小不超过 10MB</p>
      </div>

      <!-- 隐藏的两个文件选择器：拍照（capture）与相册分离 -->
      <input
        ref="cameraInputRef"
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        @change="handleFileChange"
      />
      <input
        ref="albumInputRef"
        type="file"
        accept="image/*"
        hidden
        @change="handleFileChange"
      />
    </el-card>

    <!-- ② 自定义提示词 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel__header"><span>② 告诉 AI 你想了解什么</span></div>
      </template>
      <el-input
        v-model="promptText"
        type="textarea"
        :rows="3"
        :placeholder="DEFAULT_PROMPT"
        :disabled="loading"
        maxlength="200"
        show-word-limit
        resize="none"
      />
    </el-card>

    <!-- ③ 提交 -->
    <el-button
      type="primary"
      class="submit-btn"
      :loading="loading"
      :disabled="!canSubmit"
      @click="handleSubmit"
    >
      {{ loading ? statusText || 'AI 分析中…' : '开始 AI 分析' }}
    </el-button>

    <!-- 错误提示（网络异常 / 接口报错，页面内展示，不影响后续重试） -->
    <el-alert
      v-if="errorMessage"
      class="error-alert"
      type="error"
      show-icon
      :closable="true"
      title="分析出错了"
      :description="errorMessage"
      @close="dismissError"
    />

    <!-- ④ 流式结果区：逐字追加 + 接收中闪烁光标 -->
    <el-card v-if="resultText || loading" shadow="never" class="panel result-panel">
      <template #header>
        <div class="panel__header">
          <span>③ AI 分析结果</span>
          <div class="result-tools">
            <el-tag v-if="typing" size="small" type="warning">逐字输出中</el-tag>
            <el-tag v-else-if="loading" size="small">接收中…</el-tag>
            <el-button v-if="resultText && !loading" link size="small" @click="copyResult">
              复制
            </el-button>
            <el-button link size="small" @click="handleClearResult">清空结果</el-button>
          </div>
        </div>
      </template>

      <div ref="resultBoxRef" class="result-box">
        <span class="result-text">{{ resultText }}</span>
        <!-- 流式/打字进行中的闪烁光标 -->
        <span v-if="loading || typing" class="typing-cursor" />
      </div>
    </el-card>

    <!-- 底部操作：已有结果时提供清空 / 换图重试 -->
    <div v-if="resultText || previewUrl" class="bottom-actions">
      <el-button plain :disabled="loading" @click="handleClearResult">清空结果</el-button>
      <el-button type="primary" plain :disabled="loading" @click="handleRetake">
        重新上传新图
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.ootd-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 8px;
}

.panel {
  border-radius: 14px;
  border: 1px solid #eef1f5;
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #24303f;
}

/* ---------- 图片选择 / 预览 ---------- */
.pickers {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
}

.picker-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  border-radius: 10px;
}

.picker-tip {
  margin: 2px 0 0;
  font-size: 12px;
  color: #97a0af;
  text-align: center;
}

.preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-img {
  width: 100%;
  max-height: 360px;
  object-fit: contain;
  border-radius: 10px;
  background: #f5f7fa;
}

.preview-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- 提交按钮 ---------- */
.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
}

.error-alert {
  border-radius: 10px;
}

/* ---------- 流式结果 ---------- */
.result-panel :deep(.el-card__body) {
  padding-top: 4px;
}

.result-tools {
  display: flex;
  align-items: center;
  gap: 6px;
}

.result-box {
  max-height: 46vh;
  overflow-y: auto;
  padding: 6px 4px 10px;
  font-size: 15px;
  line-height: 1.75;
  color: #24303f;
  white-space: pre-wrap;
  word-break: break-word;
}

.result-text {
  white-space: pre-wrap;
}

/* 打字机闪烁光标 */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 16px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #0e9e96;
  animation: ootd-blink 0.9s steps(1) infinite;
}

@keyframes ootd-blink {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}

.bottom-actions {
  display: flex;
  gap: 10px;
}

.bottom-actions .el-button {
  flex: 1;
  height: 44px;
  border-radius: 10px;
}
</style>
