<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NConfigProvider, darkTheme, NMessageProvider, NDialogProvider } from 'naive-ui'
import lightThemeOverrides from './themes/light'
import darkThemeOverrides from './themes/dark'

const isDark = ref(false)

// 跟随系统主题
onMounted(() => {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  isDark.value = mediaQuery.matches
  
  // 从 localStorage 读取用户偏好
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme) {
    isDark.value = savedTheme === 'dark'
  }
  
  // 监听系统主题变化
  mediaQuery.addEventListener('change', (e) => {
    if (!localStorage.getItem('theme')) {
      isDark.value = e.matches
    }
  })
})

const toggleTheme = () => {
  isDark.value = !isDark.value
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
}
</script>

<template>
  <NConfigProvider
    :theme="isDark ? darkTheme : null"
    :theme-overrides="isDark ? darkThemeOverrides : lightThemeOverrides"
  >
    <NMessageProvider>
      <NDialogProvider>
        <router-view :is-dark="isDark" @toggle-theme="toggleTheme" />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
</style>
