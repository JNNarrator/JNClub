<script setup lang="ts">
/**
 * CursorTrail.vue — 鼠标轨迹特效渲染
 * 挂载在 App.vue，与 CursorHost 同级（z-index:11000, pointer-events:none）。
 * 用 SVG polyline 连线 + circle 点渲染彩色拖尾，三种颜色模式：
 *   - rainbow：hue 递增，彩色渐变
 *   - brand：品牌粉色系，透明度随 age 衰减
 *   - pastel：粉彩色系轮换
 */
import { computed } from 'vue'
import { useCustomCursor } from '../composables/useCustomCursor'
import { useCursorTrailEffect } from '../composables/useCursorTrailEffect'

const cursor = useCustomCursor()
const { points } = useCursorTrailEffect()

/** SVG polyline 的 points 属性字符串 */
const pointsStr = computed(() =>
  points.value.map(p => `${p.x},${p.y}`).join(' '),
)

/** 根据颜色模式返回点的颜色 */
function pointColor(p: { hue: number; age: number }, mode: string): string {
  const alpha = Math.max(0, 1 - p.age)
  switch (mode) {
    case 'rainbow':
      return `hsla(${p.hue}, 80%, 65%, ${alpha})`
    case 'pastel': {
      const pastelHues = [340, 20, 45, 280, 310]
      const h = pastelHues[p.hue % pastelHues.length]
      return `hsla(${h}, 70%, 80%, ${alpha})`
    }
    case 'brand':
    default:
      return `rgba(236, 91, 142, ${alpha * 0.8})`
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
      return 'rgba(236, 91, 142, 0.5)'
  }
})
</script>

<template>
  <svg
    v-if="cursor.enabled.value && cursor.trailEffect.value !== 'none' && points.length > 1"
    class="cursor-trail"
    aria-hidden="true"
  >
    <defs>
      <!-- rainbow 渐变：12 色相 -->
      <linearGradient id="trail-grad-rainbow" x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" stop-color="hsl(0, 80%, 65%)" />
        <stop offset="16%" stop-color="hsl(60, 80%, 65%)" />
        <stop offset="33%" stop-color="hsl(120, 80%, 65%)" />
        <stop offset="50%" stop-color="hsl(180, 80%, 65%)" />
        <stop offset="66%" stop-color="hsl(240, 80%, 65%)" />
        <stop offset="83%" stop-color="hsl(300, 80%, 65%)" />
        <stop offset="100%" stop-color="hsl(360, 80%, 65%)" />
      </linearGradient>
      <!-- pastel 渐变：柔和粉彩 -->
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
      :stroke="strokeColor"
      stroke-width="2.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      opacity="0.7"
    />
    <!-- 各点粒子 -->
    <circle
      v-for="(p, i) in points"
      :key="i"
      :cx="p.x"
      :cy="p.y"
      :r="Math.max(1, 3.5 - p.age * 3)"
      :fill="pointColor(p, cursor.trailEffect.value)"
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
