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
import { usePerfTier } from '../composables/usePerfTier'

const props = defineProps<{
  isDark: boolean
}>()

const route = useRoute()
const settings = useParticlesSettings()
const perf = usePerfTier()

/** 排除页面：音乐、便签新建/查看（编辑+预览）、SSO 回调 */
const EXCLUDED_ROUTES = new Set(['music', 'note-create', 'note-view', 'sso-callback'])
const shouldShow = computed(() => {
  if (settings.style.value === 'none') return false
  if (settings.coarsePointer.value || settings.reducedMotion.value) return false
  // FPS 看门狗降级：tier>=1 时关粒子（持续耗能效果）
  if (perf.tier.value >= 1) return false
  return !EXCLUDED_ROUTES.has(route.name as string)
})

onMounted(() => {
  settings.init()
})

/** 亮暗主题配色：亮色用极深高对比色（确保在 #F5F5F7 浅底上清晰可见）；暗色用柔和亮色 */
const lightColors = ['#7A1247', '#8C1750', '#9E1D5A', '#A41F5D']
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
          number: { value: 36, density: { enable: true } },
          opacity: { value: { min: 0.5, max: 0.9 } },
          size: { value: { min: 3, max: 7 } },
          move: { ...base.particles.move, direction: 'bottom', speed: { min: 0.4, max: 1.2 } },
        },
      } as ISourceOptions
    case 'petals':
      return {
        ...base,
        particles: {
          ...base.particles,
          number: { value: 24, density: { enable: true } },
          opacity: { value: { min: 0.55, max: 0.9 } },
          size: { value: { min: 6, max: 14 } },
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
          opacity: { value: { min: 0.5, max: 0.85 } },
          size: { value: { min: 3, max: 8 } },
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
  /* 高于主界面内容层（.home z-index:1，毛玻璃面板会盖住背景），
     低于 FAB(100)/弹窗(2000+)/自定义光标(11000)；pointer-events:none 不挡任何点击 */
  z-index: 5;
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
