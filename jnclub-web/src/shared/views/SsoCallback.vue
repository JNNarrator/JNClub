<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NSpin, NText } from 'naive-ui'

const router = useRouter()
const route = useRoute()

onMounted(async () => {
  const token = route.query.token as string
  const error = route.query.error as string
  
  if (error) {
    console.error('SSO 登录失败:', error)
    // 遇到错误时重定向到登录页，避免卡住
    setTimeout(() => {
      window.location.href = '/sso/login'
    }, 1000)
    return
  }
  
  if (token) {
    localStorage.setItem('jn-token', token)
    router.push('/')
  } else {
    window.location.href = '/sso/login'
  }
})
</script>

<template>
  <div class="sso-callback">
    <NSpin size="large" />
    <NText>正在处理登录...</NText>
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
</style>
