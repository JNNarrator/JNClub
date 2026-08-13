<script setup lang="ts">
/**
 * CursorHost.vue — 全局自定义光标宿主（可爱光标，挂在 App.vue，全路由生效）
 * 两种风格（偏好 cursor.style 驱动）：
 *   - dot-halo：主圆点即时跟随 + 光环 rAF lerp 延迟追赶（弹性拖尾）；悬停放大变色，按下缩小
 *   - emoji：🐾 跟随（悬停可交互元素换 💗，按下换 ✨），同样 lerp 弹性
 * 触屏(pointer:coarse)不渲染；prefers-reduced-motion 时禁弹性直接落位；输入框内隐藏露出 I-beam。
 * 启用时给 <html> 加 .custom-cursor-active → 全局 cursor:none（规则见 main.css）。
 */
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useCustomCursor } from '../composables/useCustomCursor'

const cursor = useCustomCursor()

/** 光环追赶速率：越大越跟手（时间无关阻尼，与刷新率解耦，60/30fps 手感一致） */
const SMOOTHING = 18
/** rAF 句柄 */
let raf = 0
/** 上一帧时间戳，用于计算 dt 做时间无关阻尼 */
let lastT = 0

const loop = (t: number) => {
  // 限制 dt 上限，避免切后台回前台时产生一次大幅跳变
  const dt = lastT ? Math.min((t - lastT) / 1000, 0.05) : 1 / 60
  lastT = t
  if (cursor.reducedMotion.value) {
    // 减少动态：光环直接落位，不做弹性追赶
    cursor.haloX.value = cursor.x.value
    cursor.haloY.value = cursor.y.value
  } else {
    const k = 1 - Math.exp(-SMOOTHING * dt)
    cursor.haloX.value += (cursor.x.value - cursor.haloX.value) * k
    cursor.haloY.value += (cursor.y.value - cursor.haloY.value) * k
  }
  raf = requestAnimationFrame(loop)
}

/** emoji 模式文案：按下/悬停/默认 */
const emoji = computed(() => {
  if (cursor.pressed.value) return '✨'
  if (cursor.hovering.value) return '💗'
  return '🐾'
})

onMounted(() => {
  cursor.init()
  document.documentElement.classList.add('custom-cursor-active')
  raf = requestAnimationFrame(loop)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  document.documentElement.classList.remove('custom-cursor-active')
  cursor.destroy()
})
</script>

<template>
  <div
    v-if="cursor.enabled.value && cursor.visible.value && !cursor.inInput.value"
    class="cursor-host"
    aria-hidden="true"
  >
    <!-- 风格一：圆点 + 光环 -->
    <template v-if="cursor.style.value === 'dot-halo'">
      <div
        class="cursor-dot"
        :class="{ pressed: cursor.pressed.value }"
        :style="{ transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)` }"
      />
      <div
        class="cursor-halo"
        :class="{ hovering: cursor.hovering.value, pressed: cursor.pressed.value }"
        :style="{ transform: `translate3d(${cursor.haloX.value}px, ${cursor.haloY.value}px, 0)` }"
      />
    </template>
    <!-- 风格二：emoji 跟随（外层 translate3d 定位，内层 span 做缩放动画避免覆盖定位） -->
    <div
      v-else
      class="cursor-emoji"
      :style="{ transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)` }"
    >
      <span class="cursor-emoji-inner" :class="{ hovering: cursor.hovering.value, pressed: cursor.pressed.value }">{{ emoji }}</span>
    </div>
  </div>
</template>

<style scoped>
.cursor-host {
  position: fixed;
  inset: 0;
  z-index: 11000; /* 高于项目内最高自定义浮层（悬浮大纲 10200） */
  pointer-events: none;
}

/* 主圆点：12px，即时跟随（translate3d 合成层，不触发重排） */
.cursor-dot {
  position: absolute;
  top: 0;
  left: 0;
  width: 12px;
  height: 12px;
  margin: -6px 0 0 -6px; /* 居中 */
  border-radius: 50%;
  background: var(--brand);
  box-shadow: 0 0 8px color-mix(in srgb, var(--brand) 55%, transparent);
  transition: width var(--dur) var(--ease-bouncy), height var(--dur) var(--ease-bouncy),
    margin var(--dur) var(--ease-bouncy), background-color var(--dur) var(--ease);
  will-change: transform;
}
.cursor-dot.pressed {
  width: 8px;
  height: 8px;
  margin: -4px 0 0 -4px;
}

/* 光环：36px 透明环，lerp 延迟追赶形成弹性拖尾 */
.cursor-halo {
  position: absolute;
  top: 0;
  left: 0;
  width: 36px;
  height: 36px;
  margin: -18px 0 0 -18px;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--brand) 70%, transparent);
  background: color-mix(in srgb, var(--brand) 8%, transparent);
  transition: width var(--dur) var(--ease-bouncy), height var(--dur) var(--ease-bouncy),
    margin var(--dur) var(--ease-bouncy), border-color var(--dur) var(--ease),
    background-color var(--dur) var(--ease);
  will-change: transform;
}
.cursor-halo.hovering {
  width: 52px;
  height: 52px;
  margin: -26px 0 0 -26px;
  border-color: var(--brand);
  background: color-mix(in srgb, var(--brand) 14%, transparent);
}
.cursor-halo.pressed {
  width: 24px;
  height: 24px;
  margin: -12px 0 0 -12px;
  border-color: var(--brand);
  background: color-mix(in srgb, var(--brand) 20%, transparent);
}

/* emoji 光标：外层定位（lerp 弹性），内层缩放动画 */
.cursor-emoji {
  position: absolute;
  top: 0;
  left: 0;
  width: 28px;
  height: 28px;
  margin: -14px 0 0 -14px;
  display: flex;
  align-items: center;
  justify-content: center;
  will-change: transform;
}
.cursor-emoji-inner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  line-height: 1;
  filter: drop-shadow(0 2px 6px color-mix(in srgb, var(--brand) 30%, transparent));
  transition: transform var(--dur) var(--ease-bouncy);
}
.cursor-emoji-inner.hovering { transform: scale(1.25); }
.cursor-emoji-inner.pressed { transform: scale(0.85); }
</style>
