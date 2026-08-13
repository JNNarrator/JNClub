<script setup lang="ts">
import ShinyText from './vendor/ShinyText.vue'
import { themeColors } from './tokens'

withDefaults(
  defineProps<{
    text: string
    disabled?: boolean
    speed?: number
    className?: string
    color?: string
    shineColor?: string
    spread?: number
    yoyo?: boolean
    pauseOnHover?: boolean
    direction?: 'left' | 'right'
    delay?: number
  }>(),
  {
    disabled: false,
    speed: 2,
    className: '',
    color: themeColors.textSub,
    shineColor: themeColors.shine,
    spread: 120,
    yoyo: false,
    pauseOnHover: false,
    direction: 'left',
    delay: 0,
  },
)
</script>

<template>
  <ShinyText
    :text="text"
    :disabled="disabled"
    :speed="speed"
    :class-name="className"
    :color="color"
    :shine-color="shineColor"
    :spread="spread"
    :yoyo="yoyo"
    :pause-on-hover="pauseOnHover"
    :direction="direction"
    :delay="delay"
  />
</template>
