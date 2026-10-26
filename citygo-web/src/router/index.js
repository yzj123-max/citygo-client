/**
 * vue-router 路由表与全局守卫。
 *
 * 已实现页面：
 * - /ootd           OOTD 穿搭 AI 分析页（拍照/相册 → POST /api/ai/ootd-stream 流式分析）
 * - /stylist        景点穿搭 Stylist AI 对话页（拍照/相册 → POST /api/ai/stylist-stream 流式推荐）
 * - /login          登录页（访客页，已登录访问会跳走）
 * - /register       注册页（访客页）
 * - /profile        个人资料管理页（requiresAuth：未登录跳登录页并带 redirect）
 * - /avatar-upload  头像上传页（requiresAuth）
 */
import { createRouter, createWebHistory } from 'vue-router'

import OotdAnalyze from '../views/OotdAnalyze.vue'
import Stylist from '../views/Stylist.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Profile from '../views/Profile.vue'
import AvatarUpload from '../views/AvatarUpload.vue'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/ootd' },

  {
    path: '/ootd',
    name: 'ootd',
    component: OotdAnalyze,
    meta: { title: 'OOTD 穿搭 AI 分析' },
  },

  {
    path: '/login',
    name: 'login',
    component: Login,
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: Register,
    meta: { title: '注册', guestOnly: true },
  },

  // —— 以下页面需要登录后才能访问 ——
  {
    path: '/profile',
    name: 'profile',
    component: Profile,
    meta: { title: '个人资料', requiresAuth: true },
  },
  {
    path: '/avatar-upload',
    name: 'avatar-upload',
    component: AvatarUpload,
    meta: { title: '头像上传', requiresAuth: true },
  },

  // 景点穿搭 Stylist AI 对话页（公开页，与 OOTD 一样无需登录即可使用）
  {
    path: '/stylist',
    name: 'stylist',
    component: Stylist,
    meta: { title: '景点穿搭 AI 对话' },
  },

  // 未匹配路由统一回到 OOTD 首页
  { path: '/:pathMatch(.*)*', redirect: '/ootd' },
]

const router = createRouter({
  // history 模式；如部署到无后端 fallback 的静态服务器，可改成 createWebHashHistory
  history: createWebHistory(),
  routes,
  // 切换页面后回到顶部，符合移动端习惯
  scrollBehavior() {
    return { top: 0 }
  },
})

/**
 * 全局守卫（集中在 beforeEach，用 meta 表达，页面内不写鉴权跳转）：
 * - meta.requiresAuth=true：必须登录，否则跳 /login?redirect=原路径；
 * - meta.guestOnly=true：仅未登录可访问（登录 / 注册页），已登录则回首页；
 * - redirect 只由登录页在登录成功后消费一次。
 */
router.beforeEach((to) => {
  const authStore = useAuthStore()

  // 受保护页：未登录 → 登录页，并携带登录后要回跳的地址
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  // 访客页（登录 / 注册）：已登录就不再展示，直接回首页
  if (to.meta.guestOnly && authStore.isLoggedIn) {
    return { path: '/ootd' }
  }

  return true
})

// 页面标题跟随路由
router.afterEach((to) => {
  const title = to.meta?.title
  if (title) document.title = `CityGo · ${title}`
})

export default router
