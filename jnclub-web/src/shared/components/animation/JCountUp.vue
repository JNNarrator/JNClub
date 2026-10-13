<script setup lang="ts">
import { computed } from 'vue'
import CountUp from './vendor/CountUp.vue'
import { motion, themeColors } from './tokens'
import { useReducedMotion } from '../../composables/useReducedMotion'

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    to: number
    from?: number
    direction?: 'up' | 'down'
    delay?: number
    duration?: number
    separator?: string
    startWhen?: boolean
    className?: string
    onStart?: () => void
    onEnd?: () => void
  }>(),
  {
    from: 0,
    direction: 'up',
    delay: 0,
    duration: motion.duration,
    separator: '',
    startWhen: true,
    className: '',
  },
)

const reduced = useReducedMotion()

/**
 * 减少动态效果时不做 rAF 计数，直接显示终值。
 * 格式化规则与 vendor/CountUp.vue 的 formatNumber 保持一致。
 */
const staticText = computed(() => {
  const text = Intl.NumberFormat('en-US', {
    useGrouping: !!props.separator,
    maximumFractionDigits: 0,
  }).format(props.to)
  return props.separator ? text.replace(/,/g, props.separator) : text
})
</script>

<template>
  <span class="j-countup" :style="{ color: themeColors.textMain }">
    <template v-if="reduced">{{ staticText }}</template>
    <CountUp
      v-else
      :to="to"
      :from="from"
      :direction="direction"
      :delay="delay"
      :duration="duration"
      :separator="separator"
      :start-when="startWhen"
      :class-name="className"
      :on-start="onStart"
      :on-end="onEnd"
      v-bind="$attrs"
    />
  </span>
</template>
