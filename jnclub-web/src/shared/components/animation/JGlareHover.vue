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
