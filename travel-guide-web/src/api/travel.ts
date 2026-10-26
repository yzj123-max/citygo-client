/**
 * 旅游攻略异步任务接口封装
 *
 * 注意：所有请求都使用相对路径 /travel/...，开发环境由 vite.config.ts
 * 中的代理转发到 http://localhost:8080，从而解决浏览器跨域问题。
 */

/** 提交任务请求体（字段名与后端严格一致，勿随意改名） */
export interface GenerateTaskRequest {
  city: string // 目的地城市
  dayNum: number // 游玩天数
  budget: number | null // 人均预算（选填，null 代表不限预算）
  preferenceList: string[] // 游玩偏好（数组，不是逗号分隔字符串）
  petFriendlyFlag: boolean // 是否宠物友好
  remark: string // 备注
}

/** 接口1：提交任务返回结构 */
export interface GenerateTaskResponse {
  success: boolean
  taskId?: string
  msg?: string
}

/** 任务处理状态 */
export type TaskStatus = 'PROCESSING' | 'SUCCESS'

/** 接口2：轮询查询返回结构（data 仅 SUCCESS 时存在，为带 \n 的攻略文本） */
export interface TaskResultResponse {
  success: boolean
  status?: TaskStatus
  data?: string
  msg?: string
}

/**
 * 接口1：提交攻略生成任务
 * POST /travel/generateTask，JSON 请求体
 */
export async function generateTask(
  body: GenerateTaskRequest,
): Promise<GenerateTaskResponse> {
  const resp = await fetch('/travel/generateTask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!resp.ok) {
    throw new Error(`提交任务失败（HTTP ${resp.status}）`)
  }
  return (await resp.json()) as GenerateTaskResponse
}

/**
 * 接口2：按任务 ID 轮询查询结果
 * GET /travel/task/{taskId}
 *
 * 注意：taskId 是【路径变量】，必须拼在 URL 路径中，
 * 不能写成 /travel/queryTask?taskId=xxx 之类的 query 参数形式。
 */
export async function getTaskResult(taskId: string): Promise<TaskResultResponse> {
  const resp = await fetch(`/travel/task/${encodeURIComponent(taskId)}`, {
    method: 'GET',
  })
  if (!resp.ok) {
    throw new Error(`查询任务失败（HTTP ${resp.status}）`)
  }
  return (await resp.json()) as TaskResultResponse
}
