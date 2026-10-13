<script setup lang="ts">
import GlareHover from './vendor/GlareHover.vue'

defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    width?: string
    height?: string
    background?: string
    borderRadius?: string
    borderColor?: string
    glareColor?: string
    glareOpacity?: number
    glareAngle?: number
    glareSize?: number
    transitionDuration?: number
    playOnce?: boolean
    className?: string
  }>(),
  {
    width: '100%',
    height: '100%',
    background: 'transparent',
    borderRadius: 'var(--radius-lg)',
    borderColor: 'var(--glass-border)',
    // 唯一保留字面量的项：vendor 会解析它的 hex 再拼 rgba(…, glareOpacity)。
    // 传 var() 会落到 fallback 分支、丢掉 glareOpacity，光泽变成实心白。
    // 「高光用纯白」在亮暗两主题下都成立，故不 token 化。
    glareColor: '#ffffff',
    glareOpacity: 0.5,
    glareAngle: -45,
    glareSize: 250,
    transitionDuration: 650,
    playOnce: false,
    className: '',
  },
)
</script>

<template>
  <GlareHover
    :width="width"
    :height="height"
    :background="background"
    :border-radius="borderRadius"
    :border-color="borderColor"
    :glare-color="glareColor"
    :glare-opacity="glareOpacity"
    :glare-angle="glareAngle"
    :glare-size="glareSize"
    :transition-duration="transitionDuration"
    :play-once="playOnce"
    :class-name="className"
    v-bind="$attrs"
  >
    <slot />
  </GlareHover>
</template>
