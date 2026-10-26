<script setup>
/**
 * 登录页（移动端垂直布局，风格与 OOTD 页统一）。
 *
 * - el-form 基础校验：手机号格式、密码长度；校验不通过用 ElMessage 弹窗提示第一条错误；
 * - 登录成功后仅由本页消费一次路由 query.redirect（没有则回 OOTD 首页）；
 * - 本页为访客页（meta.guestOnly），已登录访问会被路由守卫自动带回首页；
 * - token 写入与后续请求自动携带由 Pinia store + axios 请求拦截器负责。
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  phone: '',
  password: '',
})

/** 手机号校验正则（大陆 11 位手机号） */
const PHONE_REG = /^1[3-9]\d{9}$/

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_REG, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' },
  ],
}

/** 提交登录 */
async function handleLogin() {
  if (loading.value) return

  // 表单校验不通过：弹窗提示第一条错误（同时表单项下方也会红字标注）
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    const firstError = pickFirstErrorMessage()
    ElMessage.error(firstError || '请检查输入内容是否正确')
    return
  }

  loading.value = true
  try {
    await authStore.login({ phone: form.phone.trim(), password: form.password })
    ElMessage.success('登录成功')
    // 消费守卫带来的 redirect；防止开放重定向，只允许站内路径
    const redirect = normalizeRedirect(route.query.redirect)
    router.replace(redirect)
  } catch {
    // 具体错误文案已由响应拦截器统一弹窗，这里无需重复提示
  } finally {
    loading.value = false
  }
}

/** 跳转注册页（保留登录后回跳地址） */
function goRegister() {
  const redirect = normalizeRedirect(route.query.redirect)
  router.replace({
    path: '/register',
    query: redirect !== '/ootd' ? { redirect: route.query.redirect } : undefined,
  })
}

/**
 * 从 validate 失败的表单中取第一条错误信息
 * （el-form validate 失败时 reject 一个字段错误对象）
 */
function pickFirstErrorMessage() {
  const fields = formRef.value?.fields || []
  for (const field of fields) {
    const message = field.validateMessage
    if (message) return String(message)
  }
  return ''
}

/** 仅允许站内相对路径，忽略 // 开头的协议相对 URL，防止开放重定向 */
function normalizeRedirect(raw) {
  if (typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//')) {
    return raw
  }
  return '/ootd'
}
</script>

<template>
  <section class="auth-page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel__header"><span>账号登录</span></div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入手机号"
            maxlength="11"
            clearable
            :disabled="loading"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码（6-20 位）"
            maxlength="20"
            show-password
            clearable
            :disabled="loading"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          @click="handleLogin"
        >
          {{ loading ? '登录中…' : '登 录' }}
        </el-button>
      </el-form>

      <div class="auth-links">
        <span>还没有账号？</span>
        <el-button link type="primary" :disabled="loading" @click="goRegister">
          立即注册
        </el-button>
      </div>
    </el-card>

    <p class="auth-tip">USB 真机调试请先在电脑执行：adb reverse tcp:8080 tcp:8080</p>
  </section>
</template>

<style scoped>
.auth-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 8px;
}

.panel {
  border-radius: 14px;
  border: 1px solid #eef1f5;
}

.panel__header {
  font-size: 15px;
  font-weight: 600;
  color: #24303f;
}

.submit-btn {
  width: 100%;
  height: 48px;
  margin-top: 4px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
}

.auth-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  margin-top: 12px;
  font-size: 14px;
  color: #97a0af;
}

.auth-tip {
  margin: 0;
  font-size: 12px;
  color: #97a0af;
  text-align: center;
}
</style>
