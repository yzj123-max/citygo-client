/**
 * 认证与个人资料相关接口。
 *
 * 所有路径统一经 axios 实例 http 发出，自动拼接 http://localhost:8080 前缀、
 * 自动携带 token 并走统一响应拦截（见 ./http）。
 *
 * ⚠️ 接口路径常量集中放在文件顶部：若后端实际路径不同（例如 /userLogin、/userRegister），
 * 只改这里的常量即可，页面与 store 无需改动。
 */
import http from './http'

/** —— 接口路径（以后端真实定义为准，统一在此维护） —— */
export const API_PATHS = {
  // 密码登录：POST JSON { phone, password }
  login: '/api/auth/login',
  // 注册：POST JSON { phone, password, nickname? }
  register: '/api/auth/register',
  // 获取当前登录用户资料：GET（自动带 token）
  profile: '/api/user/profile',
  // 更新资料：PUT JSON
  updateProfile: '/api/user/profile',
  // 头像上传：POST multipart/form-data，文件字段名固定 file
  avatar: '/api/user/avatar',
}

/**
 * 密码登录
 * @param {{phone:string, password:string}} payload
 * @returns {Promise<{token?:string, userInfo?:object}>} 拦截器已解包 data
 */
export function loginApi(payload) {
  return http.post(API_PATHS.login, payload)
}

/**
 * 注册
 * @param {{phone:string, password:string, nickname?:string}} payload
 */
export function registerApi(payload) {
  return http.post(API_PATHS.register, payload)
}

/** 获取当前登录用户资料；可传 axios 配置（如 { silent: true } 关闭错误弹窗） */
export function getProfileApi(config) {
  return http.get(API_PATHS.profile, config)
}

/**
 * 更新个人资料
 * @param {{nickname?:string, gender?:string, signature?:string}} payload
 */
export function updateProfileApi(payload) {
  return http.put(API_PATHS.updateProfile, payload)
}

/**
 * 头像上传（FormData 文件上传，不是 base64）。
 * 注意：不要手动设置 Content-Type，拦截器会删除它，由浏览器自动生成
 * multipart/form-data; boundary=...，后端才能正确解析文件。
 * @param {File} file
 * @param {(percent:number)=>void} [onProgress] 上传进度回调（0-100）
 */
export function uploadAvatarApi(file, onProgress) {
  const formData = new FormData()
  // 文件字段名固定 file（与 SpringBoot 后端 MultipartFile 参数名对齐）
  formData.append('file', file)

  return http.post(API_PATHS.avatar, formData, {
    headers: {
      // 仅本接口为 multipart，显式不写 Content-Type，交给浏览器带 boundary
      'Content-Type': undefined,
    },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    },
  })
}
