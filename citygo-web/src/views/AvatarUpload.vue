<script setup>
/**
 * 头像上传页（requiresAuth）。
 *
 * 与 OOTD 页的关键区别：头像走 **FormData 文件上传（multipart/form-data）**，
 * 不转 base64；文件字段名 file，与后端 MultipartFile 参数对齐。
 * 请求由 axios 统一实例发出：请求拦截器自动删除 FormData 的 Content-Type，
 * 由浏览器补 boundary，并自动在 header 携带 token。
 *
 * 功能：拍照 / 相册选择 → 本地预览 → 校验（类型 / 大小）→ 上传（带进度条）→
 * 成功后回写 store 头像，可继续更换或返回资料页。
 */
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/** 拍照 / 相册两个隐藏 input（拍照 input 带 capture，手机直接唤起摄像头） */
const cameraInputRef = ref(null)
const albumInputRef = ref(null)

const selectedFile = ref(null)
const previewUrl = ref('')
const uploading = ref(false)
const progress = ref(0)
const uploadedAvatar = ref('')

/** 允许的图片类型 & 大小限制（头像限制 5MB） */
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_SIZE = 5 * 1024 * 1024

/** 选择图片（拍照 / 相册共用） */
function handleFileChange(event) {
  const file = event.target.files?.[0]
  // input.value 清空，确保选择同一张图片时仍能触发 change
  event.target.value = ''
  if (!file) return

  if (!ALLOWED_TYPES.includes(file.type)) {
    ElMessage.error('仅支持 JPG / PNG / WEBP 格式的图片')
    return
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('图片不能超过 5MB')
    return
  }

  selectedFile.value = file
  uploadedAvatar.value = ''
  progress.value = 0

  // 生成本地预览地址（object URL，上传完成或离开页面时释放）
  releasePreview()
  previewUrl.value = URL.createObjectURL(file)
}

/** 执行上传 */
async function handleUpload() {
  if (!selectedFile.value || uploading.value) return

  uploading.value = true
  progress.value = 0
  try {
    const url = await authStore.uploadAvatar(selectedFile.value, (percent) => {
      progress.value = percent
    })
    uploadedAvatar.value = url || previewUrl.value
    ElMessage.success('头像上传成功')
  } catch {
    // 错误文案由响应拦截器统一弹窗（含网络失败 / adb reverse 提醒）
  } finally {
    uploading.value = false
  }
}

/** 重新选择图片：清状态后唤起相册 */
function handleRetake() {
  selectedFile.value = null
  uploadedAvatar.value = ''
  progress.value = 0
  releasePreview()
  previewUrl.value = ''
  albumInputRef.value?.click()
}

/** 返回资料页 */
function goBack() {
  router.push('/profile')
}

function releasePreview() {
  if (previewUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(previewUrl.value)
  }
}

onBeforeUnmount(() => {
  releasePreview()
})
</script>

<template>
  <section class="avatar-page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel__header"><span>① 选择头像图片</span></div>
      </template>

      <!-- 未选择：拍照 / 相册两个入口 -->
      <div v-if="!previewUrl" class="picker">
        <el-button
          type="primary"
          class="picker__btn"
          :disabled="uploading"
          @click="cameraInputRef?.click()"
        >
          拍 照
        </el-button>
        <el-button class="picker__btn" :disabled="uploading" @click="albumInputRef?.click()">
          从相册选择
        </el-button>
        <p class="picker__tip">支持 JPG / PNG / WEBP，图片不超过 5MB</p>
      </div>

      <!-- 已选择：圆形预览 -->
      <div v-else class="preview">
        <div class="preview__avatar" :class="{ 'is-uploaded': !!uploadedAvatar }">
          <el-avatar :size="140" :src="uploadedAvatar || previewUrl" shape="circle" />
        </div>
        <p v-if="selectedFile" class="preview__name">{{ selectedFile.name }}</p>

        <!-- 上传进度条 -->
        <el-progress
          v-if="uploading || progress === 100"
          :percentage="progress"
          :status="progress === 100 ? 'success' : undefined"
          class="preview__progress"
        />

        <div v-if="!uploading" class="preview__actions">
          <el-button @click="handleRetake">{{ uploadedAvatar ? '更换图片' : '重新选择' }}</el-button>
          <el-button
            v-if="!uploadedAvatar"
            type="primary"
            :loading="uploading"
            @click="handleUpload"
          >
            上传头像
          </el-button>
          <el-button v-else type="primary" @click="goBack">返回资料页</el-button>
        </div>
      </div>
    </el-card>

    <el-button class="back-btn" plain :disabled="uploading" @click="goBack">
      暂不上传，返回
    </el-button>

    <p class="page-tip">
      头像通过 FormData 文件上传（非 base64），请求自动携带 token。
      <br />USB 真机调试请先执行：adb reverse tcp:8080 tcp:8080
    </p>

    <!-- 隐藏文件选择框：capture="environment" 在手机上调起后置摄像头 -->
    <input
      ref="cameraInputRef"
      type="file"
      accept="image/*"
      capture="environment"
      class="hidden-input"
      @change="handleFileChange"
    />
    <input
      ref="albumInputRef"
      type="file"
      accept="image/*"
      class="hidden-input"
      @change="handleFileChange"
    />
  </section>
</template>

<style scoped>
.avatar-page {
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

.hidden-input {
  display: none;
}

.picker {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 12px 0 4px;
}

.picker__btn {
  width: 80%;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
}

.picker__tip {
  margin: 0;
  font-size: 12px;
  color: #97a0af;
}

.preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.preview__avatar {
  padding: 6px;
  border-radius: 50%;
}

.preview__avatar.is-uploaded {
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.35);
}

.preview__name {
  margin: 0;
  font-size: 13px;
  color: #97a0af;
  word-break: break-all;
  text-align: center;
}

.preview__progress {
  width: 100%;
  margin-top: 4px;
}

.preview__actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.back-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
}

.page-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: #97a0af;
  text-align: center;
}
</style>
