<script setup lang="ts">
import { computed } from 'vue'
import AnimatedContent from './vendor/AnimatedContent.vue'
import { motion } from './tokens'
import { useReducedMotion } from '../../composables/useReducedMotion'

const emit = defineEmits<{ complete: [] }>()

const props = withDefaults(
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

const reduced = useReducedMotion()

/** 减少动态效果：取消位移、缩放与淡入，内容直接出现在最终态 */
const animDistance = computed(() => (reduced.value ? 0 : props.distance))
const animDuration = computed(() => (reduced.value ? 0 : props.duration))
const animOpacity = computed(() => (reduced.value ? false : props.animateOpacity))
const animScale = computed(() => (reduced.value ? 1 : props.scale))
const animDelay = computed(() => (reduced.value ? 0 : props.delay))
</script>

<template>
  <AnimatedContent
    :distance="animDistance"
    :direction="direction"
    :reverse="reverse"
    :duration="animDuration"
    :ease="ease"
    :initial-opacity="initialOpacity"
    :animate-opacity="animOpacity"
    :scale="animScale"
    :threshold="threshold"
    :delay="animDelay"
    :class-name="className"
    @complete="emit('complete')"
  >
    <slot />
  </AnimatedContent>
</template>
