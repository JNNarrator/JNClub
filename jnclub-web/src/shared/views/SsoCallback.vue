<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NSpin, NText, NButton, NResult } from 'naive-ui'

const router = useRouter()
const route = useRoute()

const errorMsg = ref('')
const isLoading = ref(true)

onMounted(async () => {
  const token = route.query.token as string
  const error = route.query.error as string

  if (error) {
    isLoading.value = false
    switch (error) {
      case 'sso_error':
        errorMsg.value = 'SSO 服务暂时不可用，请稍后重试'
        break
      case 'sso_failed':
        errorMsg.value = 'SSO 登录验证失败，请重新登录'
        break
      default:
        errorMsg.value = `登录失败: ${error}`
    }
    return
  }

  if (token) {
    localStorage.setItem('jn-token', token)
    await router.push('/')
  } else {
    isLoading.value = false
    errorMsg.value = '缺少登录凭证'
  }
})

const handleRetry = () => {
  window.location.href = '/sso/login'
}
</script>

<template>
  <div class="sso-callback">
    <div v-if="isLoading" class="loading-state">
      <NSpin size="large" />
      <NText depth="3">正在处理登录...</NText>
    </div>
    <div v-else class="error-state">
      <NResult
        status="error"
        title="登录失败"
        :description="errorMsg"
      >
        <template #footer>
          <NButton type="primary" @click="handleRetry">重新登录</NButton>
        </template>
      </NResult>
    </div>
  </div>
</template>

<style scoped>
.sso-callback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
</style>
