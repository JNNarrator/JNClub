<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { NConfigProvider, darkTheme, NMessageProvider, NDialogProvider } from 'naive-ui'
import { tokensToCSSVars, lightTokens, darkTokens } from './themes/tokens'
import lightThemeOverrides from './themes/light'
import darkThemeOverrides from './themes/dark'

const isDark = ref(false)

/** 将 token 注入为 CSS 变量到 :root */
function applyTokens(isDark: boolean) {
  const tokens = isDark ? darkTokens : lightTokens
  const vars = tokensToCSSVars(tokens)
  const root = document.documentElement
  Object.entries(vars).forEach(([key, val]) => {
    root.style.setProperty(key, val)
  })
}

onMounted(() => {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  isDark.value = mediaQuery.matches
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme) {
    isDark.value = savedTheme === 'dark'
  }
  applyTokens(isDark.value)

  mediaQuery.addEventListener('change', (e) => {
    if (!localStorage.getItem('theme')) {
      isDark.value = e.matches
    }
  })
})

watch(isDark, (val) => {
  applyTokens(val)
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
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
}
</style>
