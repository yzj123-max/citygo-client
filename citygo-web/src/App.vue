<script setup>
// 根组件：移动端外壳（顶部标题栏 + 个人中心入口 + 路由出口），页面主体在 router-view 中切换
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { BASE_URL } from './api/request'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const pageTitle = computed(() => route.meta?.title || 'CityGo')

/** 头像可能是后端返回的相对路径，拼上统一前缀 */
const headerAvatar = computed(() => {
  const url = authStore.avatarUrl
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return url
  return BASE_URL + (url.startsWith('/') ? url : `/${url}`)
})

/** 点击右上角：已登录进资料页；未登录由路由守卫自动带去登录页 */
function goAccount() {
  router.push(authStore.isLoggedIn ? '/profile' : '/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <span class="app-header__title">{{ pageTitle }}</span>
      <!-- 个人中心入口（右上角小头像 / 「我的」） -->
      <button
        type="button"
        class="app-header__account"
        :title="authStore.isLoggedIn ? '个人资料' : '登录 / 注册'"
        @click="goAccount"
      >
        <el-avatar v-if="headerAvatar" :size="28" :src="headerAvatar" />
        <span v-else>{{ authStore.isLoggedIn ? authStore.displayName.slice(0, 1) : '我的' }}</span>
      </button>
    </header>

    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b7f7a, #0e9e96);
  color: #fff;
  box-shadow: 0 2px 8px rgba(11, 127, 122, 0.25);
}

.app-header__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* 右上角个人中心入口：绝对定位不挤占居中标题 */
.app-header__account {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: 12px;
  line-height: 28px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.app-header__account:active {
  background: rgba(255, 255, 255, 0.35);
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 640px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 12px 14px calc(12px + env(safe-area-inset-bottom));
}
</style>
