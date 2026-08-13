<script setup lang="ts">
import AnimatedContent from './vendor/AnimatedContent.vue'
import { motion } from './tokens'

const emit = defineEmits<{ complete: [] }>()

withDefaults(
  defineProps<{
    distance?: number
    direction?: 'vertical' | 'horizontal'
    reverse?: boolean
    duration?: number
    ease?: string | ((progress: number) => number)
    initialOpacity?: number
    animateOpacity?: boolean
    scale?: number
    threshold?: number
    delay?: number
    className?: string
  }>(),
  {
    distance: 24,
    direction: 'vertical',
    reverse: false,
    duration: motion.duration,
    ease: motion.gsapEase,
    initialOpacity: 0,
    animateOpacity: true,
    scale: 1,
    threshold: 0.1,
    delay: 0,
    className: '',
  },
)
</script>

<template>
  <AnimatedContent
    :distance="distance"
    :direction="direction"
    :reverse="reverse"
    :duration="duration"
    :ease="ease"
    :initial-opacity="initialOpacity"
    :animate-opacity="animateOpacity"
    :scale="scale"
    :threshold="threshold"
    :delay="delay"
    :class-name="className"
    @complete="emit('complete')"
  >
    <slot />
  </AnimatedContent>
</template>
