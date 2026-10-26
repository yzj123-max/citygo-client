<script setup>
/**
 * 注册页（移动端垂直布局，风格与登录页统一）。
 *
 * - 字段：手机号 / 密码 / 确认密码 / 昵称（选填）；
 * - el-form 基础校验 + 自定义二次密码一致性校验，不通过弹窗提示第一条错误；
 * - 注册成功后跳转登录页（携带 redirect），由用户主动登录；
 * - 本页为访客页（meta.guestOnly），已登录访问会被路由守卫带回首页。
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
  confirmPassword: '',
  nickname: '',
})

const PHONE_REG = /^1[3-9]\d{9}$/

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_REG, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      // 自定义校验：两次密码必须一致
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  nickname: [
    { max: 12, message: '昵称不能超过 12 个字符', trigger: 'blur' },
  ],
}

/** 提交注册 */
async function handleRegister() {
  if (loading.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.error(pickFirstErrorMessage() || '请检查注册信息是否正确')
    return
  }

  loading.value = true
  try {
    await authStore.register({
      phone: form.phone.trim(),
      password: form.password,
      // 昵称为空则不传，避免后端收到空字符串
      nickname: form.nickname.trim() || undefined,
    })
    ElMessage.success('注册成功，请登录')
    const query = route.query.redirect ? { redirect: route.query.redirect } : undefined
    router.replace({ path: '/login', query })
  } catch {
    // 错误文案由响应拦截器统一弹窗
  } finally {
    loading.value = false
  }
}

/** 返回登录页（保留 redirect） */
function goLogin() {
  const query = route.query.redirect ? { redirect: route.query.redirect } : undefined
  router.replace({ path: '/login', query })
}

/** 取第一个表单项校验错误文案，用于弹窗提示 */
function pickFirstErrorMessage() {
  const fields = formRef.value?.fields || []
  for (const field of fields) {
    if (field.validateMessage) return String(field.validateMessage)
  }
  return ''
}
</script>

<template>
  <section class="auth-page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel__header"><span>注册新账号</span></div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
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
            placeholder="请设置 6-20 位密码"
            maxlength="20"
            show-password
            clearable
            :disabled="loading"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            maxlength="20"
            show-password
            clearable
            :disabled="loading"
          />
        </el-form-item>

        <el-form-item label="昵称（选填）" prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="给自己起个昵称"
            maxlength="12"
            clearable
            :disabled="loading"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          @click="handleRegister"
        >
          {{ loading ? '注册中…' : '注 册' }}
        </el-button>
      </el-form>

      <div class="auth-links">
        <span>已有账号？</span>
        <el-button link type="primary" :disabled="loading" @click="goLogin">
          返回登录
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
