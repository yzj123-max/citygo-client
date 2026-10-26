<script setup>
// 预留路由统一占位页：后续开发登录 / 注册 / 个人资料 / 头像上传 / 景点穿搭 Stylist 时，
// 直接在 router/index.js 中把对应 component 替换成真实页面即可。
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta?.title || '页面')
const placeholderTitle = computed(() => `「${title.value}」页面预留中`)
</script>

<template>
  <div class="placeholder">
    <el-result
      icon="info"
      :title="placeholderTitle"
      sub-title="该页面将在后续迭代中接入（登录 / 注册 / 个人资料 / 头像上传 / 景点穿搭 AI 对话）"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/ootd')">返回 OOTD 分析</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.placeholder {
  padding-top: 24px;
}
</style>
