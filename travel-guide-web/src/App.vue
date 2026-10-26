<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { generateTask, getTaskResult } from './api/travel'

/* ============================ 表单数据 ============================ */

// 偏好可选项（多选）
const PREFERENCE_OPTIONS = ['美食', '拍照', '购物', '自然风光']

// 表单模型：字段名与后端 JSON 契约严格对应
const form = reactive({
  city: '', // 目的地城市
  dayNum: 3, // 游玩天数
  budget: '' as number | '', // 预算（输入框空串表示未填写，提交时转 null）
  preferenceList: ['美食'] as string[], // 游玩偏好多选（数组）
  petFriendlyFlag: false, // 是否宠物友好
  remark: '', // 备注
})

/* ============================ 页面状态 ============================ */

const loading = ref(false) // 是否处于「提交中 / 轮询中」，控制按钮禁用
const pollingText = ref('') // 轮询过程中的友好提示文案
const guideContent = ref('') // 生成成功后的攻略文本（含 \n）
const errorMsg = ref('') // 提交 / 查询阶段的错误提示（页面内红字）

// 轮询定时器与「请求在途」标记（防止上一次查询未返回就发下一次）
let pollTimer: ReturnType<typeof setInterval> | null = null
let pollInFlight = false

// 轮询间隔：严格按需求每 2000ms 一次
const POLL_INTERVAL = 2000

/** 清理轮询定时器 */
function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollInFlight = false
}

/** 切换某个偏好的勾选状态 */
function togglePreference(item: string, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    if (!form.preferenceList.includes(item)) form.preferenceList.push(item)
  } else {
    form.preferenceList = form.preferenceList.filter((p) => p !== item)
  }
}

/** 前端基础校验，返回错误信息；通过返回空串 */
function validate(): string {
  if (!form.city.trim()) return '请输入目的地城市'
  if (!Number.isInteger(form.dayNum) || form.dayNum <= 0) return '请输入正确的游玩天数（正整数）'
  if (form.budget !== '' && (!(Number(form.budget) > 0))) return '预算需为大于 0 的数字，或留空表示不限'
  return ''
}

/* ============================ 提交 + 轮询主流程 ============================ */

async function handleGenerate() {
  // 二次防护：loading 期间直接忽略点击（按钮本身也已 disabled）
  if (loading.value) return

  const validateError = validate()
  if (validateError) {
    alert(validateError)
    return
  }

  // 重置页面状态
  errorMsg.value = ''
  guideContent.value = ''
  loading.value = true
  pollingText.value = '正在提交任务…'

  try {
    // —— 步骤1：提交任务，字段名严格匹配后端契约 ——
    const submitResp = await generateTask({
      city: form.city.trim(),
      dayNum: form.dayNum,
      budget: form.budget === '' ? null : Number(form.budget), // 空串 → null（不限预算）
      preferenceList: [...form.preferenceList], // 必须是数组
      petFriendlyFlag: form.petFriendlyFlag,
      remark: form.remark.trim(),
    })

    if (!submitResp.success || !submitResp.taskId) {
      // 提交被后端拒绝：停止加载并弹窗展示后端 msg
      throw new Error(submitResp.msg || '任务提交失败')
    }

    // —— 步骤2：拿到 taskId，自动开始每 2s 轮询 ——
    pollingText.value = submitResp.msg || '任务已提交，后台正在生成攻略…'
    startPolling(submitResp.taskId)
  } catch (e) {
    // 提交阶段网络异常 / 后端返回失败：弹窗报错并恢复按钮
    loading.value = false
    pollingText.value = ''
    errorMsg.value = (e as Error).message || '提交任务失败，请检查网络后重试'
    alert(errorMsg.value)
  }
}

/** 启动定时轮询：SUCCESS 停止并展示；success=false 停止并弹窗报错 */
function startPolling(taskId: string) {
  stopPolling() // 兜底：防止残留定时器造成重复轮询

  pollTimer = setInterval(async () => {
    // 上一次查询还没回来时跳过本轮，避免请求堆积
    if (pollInFlight) return
    pollInFlight = true

    try {
      // 查询接口：路径变量 /travel/task/{taskId}
      const result = await getTaskResult(taskId)

      // 情况3：success 为 false（如任务不存在）→ 停止轮询并弹窗报错
      if (!result.success) {
        stopPolling()
        loading.value = false
        pollingText.value = ''
        errorMsg.value = result.msg || '攻略生成失败'
        alert(errorMsg.value)
        return
      }

      // 情况2：成功拿到攻略 → 停止轮询，展示文本
      if (result.status === 'SUCCESS') {
        stopPolling()
        loading.value = false
        pollingText.value = ''
        guideContent.value = result.data ?? ''
        return
      }

      // 情况1：PROCESSING 处理中 → 更新提示，继续下一次轮询
      if (result.status === 'PROCESSING') {
        pollingText.value = result.msg || '正在生成，请稍后查询…'
      }
    } catch (e) {
      // 查询过程中出现网络异常：停止轮询、弹窗报错、恢复按钮可重新提交
      stopPolling()
      loading.value = false
      pollingText.value = ''
      errorMsg.value = `查询任务状态失败：${(e as Error).message || '网络异常'}`
      alert(errorMsg.value)
    } finally {
      pollInFlight = false
    }
  }, POLL_INTERVAL)
}

