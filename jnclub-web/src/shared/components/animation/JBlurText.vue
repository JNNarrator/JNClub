<script setup lang="ts">
import BlurText from './vendor/BlurText.vue'

withDefaults(
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
</script>

<template>
  <BlurText
    :text="text"
    :delay="delay"
    :class-name="className"
    :animate-by="animateBy"
    :direction="direction"
    :threshold="threshold"
    :root-margin="rootMargin"
    :animation-from="animationFrom"
    :animation-to="animationTo"
    :easing="easing"
    :step-duration="stepDuration"
  />
</template>
