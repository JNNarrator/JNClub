<script setup lang="ts">
/**
 * JGradientText — 渐变流动文字（GradientText 封装）
 * 官方组件用 slots.default 拼接文本，经 slot 转发链(_renderSlot/_withCtx)后
 * v.children 可能变成对象导致 "[object Object]"，故改由本封装直接渲染静态文本，
 * 业务侧统一传 text prop。
 */
import GradientText from './vendor/GradientText.vue'
import { themeColors } from './tokens'

withDefaults(
  defineProps<{
    text: string
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
    {{ text }}
  </GradientText>
</template>
