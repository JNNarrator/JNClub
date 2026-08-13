<script setup lang="ts">
import CountUp from './vendor/CountUp.vue'
import { motion, themeColors } from './tokens'

defineOptions({ inheritAttrs: false })

withDefaults(
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
</script>

<template>
  <span class="j-countup" :style="{ color: themeColors.textMain }">
    <CountUp
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
