/**
 * 登录态 / 用户信息 Pinia store（setup 写法）。
 *
 * - token 与 userInfo 同时保存在内存（ref）与 localStorage（刷新不丢）；
 * - 受保护接口由 axios 请求拦截器自动带 token，页面无需手动处理；
 * - 401 时 http 拦截器会清空本地数据并整页跳登录页，与本 store 的 resetAuth 保持一致。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getProfileApi,
  loginApi,
  registerApi,
  updateProfileApi,
  uploadAvatarApi,
} from '../api/auth'
import {
  clearToken,
  clearUserInfo,
  getToken,
  getUserInfo,
  setToken,
  setUserInfo,
} from '../utils/storage'

export const useAuthStore = defineStore('auth', () => {
  /* ------------------------------ state ------------------------------ */
  const token = ref(getToken())
  const userInfo = ref(getUserInfo())

  /* ------------------------------ getters ------------------------------ */
  /** 是否已登录（路由守卫直接读布尔值，避免 ref 取 .value 的语义错误） */
  const isLoggedIn = computed(() => !!token.value)
  /** 展示用昵称：昵称 → 手机尾号 → 游客 */
  const displayName = computed(
    () =>
      userInfo.value?.nickname ||
      (userInfo.value?.phone ? `用户${String(userInfo.value.phone).slice(-4)}` : '游客'),
  )
  const avatarUrl = computed(() => userInfo.value?.avatar || '')

  /* ------------------------------ actions ------------------------------ */

  /**
   * 兼容解析登录 / 注册接口返回：
   * { token } / { token, userInfo } / { token, phone, nickname, ... } / { data: {...} } 由拦截器已剥一层。
   */
  function applyLoginResult(result) {
    const data = result && typeof result === 'object' ? result : {}
    const newToken = data.token || data.accessToken || data.jwt || ''
    const info = data.userInfo || data.user || extractUserFields(data)
    if (newToken) {
      token.value = newToken
      setToken(newToken)
    }
    if (info && Object.keys(info).length) {
      userInfo.value = info
      setUserInfo(info)
    }
    return newToken
  }

  /** 从平铺对象中提取常见用户字段（后端把用户信息直接平铺在登录响应里时使用） */
  function extractUserFields(data) {
    const fields = ['id', 'phone', 'nickname', 'avatar', 'gender', 'signature', 'email']
    const picked = {}
    fields.forEach((key) => {
      if (data[key] !== undefined && data[key] !== null) picked[key] = data[key]
    })
    return picked
  }

  /** 密码登录；成功后返回 true，失败抛出由页面 / 拦截器提示 */
  async function login(payload) {
    const result = await loginApi(payload)
    const newToken = applyLoginResult(result)
    // 登录后顺手拉取最新资料（静默：资料接口暂时不可用也不阻断登录成功流程）
    if (newToken) await refreshProfile({ silent: true })
    return true
  }

  /** 注册：注册成功后默认不自动登录，页面引导去登录；需要自动登录可改为调用 login */
  async function register(payload) {
    return registerApi(payload)
  }

  /** 拉取最新个人资料并合并入库；options 透传 axios 配置（如 { silent: true }） */
  async function refreshProfile(options) {
    const data = await getProfileApi(options)
    if (data && typeof data === 'object') {
      // 与缓存合并，后端只返回部分字段时不会把其它字段清空
      userInfo.value = { ...(userInfo.value || {}), ...data }
      setUserInfo(userInfo.value)
    }
    return data
  }

  /** 更新资料 */
  async function updateProfile(payload) {
    const data = await updateProfileApi(payload)
    // 后端可能返回更新后的完整资料，也可能只返回成功标记，做一次合并兜底
    if (data && typeof data === 'object') {
      const merged = { ...(userInfo.value || {}), ...data }
      userInfo.value = merged
      setUserInfo(merged)
    } else {
      const merged = { ...(userInfo.value || {}), ...payload }
      userInfo.value = merged
      setUserInfo(merged)
    }
  }

  /**
   * 头像 FormData 上传：成功后把新头像地址写入用户信息。
   * @param {File} file
   * @param {(percent:number)=>void} [onProgress]
   */
  async function uploadAvatar(file, onProgress) {
    const data = await uploadAvatarApi(file, onProgress)
    const url =
      (data && (data.avatar || data.url || data.avatarUrl)) ||
      (typeof data === 'string' ? data : '')
    if (url) {
      const merged = { ...(userInfo.value || {}), avatar: url }
      userInfo.value = merged
      setUserInfo(merged)
    }
    return url
  }

  /** 退出登录 / 401：清内存与本地，不主动跳转（由调用方或拦截器决定去向） */
  function resetAuth() {
    token.value = ''
    userInfo.value = null
    clearToken()
    clearUserInfo()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    displayName,
    avatarUrl,
    login,
    register,
    refreshProfile,
    updateProfile,
    uploadAvatar,
    resetAuth,
  }
})
