<script setup lang="ts">
import type { CSSProperties } from 'vue'
import SplitText from './vendor/SplitText.vue'
import { motion } from './tokens'

type SplitType = 'chars' | 'words' | 'lines' | 'words, chars'
type TagName = 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'p' | 'span'

const emit = defineEmits<{ 'animation-complete': [] }>()

withDefaults(
  defineProps<{
    text: string
    className?: string
    delay?: number
    duration?: number
    ease?: string | ((t: number) => number)
    splitType?: SplitType
    from?: Record<string, string | number>
    to?: Record<string, string | number>
    threshold?: number
    rootMargin?: string
    tag?: TagName
    textAlign?: CSSProperties['textAlign']
  }>(),
  {
    className: '',
    delay: motion.stagger,
    duration: motion.duration,
    ease: motion.gsapEase,
    splitType: 'chars',
    from: () => ({ opacity: 0, y: 24 }),
    to: () => ({ opacity: 1, y: 0 }),
    threshold: 0.1,
    rootMargin: '-40px',
    tag: 'span',
    textAlign: 'left',
  },
)
</script>

<template>
  <SplitText
    :text="text"
    :class-name="className"
    :delay="delay"
    :duration="duration"
    :ease="ease"
    :split-type="splitType"
    :from="from"
    :to="to"
    :threshold="threshold"
    :root-margin="rootMargin"
    :tag="tag"
    :text-align="textAlign"
    @animation-complete="emit('animation-complete')"
  />
</template>
