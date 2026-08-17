<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NConfigProvider, darkTheme, NMessageProvider, NDialogProvider } from 'naive-ui'
import { tokensToCSSVars, lightTokens, darkTokens } from './themes/tokens'
import lightThemeOverrides from './themes/light'
import darkThemeOverrides from './themes/dark'
import { useOnlineStatus } from './shared/composables/useOnlineStatus'
import CursorHost from './shared/components/CursorHost.vue'
import CursorClickParticles from './shared/components/CursorClickParticles.vue'
import CursorTrail from './shared/components/CursorTrail.vue'
import ParticlesBackground from './shared/components/ParticlesBackground.vue'
import { usePlatform } from './shared/composables/usePlatform'
import { usePerfTier } from './shared/composables/usePerfTier'

// 平台检测：首帧前把 data-platform 写到 <html>，保留给遥测/排障（不再用于降级动画）
const { init: initPlatform } = usePlatform()
initPlatform()
// 能力自适应 FPS 看门狗（替代平台分叉）：同一套代码，弱机自动降级
const { init: initPerfTier } = usePerfTier()
initPerfTier()

const isDark = ref(false)
const route = useRoute()

// 离线提示（PWA 应用壳离线可打开，业务数据仍需网络）
const { isOnline } = useOnlineStatus()

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
        <Transition name="offline-fade">
          <div v-if="!isOnline" class="offline-banner" role="status">
            <span class="offline-dot" />当前离线 — 应用已离线打开，数据可能需要联网后加载
          </div>
        </Transition>
        <router-view v-slot="{ Component }">
          <Transition name="page-fade" mode="out-in">
            <component
              :is="Component"
              :key="route.path"
              :is-dark="isDark"
              @toggle-theme="toggleTheme"
            />
          </Transition>
        </router-view>
        <!-- 全局粒子背景（tsParticles，偏好 particles.style 驱动，路由内排除音乐/便签） -->
        <ParticlesBackground :is-dark="isDark" />
        <!-- 全局自定义光标（可爱光标：5 种风格，偏好 cursor.style 驱动） -->
        <CursorHost />
        <!-- 点击粒子特效（星星/爱心/花朵散开） -->
        <CursorClickParticles />
        <!-- 鼠标轨迹特效（彩虹/品牌粉/粉彩拖尾） -->
        <CursorTrail />
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

/* 离线提示条：固定顶部，不遮挡内容 */
.offline-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--glass-bg-solid);
  border-bottom: 1px solid var(--glass-border);
  color: var(--text-2);
  font-size: 13px;
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}
.offline-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--state-warning);
  flex-shrink: 0;
}
.offline-fade-enter-active,
.offline-fade-leave-active {
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease);
}
.offline-fade-enter-from,
.offline-fade-leave-to {
  opacity: 0;
  transform: translateY(-100%);
}

/* 全局路由过渡：淡入 + 轻微上移（vue-bits 适配层节奏，随主题 token 缓动） */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--dur, 0.2s) var(--ease, ease), transform var(--dur, 0.2s) var(--ease, ease);
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
