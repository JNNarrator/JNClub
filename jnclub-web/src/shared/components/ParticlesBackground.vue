<script setup lang="ts">
/**
 * ParticlesBackground.vue — 全局粒子特效背景（tsParticles，挂在 App.vue，全路由生效）
 * 4 套风格由偏好 particles.style 驱动；排除音乐页、便签编辑/预览页、SSO 回调页
 * 性能克制：interactivity 全关、fpsLimit 30、粒子数 ≤40、detectRetina false、pointer-events none
 * 触屏 / prefers-reduced-motion 自动关闭
 */
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { ISourceOptions } from '@tsparticles/engine'
import { useParticlesSettings } from '../composables/useParticlesSettings'

const props = defineProps<{
  isDark: boolean
}>()

const route = useRoute()
const settings = useParticlesSettings()

/** 排除页面：音乐、便签新建/查看（编辑+预览）、SSO 回调 */
const EXCLUDED_ROUTES = new Set(['music', 'note-create', 'note-view', 'sso-callback'])
const shouldShow = computed(() => {
  if (settings.style.value === 'none') return false
  if (settings.coarsePointer.value || settings.reducedMotion.value) return false
  return !EXCLUDED_ROUTES.has(route.name as string)
})

onMounted(() => {
  settings.init()
})

/** 亮暗主题配色（与 tokens.ts 对应） */
const lightColors = ['#EC5B8E', '#FF8FAB', '#FFB3C6', '#F472B6']
const darkColors = ['#FF8FAB', '#F472B6', '#5A3A45', '#FFB3C6']

/** 按风格 + 主题构建 options（slim：circle 形状，靠颜色/大小/速度/方向区分） */
const options = computed<ISourceOptions>(() => {
  const style = settings.style.value
  const palette = props.isDark ? darkColors : lightColors
  const base = {
    fullScreen: { enable: false },
    background: { color: { value: 'transparent' } },
    fpsLimit: 30,
    detectRetina: false,
    interactivity: {
      events: { onHover: { enable: false }, onClick: { enable: false } },
    },
    particles: {
      number: { value: 36, density: { enable: true } },
      color: { value: palette },
      shape: { type: 'circle' },
      links: { enable: false },
      move: { enable: true, outModes: 'out', random: true },
    },
  }

  switch (style) {
    case 'snow':
      return {
        ...base,
        particles: {
          ...base.particles,
          number: { value: 32, density: { enable: true } },
          opacity: { value: { min: 0.2, max: 0.7 } },
          size: { value: { min: 1, max: 3.5 } },
          move: { ...base.particles.move, direction: 'bottom', speed: { min: 0.4, max: 1.2 } },
        },
      } as ISourceOptions
    case 'petals':
      return {
        ...base,
        particles: {
          ...base.particles,
          number: { value: 20, density: { enable: true } },
          opacity: { value: { min: 0.3, max: 0.7 } },
          size: { value: { min: 3, max: 7 } },
          move: { ...base.particles.move, direction: 'bottom', speed: { min: 0.5, max: 1.4 } },
        },
      } as ISourceOptions
    case 'float-dots':
    default:
      return {
        ...base,
        particles: {
          ...base.particles,
          number: { value: 40, density: { enable: true } },
          opacity: { value: { min: 0.15, max: 0.55 } },
          size: { value: { min: 1, max: 4 } },
          move: { ...base.particles.move, direction: 'top', speed: { min: 0.3, max: 0.9 } },
        },
      } as ISourceOptions
  }
})
</script>

<template>
  <div v-if="shouldShow" class="particles-bg" aria-hidden="true">
    <VueParticles id="jnclub-particles" :options="options" />
  </div>
</template>

<style scoped>
.particles-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}
.particles-bg :deep(#jnclub-particles),
.particles-bg :deep(canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
