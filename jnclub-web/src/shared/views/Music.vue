<script setup lang="ts">
/**
 * Music.vue — 音乐播放器内嵌页
 * 在 JNClub 壳内以 iframe 全屏内嵌 /music/ 播放器（nginx 静态托管，对外路径不变）。
 * 音乐页面自身无登录态要求，保持匿名访问；仅当用户进入 JNClub 后通过侧边栏入口打开。
 *
 * 主题衔接（PLAN-2026-19 M3-1）：音乐前端有独立的暖橙体系与自己的主题开关，
 * 主站只把「当前是亮还是暗」传过去，不干预它的品牌色与色温。两条通道各司其职：
 *   1. 首次加载走 `?theme=` —— 首帧就要用对底色，postMessage 来不及；
 *   2. 之后走 postMessage —— 换 src 会重载 iframe、中断播放，主题切换不能走那条路。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

type ThemeMode = 'light' | 'dark'

/** 主站主题落在 `<html data-theme>`（见 themes/tokens.generated.css），读属性即可 */
function readTheme(): ThemeMode {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

const frameRef = ref<HTMLIFrameElement | null>(null)
/** 首帧主题只取一次，此后由 postMessage 接管 */
const src = `/music/?theme=${readTheme()}`
const loaded = ref(false)

function pushTheme() {
  const target = frameRef.value?.contentWindow
  if (!target) return
  target.postMessage({ type: 'jn:theme', theme: readTheme() }, window.location.origin)
}

let observer: MutationObserver | null = null
let fallbackTimer: number | undefined

/** 加载完成 → 渐显并补推一次主题（主题可能在 iframe 就绪前就切过了） */
function onFrameLoad() {
  loaded.value = true
  window.clearTimeout(fallbackTimer)
  pushTheme()
}

onMounted(() => {
  observer = new MutationObserver(pushTheme)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  // 兜底：load 事件若因故丢失，不能让 iframe 永远停在透明态
  fallbackTimer = window.setTimeout(() => {
    loaded.value = true
  }, 3000)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.clearTimeout(fallbackTimer)
})
</script>

<template>
  <div class="music-page">
    <iframe
      ref="frameRef"
      class="music-frame"
      :class="{ 'is-loaded': loaded }"
      :src="src"
      title="JNMusic 音乐播放器"
      loading="eager"
      allow="autoplay; fullscreen"
      @load="onFrameLoad"
    />
  </div>
</template>

<style scoped>
.music-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  /* 音乐前端自带暖色底，这里只跟随主站主题的明暗，避免加载瞬间透出冲突底色 */
  background: var(--bg-page);
}

.music-frame {
  flex: 1;
  width: 100%;
  border: none;
  background: var(--bg-page);
  /* 渐显代替白屏：iframe 内的主题由 ?theme= 首帧决定，加载期间不该看到白 */
  opacity: 0;
  transition: opacity var(--dur-slow) var(--ease);
}

.music-frame.is-loaded {
  opacity: 1;
}
</style>
