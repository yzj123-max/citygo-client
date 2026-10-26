<script setup>
/**
 * 个人资料管理页（requiresAuth：未登录由路由守卫跳登录页）。
 *
 * - 进入页面拉取一次最新资料（store 中有缓存则先渲染缓存）；
 * - 可编辑昵称 / 性别 / 个性签名，保存走 PUT 资料接口（请求自动带 token）；
 * - 头像修改跳转到独立的头像上传页（FormData 文件上传）；
 * - 退出登录：清空登录态后回 OOTD 首页；
 * - 风格与 OOTD / 登录页统一：卡片 + 垂直布局。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getToken } from '../utils/storage'
import { BASE_URL } from '../api/request'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref(null)
const loadingProfile = ref(false)
const saving = ref(false)

const form = reactive({
  nickname: '',
  gender: '',
  signature: '',
})

/** 头像可能是相对路径（后端返回 /upload/xxx.jpg），统一拼成完整地址展示 */
function resolveAvatar(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return url
  return BASE_URL + (url.startsWith('/') ? url : `/${url}`)
}

/** 把 store 中的资料同步到表单（缓存即时可见，接口返回后再覆盖） */
function fillForm() {
  const info = authStore.userInfo || {}
  form.nickname = info.nickname || ''
  form.gender = info.gender || ''
  form.signature = info.signature || ''
}

onMounted(async () => {
  fillForm()
  loadingProfile.value = true
  try {
    await authStore.refreshProfile()
    fillForm()
  } catch {
    // 拉取失败的错误文案已由响应拦截器统一弹窗；缓存数据仍可编辑
  } finally {
    loadingProfile.value = false
  }
})

const rules = {
  nickname: [{ max: 12, message: '昵称不能超过 12 个字符', trigger: 'blur' }],
  signature: [{ max: 50, message: '个性签名不能超过 50 个字符', trigger: 'blur' }],
}

/** 保存资料 */
async function handleSave() {
  if (saving.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    const fields = formRef.value?.fields || []
    for (const field of fields) {
      if (field.validateMessage) {
        ElMessage.error(String(field.validateMessage))
        return
      }
    }
    ElMessage.error('请检查资料内容是否正确')
    return
  }

  saving.value = true
  try {
    await authStore.updateProfile({
      nickname: form.nickname.trim(),
      gender: form.gender,
      signature: form.signature.trim(),
    })
    ElMessage.success('资料已保存')
  } catch {
    // 错误文案由响应拦截器统一弹窗
  } finally {
    saving.value = false
  }
}

/** 跳转头像上传页 */
function goAvatarUpload() {
  router.push('/avatar-upload')
}

/** 退出登录：二次确认后清登录态 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
      // 移动端弹窗样式微调
      center: true,
    })
  } catch {
    return // 用户取消
  }
  authStore.resetAuth()
  ElMessage.success('已退出登录')
  router.replace('/ootd')
}
</script>

<template>
  <section class="profile-page" v-loading="loadingProfile">
    <!-- ① 头像区：点击进入头像上传页 -->
    <el-card shadow="never" class="panel" body-class="avatar-card__body" @click="goAvatarUpload">
      <div class="avatar-row">
        <el-avatar :size="64" :src="resolveAvatar(authStore.avatarUrl)">
          {{ authStore.displayName.slice(0, 1) }}
        </el-avatar>
        <div class="avatar-row__meta">
          <p class="avatar-row__name">{{ authStore.displayName }}</p>
          <p class="avatar-row__tip">点击更换头像（支持拍照 / 相册）</p>
        </div>
        <span class="avatar-row__arrow">›</span>
      </div>
    </el-card>

    <!-- ② 资料编辑区 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel__header"><span>基本资料</span></div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="手机号">
          <el-input
            :model-value="authStore.userInfo?.phone || '—'"
            disabled
          />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="请输入昵称（最多 12 字）"
            maxlength="12"
            show-word-limit
            clearable
          />
        </el-form-item>

        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio-button value="male">男</el-radio-button>
            <el-radio-button value="female">女</el-radio-button>
            <el-radio-button value="secret">保密</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="个性签名" prop="signature">
          <el-input
            v-model="form.signature"
            type="textarea"
            :rows="3"
            placeholder="介绍一下自己吧（最多 50 字）"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-button
          type="primary"
          class="submit-btn"
          :loading="saving"
          @click="handleSave"
        >
          {{ saving ? '保存中…' : '保存资料' }}
        </el-button>
      </el-form>
    </el-card>

    <!-- ③ 退出登录 -->
    <el-button class="logout-btn" plain @click="handleLogout">退出登录</el-button>

    <!-- 调试提示：token 存在时展示前 8 位，方便毕设演示核对请求头 -->
    <p class="debug-tip">
      当前 token：{{ getToken() ? getToken().slice(0, 8) + '…（请求自动携带）' : '无' }}
      <br />USB 真机调试请先执行：adb reverse tcp:8080 tcp:8080
    </p>
  </section>
</template>

<style scoped>
.profile-page {
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

/* 头像卡片整体可点 */
:deep(.avatar-card__body) {
  cursor: pointer;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-row__meta {
  flex: 1;
  min-width: 0;
}

.avatar-row__name {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #24303f;
}

.avatar-row__tip {
  margin: 0;
  font-size: 13px;
  color: #97a0af;
}

.avatar-row__arrow {
  font-size: 22px;
  color: #c3cad6;
  line-height: 1;
}

.submit-btn {
  width: 100%;
  height: 48px;
  margin-top: 4px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 12px;
}

.logout-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-size: 15px;
}

.debug-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #97a0af;
  text-align: center;
  word-break: break-all;
}
</style>
