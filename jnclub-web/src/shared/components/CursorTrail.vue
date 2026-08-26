<script setup lang="ts">
/**
 * CursorTrail.vue — 鼠标轨迹特效渲染
 * 挂载在 App.vue，与 CursorHost 同级（z-index:11000, pointer-events:none）。
 * 用 SVG polyline 连线 + circle 点渲染彩色拖尾，三种颜色模式。
 * 优化：circle 使用 transform translate 替代 cx/cy 属性，避免重排触发。
 */
import { computed, type CSSProperties } from 'vue'
import { useCustomCursor } from '../composables/useCustomCursor'
import { useCursorTrailEffect } from '../composables/useCursorTrailEffect'

const cursor = useCustomCursor()
const { points } = useCursorTrailEffect()

/** SVG polyline 的 points 属性字符串 */
const pointsStr = computed(() =>
  points.value.map(p => `${p.x},${p.y}`).join(' '),
)

/** 根据颜色模式返回点的填充色（brand 使用主题 token） */
function pointFill(p: { hue: number; age: number }, mode: string): string {
  switch (mode) {
    case 'rainbow':
      return `hsla(${p.hue}, 80%, 65%, ${Math.max(0, 1 - p.age)})`
    case 'pastel': {
      const pastelHues = [340, 20, 45, 280, 310]
      const h = pastelHues[p.hue % pastelHues.length]
      return `hsla(${h}, 70%, 80%, ${Math.max(0, 1 - p.age)})`
    }
    case 'brand':
    default:
      return 'var(--brand)'
  }
}

/** 点的透明度：brand 模式下 alpha 交给 opacity 控制，便于跟随主题 token */
function pointOpacity(p: { age: number }, mode: string): number {
  const alpha = Math.max(0, 1 - p.age)
  if (mode === 'brand') return alpha * 0.8
  return 1
}

/** 点样式：非品牌色在 fill 中携带 alpha，品牌色用 var(--brand) + opacity */
function pointStyle(p: { hue: number; age: number }, mode: string): CSSProperties {
  return {
    fill: pointFill(p, mode),
    opacity: pointOpacity(p, mode),
  }
}

/** polyline 描边颜色 */
const strokeColor = computed(() => {
  switch (cursor.trailEffect.value) {
    case 'rainbow':
      return 'url(#trail-grad-rainbow)'
    case 'pastel':
      return 'url(#trail-grad-pastel)'
    case 'brand':
    default:
      return 'var(--brand)'
  }
})

/** 描边样式：品牌色走 token，透明度统一由 CSS 控制 */
const strokeStyle = computed<CSSProperties>(() => ({
  stroke: strokeColor.value,
  opacity: 0.7,
}))
</script>

<template>
  <svg
    v-if="cursor.enabled.value && cursor.trailEffect.value !== 'none' && points.length > 1"
    class="cursor-trail"
    aria-hidden="true"
  >
    <defs>
      <linearGradient id="trail-grad-rainbow" x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" stop-color="hsl(0, 80%, 65%)" />
        <stop offset="16%" stop-color="hsl(60, 80%, 65%)" />
        <stop offset="33%" stop-color="hsl(120, 80%, 65%)" />
        <stop offset="50%" stop-color="hsl(180, 80%, 65%)" />
        <stop offset="66%" stop-color="hsl(240, 80%, 65%)" />
        <stop offset="83%" stop-color="hsl(300, 80%, 65%)" />
        <stop offset="100%" stop-color="hsl(360, 80%, 65%)" />
      </linearGradient>
      <linearGradient id="trail-grad-pastel" x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" stop-color="hsl(340, 70%, 80%)" />
        <stop offset="25%" stop-color="hsl(20, 70%, 80%)" />
        <stop offset="50%" stop-color="hsl(45, 70%, 80%)" />
        <stop offset="75%" stop-color="hsl(280, 70%, 80%)" />
        <stop offset="100%" stop-color="hsl(310, 70%, 80%)" />
      </linearGradient>
    </defs>
    <!-- 连线拖尾 -->
    <polyline
      :points="pointsStr"
      fill="none"
      :style="strokeStyle"
      stroke-width="2.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <!-- 各点粒子：使用 transform translate 替代 cx/cy，避免重排 -->
    <circle
      v-for="(p, i) in points"
      :key="i"
      :r="Math.max(1, 3.5 - p.age * 3)"
      :style="{
        ...pointStyle(p, cursor.trailEffect.value),
        transform: `translate(${p.x}px, ${p.y}px)`,
      }"
    />
  </svg>
</template>

<style scoped>
.cursor-trail {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 11000;
  pointer-events: none;
  overflow: hidden;
}
</style>
