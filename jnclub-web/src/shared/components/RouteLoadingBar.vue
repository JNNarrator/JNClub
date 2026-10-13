<script setup lang="ts">
/**
 * RouteLoadingBar —— 把路由导航接到 naive-ui 的顶部进度条。
 *
 * 为什么需要：vite 按路由做了 chunk 分割，首次进入某个页面要下载对应 chunk；
 * 这期间页面完全静止，用户不知道点击是否生效（尤其弱网/首访）。
 * 顶部 2px 进度条是这类等待最轻的反馈，不遮挡内容、不打断过渡动画。
 *
 * 两个刻意的设计：
 * 1. **180ms 阈值**：同一 chunk 已缓存时导航常在几十毫秒内完成，若每次都 start
 *    就会看到进度条闪一下，比没有更烦。超过阈值才真正显示。
 * 2. **挂在 NLoadingBarProvider 内**：useLoadingBar 依赖 provider 注入，
 *    本组件必须渲染在 provider 子树里（见 App.vue）。
 *
 * 它不负责页面内容的加载反馈 —— 那是各页骨架屏的职责，两者互不替代：
 * 进度条回答「点击有没有生效」，骨架屏回答「数据到哪了」。
 */
import { onUnmounted } from 'vue'
import { useLoadingBar } from 'naive-ui'
import { useRouter } from 'vue-router'

/** 导航快于此值不显示进度条，避免缓存命中时的闪烁 */
const SHOW_DELAY = 180

const loadingBar = useLoadingBar()
const router = useRouter()

let timer: ReturnType<typeof setTimeout> | null = null
let pending = false
let started = false

function clearTimer() {
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

const offBefore = router.beforeEach(() => {
  pending = true
  clearTimer()
  timer = setTimeout(() => {
    if (!pending) return
    started = true
    loadingBar.start()
  }, SHOW_DELAY)
})

// afterEach 在导航确认或失败后都会触发，用它统一收尾，无需区分成功/取消
const offAfter = router.afterEach(() => {
  pending = false
  clearTimer()
  if (started) {
    started = false
    loadingBar.finish()
  }
})

const offError = router.onError(() => {
  pending = false
  clearTimer()
  if (started) {
    started = false
    loadingBar.error()
  }
})

onUnmounted(() => {
  clearTimer()
  offBefore()
  offAfter()
  offError()
})
</script>

<template>
  <!-- 无渲染：进度条由 NLoadingBarProvider 自己 Teleport 到 body -->
</template>
