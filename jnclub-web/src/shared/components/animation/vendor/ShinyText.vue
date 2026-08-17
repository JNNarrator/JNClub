<script setup lang="ts">
import { computed } from 'vue';

interface ShinyTextProps {
  text: string;
  disabled?: boolean;
  speed?: number;
  className?: string;
  color?: string;
  shineColor?: string;
  spread?: number;
  yoyo?: boolean;
  pauseOnHover?: boolean;
  direction?: 'left' | 'right';
  delay?: number;
}

const props = withDefaults(defineProps<ShinyTextProps>(), {
  disabled: false,
  speed: 2,
  className: '',
  color: '#b5b5b5',
  shineColor: '#ffffff',
  spread: 120,
  yoyo: false,
  pauseOnHover: false,
  direction: 'left',
  delay: 0,
});

/**
 * CSS 化实现（替代原 motion-v useAnimationFrame 逐帧驱动）：
 * 用浏览器原生 @keyframes 驱动 background-position 扫光，不再每个实例跑 rAF，
 * 两平台渲染统一由浏览器调度，主线程零负载、行为一致。
 * props 透传保持不变（业务侧用 JShinyText 无感）。
 */
const wrapperStyle = computed(() => {
  const base = {
    backgroundImage: `linear-gradient(${props.spread}deg, ${props.color} 0%, ${props.color} 35%, ${props.shineColor} 50%, ${props.color} 65%, ${props.color} 100%)`,
    backgroundSize: '200% auto',
    WebkitBackgroundClip: 'text',
    backgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
  } as Record<string, string | number>;
  if (props.disabled) return base;
  return {
    ...base,
    backgroundPosition: props.direction === 'right' ? '-50% center' : '150% center',
    animationName: props.direction === 'right' ? 'j-shiny-sweep-right' : 'j-shiny-sweep-left',
    animationDuration: `${props.speed}s`,
    animationDelay: `${props.delay}s`,
    animationTimingFunction: 'linear',
    animationIterationCount: 'infinite',
    animationDirection: props.yoyo ? 'alternate' : 'normal',
  };
});
</script>

<template>
  <span
    :class="['inline-block', 'j-shiny-text', className, pauseOnHover && !disabled ? 'j-shiny-pause' : '']"
    :style="wrapperStyle"
  >
    {{ text }}
  </span>
</template>