// 组件卸载前必须清理定时器，避免内存泄漏与无效请求
onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="page">
    <div class="card">
      <h1 class="title">AI 旅游攻略生成</h1>
      <p class="subtitle">填写出行需求，AI 异步生成专属攻略</p>

      <form class="form" @submit.prevent>
        <!-- 目的地城市 -->
        <div class="field">
          <label class="label">目的地城市</label>
          <input
            v-model="form.city"
            class="input"
            type="text"
            placeholder="例如：长沙"
            :disabled="loading"
          />
        </div>

        <!-- 游玩天数 + 预算 同一行 -->
        <div class="field-row">
          <div class="field">
            <label class="label">游玩天数</label>
            <input
              v-model.number="form.dayNum"
              class="input"
              type="number"
              min="1"
              step="1"
              :disabled="loading"
            />
          </div>
          <div class="field">
            <label class="label">预算（元，选填）</label>
            <input
              v-model.number="form.budget"
              class="input"
              type="number"
              min="1"
              placeholder="不限可留空"
              :disabled="loading"
            />
          </div>
        </div>

        <!-- 游玩偏好（多选框） -->
        <div class="field">
          <label class="label">游玩偏好（可多选）</label>
          <div class="checkbox-group">
            <label
              v-for="item in PREFERENCE_OPTIONS"
              :key="item"
              class="checkbox-chip"
              :class="{ active: form.preferenceList.includes(item) }"
            >
              <input
                type="checkbox"
                class="checkbox-input"
                :checked="form.preferenceList.includes(item)"
                :disabled="loading"
                @change="togglePreference(item, $event)"
              />
              {{ item }}
            </label>
          </div>
        </div>

        <!-- 是否宠物友好 -->
        <div class="field">
          <label class="checkbox-line">
            <input
              v-model="form.petFriendlyFlag"
              type="checkbox"
              :disabled="loading"
            />
            <span>是否宠物友好（优先安排可携带宠物的地点）</span>
          </label>
        </div>

        <!-- 备注 -->
        <div class="field">
          <label class="label">备注</label>
          <textarea
            v-model="form.remark"
            class="textarea"
            rows="3"
            placeholder="例如：休闲慢节奏、不吃辣"
            :disabled="loading"
          ></textarea>
        </div>

        <!-- 提交按钮：提交后置灰禁用，禁止重复提交 -->
        <button
          class="btn-primary"
          type="button"
          :disabled="loading"
          @click="handleGenerate"
        >
          {{ loading ? '正在生成，请稍候…' : '生成攻略' }}
        </button>

        <!-- 加载 / 轮询提示 -->
        <div v-if="loading" class="loading-tip">
          <span class="loading-dot"></span>
          <span>{{ pollingText || '正在生成，请稍候…' }}</span>
        </div>
      </form>

      <!-- 攻略结果：white-space: pre-wrap 使后端文本中的 \n 正常换行显示 -->
      <div v-if="guideContent" class="result">
        <div class="result-title">生成的攻略</div>
        <div class="result-content">{{ guideContent }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 页面居中布局 */
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 16px 48px;
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 28px 32px;
  box-shadow: 0 2px 12px rgba(31, 35, 41, 0.06);
}

.title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2329;
}

.subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #8a8f99;
}

.form {
  margin-top: 20px;
}

.field {
  margin-bottom: 16px;
}

/* 天数与预算并排 */
.field-row {
  display: flex;
  gap: 16px;
}

.field-row .field {
  flex: 1;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #33383f;
}

.input,
.textarea {
  width: 100%;
  padding: 9px 12px;
  font-size: 14px;
  color: #1f2329;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  outline: none;
  background: #fff;
  transition: border-color 0.15s ease;
}

.input:focus,
.textarea:focus {
  border-color: #2f6bff;
}

.input:disabled,
.textarea:disabled {
  background: #f5f6f8;
  color: #8a8f99;
}

.textarea {
  resize: vertical;
  font-family: inherit;
}

/* 多选偏好：复选框 + 胶囊样式，简洁不花哨 */
.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.checkbox-chip {
  position: relative;
  padding: 7px 14px;
  font-size: 13px;
  color: #4e5969;
  background: #f4f5f7;
  border: 1px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  user-select: none;
}

.checkbox-chip.active {
  color: #2f6bff;
  background: #eef3ff;
  border-color: #2f6bff;
}

/* 隐藏原生 checkbox，仅保留勾选语义（无障碍/键盘仍可用） */
.checkbox-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

/* 宠物友好单行复选框 */
.checkbox-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #33383f;
  cursor: pointer;
}

/* 加载提示 */
.loading-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  font-size: 14px;
  color: #2f6bff;
}

/* 简单的呼吸圆点提示（非花哨动画，仅表示进行中） */
.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2f6bff;
  animation: blink 1s ease-in-out infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.25;
  }
  50% {
    opacity: 1;
  }
}

/* 结果区 */
.result {
  margin-top: 24px;
  border: 1px solid #e5e8ef;
  border-radius: 10px;
  overflow: hidden;
}

.result-title {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
  background: #f7f9fc;
  border-bottom: 1px solid #e5e8ef;
}

/* 关键：保留后端攻略文本中的换行符 \n 与空格，实现自动换行 */
.result-content {
  padding: 14px;
  font-size: 14px;
  color: #33383f;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
