<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { computed } from 'vue'
import SplitText from './vendor/SplitText.vue'
import { motion } from './tokens'
import { useReducedMotion } from '../../composables/useReducedMotion'

type SplitType = 'chars' | 'words' | 'lines' | 'words, chars'
type TagName = 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'p' | 'span'

const emit = defineEmits<{ 'animation-complete': [] }>()

const props = withDefaults(
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

const reduced = useReducedMotion()

/** 减少动态效果：取消位移与错峰，GSAP 只落终态（仍在 DOM 层完成字符拆分） */
const animFrom = computed(() => (reduced.value ? { opacity: 1, y: 0 } : props.from))
const animDelay = computed(() => (reduced.value ? 0 : props.delay))
const animDuration = computed(() => (reduced.value ? 0 : props.duration))
</script>

<template>
  <SplitText
    :text="text"
    :class-name="className"
    :delay="animDelay"
    :duration="animDuration"
    :ease="ease"
    :split-type="splitType"
    :from="animFrom"
    :to="to"
    :threshold="threshold"
    :root-margin="rootMargin"
    :tag="tag"
    :text-align="textAlign"
    @animation-complete="emit('animation-complete')"
  />
</template>
