<script setup lang="ts">
import { computed } from 'vue'
import BlurText from './vendor/BlurText.vue'
import { useReducedMotion } from '../../composables/useReducedMotion'

const props = withDefaults(
  defineProps<{
    text?: string
    delay?: number
    className?: string
    animateBy?: 'words' | 'letters'
    direction?: 'top' | 'bottom'
    threshold?: number
    rootMargin?: string
    animationFrom?: Record<string, string | number>
    animationTo?: Array<Record<string, string | number>>
    easing?: (t: number) => number
    stepDuration?: number
  }>(),
  {
    text: '',
    delay: 120,
    className: '',
    animateBy: 'words',
    direction: 'top',
    threshold: 0.1,
    rootMargin: '0px',
    stepDuration: 0.35,
  },
)

const reduced = useReducedMotion()

/** 减少动态效果：从「已清晰、已就位」起手，取消逐词模糊入场 */
const animFrom = computed(() =>
  reduced.value ? { filter: 'blur(0px)', opacity: 1, y: 0 } : props.animationFrom,
)
const animDelay = computed(() => (reduced.value ? 0 : props.delay))
const animStep = computed(() => (reduced.value ? 0 : props.stepDuration))
</script>

<template>
  <BlurText
    :text="text"
    :delay="animDelay"
    :class-name="className"
    :animate-by="animateBy"
    :direction="direction"
    :threshold="threshold"
    :root-margin="rootMargin"
    :animation-from="animFrom"
    :animation-to="animationTo"
    :easing="easing"
    :step-duration="animStep"
  />
</template>
