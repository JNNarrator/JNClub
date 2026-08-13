<script setup lang="ts">
import GradientText from './vendor/GradientText.vue'
import { themeColors } from './tokens'

withDefaults(
  defineProps<{
    className?: string
    colors?: string[]
    animationSpeed?: number
    showBorder?: boolean
    direction?: 'horizontal' | 'vertical' | 'diagonal'
    pauseOnHover?: boolean
    yoyo?: boolean
  }>(),
  {
    className: '',
    colors: () => [themeColors.brand, themeColors.brandStrong, themeColors.brand],
    animationSpeed: 8,
    showBorder: false,
    direction: 'horizontal',
    pauseOnHover: false,
    yoyo: true,
  },
)
</script>

<template>
  <GradientText
    :class-name="className"
    :colors="colors"
    :animation-speed="animationSpeed"
    :show-border="showBorder"
    :direction="direction"
    :pause-on-hover="pauseOnHover"
    :yoyo="yoyo"
  >
    <slot />
  </GradientText>
</template>
