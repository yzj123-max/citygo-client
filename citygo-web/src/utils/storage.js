/**
 * 本地持久化工具（localStorage 轻封装）。
 *
 * 说明：毕设项目先以 localStorage 保存登录态 / 用户信息，刷新与重开浏览器不丢失；
 * 所有读写统一收口在本文件，后续若要换成 Cookie / 加密存储，只改这里即可。
 */

const TOKEN_KEY = 'citygo_token'
const USER_KEY = 'citygo_user_info'

/** 读取登录 token；未登录返回空串 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/** 保存 token（登录 / 注册成功后调用） */
export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
}

/** 清除 token（退出登录 / 401 过期时调用） */
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 读取缓存的用户信息；解析失败返回 null
 * @returns {null | {id?:number|string, phone?:string, nickname?:string, avatar?:string, signature?:string, gender?:string}}
 */
export function getUserInfo() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/** 保存用户信息（自动合并旧字段，避免部分更新时丢字段） */
export function setUserInfo(info) {
  if (!info || typeof info !== 'object') return
  const merged = { ...(getUserInfo() || {}), ...info }
  localStorage.setItem(USER_KEY, JSON.stringify(merged))
}

/** 清除用户信息 */
export function clearUserInfo() {
  localStorage.removeItem(USER_KEY)
}
