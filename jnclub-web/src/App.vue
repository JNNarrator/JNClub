<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { NConfigProvider, darkTheme, NMessageProvider, NDialogProvider } from 'naive-ui'
import lightThemeOverrides from './themes/light'
import darkThemeOverrides from './themes/dark'
import { lightTokens, darkTokens } from './themes/tokens'
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

/**
 * 首帧即取到正确主题：index.html 的内联脚本已用同一份判定写入 data-theme，
 * 这里同步读取可保证 naive-ui 首帧就是对应主题（否则暗色用户会闪一帧亮色弹层）。
 */
function resolveInitialDark(): boolean {
  try {
    const saved = localStorage.getItem('theme')
    if (saved) return saved === 'dark'
  } catch {
    /* localStorage 不可用：退回系统偏好 */
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

const isDark = ref(resolveInitialDark())
const route = useRoute()

// 离线提示（PWA 应用壳离线可打开，业务数据仍需网络）
const { isOnline } = useOnlineStatus()

/**
 * 主题切换只写 <html data-theme>，具体变量由构建期生成的 tokens.generated.css 提供。
 * 原先在此处循环 setProperty 注入 117 个变量，导致首帧 var() 全部无值回退（FOUC），
 * 且硬编码颜色永远不随主题变化（缺少 CSS 侧主题钩子）。
 */
function applyTheme(dark: boolean) {
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  // PWA 状态栏颜色：meta 的 media 分支只认系统偏好，认不了用户显式选择。
  // 取 token 而非再写一遍字面量 —— meta 认不了 var()，这里是唯一能复用单一来源的方式。
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', dark ? darkTokens.bgPage : lightTokens.brand)
}

onMounted(() => {
  applyTheme(isDark.value)

  // 仅在用户未显式选择过主题时跟随系统变化
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery.addEventListener('change', (e) => {
    if (!localStorage.getItem('theme')) {
      isDark.value = e.matches
    }
  })
})

watch(isDark, (val) => applyTheme(val))

/* ------------------------------------------------------------------ *
 * 主题切换的圆形揭示（View Transition API）
 *
 * 整页硬闪是切换主题最刺眼的瞬间。这里用 startViewTransition 把「旧主题快照
 * → 新主题」包起来，新主题以点击位置为圆心做 clip-path 圆形扩散。
 *
 * 揭示原点不通过 emit 层层透传，而是就地解析：
 *  1) document.activeElement —— 按钮点击/键盘回车后都指向该按钮（Chrome/Firefox）
 *  2) 最近一次 pointerdown 坐标 —— Safari 点击按钮不聚焦，activeElement 会停在 body
 *  3) 视口中心 —— 纯键盘或程序化调用的兜底
 * 这样 8 个 toggle-theme 透传层无需改动，也不引入跨层耦合。
 * ------------------------------------------------------------------ */

type ViewTransitionDocument = Document & {
  startViewTransition?: (callback: () => void | Promise<void>) => { finished: Promise<void> }
}

const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')

let lastPointer: { x: number; y: number } | null = null
// capture + passive：早于按钮自己的 click 处理器记录，且不干扰任何滚动/点击
window.addEventListener(
  'pointerdown',
  (e) => {
    lastPointer = { x: e.clientX, y: e.clientY }
  },
  { passive: true, capture: true },
)

function resolveRevealOrigin(): { x: number; y: number } {
  const el = document.activeElement as HTMLElement | null
  if (el && el !== document.body && typeof el.getBoundingClientRect === 'function') {
    const rect = el.getBoundingClientRect()
    if (rect.width || rect.height) {
      return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
    }
  }
  if (lastPointer) return lastPointer
  return { x: window.innerWidth / 2, y: window.innerHeight / 2 }
}

const toggleTheme = async () => {
  const next = !isDark.value
  const commit = () => {
    isDark.value = next
    localStorage.setItem('theme', next ? 'dark' : 'light')
  }

  const startViewTransition = (document as ViewTransitionDocument).startViewTransition
  // 不支持该 API 或用户要求减少动态效果时，直接切换（不做揭示动画）
  if (typeof startViewTransition !== 'function' || reducedMotionQuery.matches) {
    commit()
    return
  }

  const { x, y } = resolveRevealOrigin()
  const root = document.documentElement
  root.style.setProperty('--vt-x', `${x}px`)
  root.style.setProperty('--vt-y', `${y}px`)

  const transition = startViewTransition.call(document, async () => {
    commit()
    // 等 data-theme 与 naive-ui 主题实际落到 DOM，View Transition 才会截到新主题
    await nextTick()
  })

  try {
    await transition.finished
  } catch {
    /* 快速连点会让上一段过渡被跳过并以 rejected promise 结束，属预期 */
  } finally {
    root.style.removeProperty('--vt-x')
    root.style.removeProperty('--vt-y')
  }
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
  font-size: var(--fs-md);
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

/* 全局路由过渡：淡入 + 轻微上移（随主题 token 缓动）
   out-in 模式下总耗时 = 离场 + 入场，用 --dur-fast 把 400ms 压到 300ms，
   翻页手感更跟手；进场比离场略慢，保证新页面「落定」的从容感。 */
.page-fade-leave-active {
  transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}
.page-fade-enter-active {
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease);
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
