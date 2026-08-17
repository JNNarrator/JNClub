<script setup lang="ts">
import { computed, useSlots } from 'vue';

interface GradientTextProps {
  className?: string;
  colors?: string[];
  animationSpeed?: number;
  showBorder?: boolean;
  direction?: 'horizontal' | 'vertical' | 'diagonal';
  pauseOnHover?: boolean;
  yoyo?: boolean;
}

const props = withDefaults(defineProps<GradientTextProps>(), {
  className: '',
  colors: () => ['#27FF64', '#27FF64', '#A0FFBC'],
  animationSpeed: 8,
  showBorder: false,
  direction: 'horizontal',
  pauseOnHover: false,
  yoyo: true,
});

const slots = useSlots();
const text = computed(() => (slots.default?.() ?? []).map(v => v.children).join(''));

const isHorizontal = computed(() => props.direction !== 'vertical');

/** diagonal 也走水平移动，避免干涉条纹（与原先一致） */
const gradientAngle = computed(() =>
  props.direction === 'vertical' ? 'to bottom' : 'to right',
);
const gradientColors = computed(() => [...props.colors, props.colors[0]].join(', '));

/**
 * CSS 化实现（替代原 motion-v useAnimationFrame）：
 * 文字层/边框层共用一种 layerStyle，原生 @keyframes 驱动 background-position。
 */
const layerStyle = computed(() => ({
  backgroundImage: `linear-gradient(${gradientAngle.value}, ${gradientColors.value})`,
  backgroundSize: isHorizontal.value ? '300% 100%' : '100% 300%',
  backgroundRepeat: 'repeat',
  backgroundPosition: isHorizontal.value ? '0% 50%' : '50% 0%',
  animationName: isHorizontal.value ? 'j-grad-h' : 'j-grad-v',
  animationDuration: `${props.animationSpeed}s`,
  animationTimingFunction: 'linear',
  animationIterationCount: 'infinite',
  animationDirection: props.yoyo ? 'alternate' : 'normal',
}));

const pauseClass = computed(() => (props.pauseOnHover ? 'j-grad-pause' : ''));
</script>

<template>
  <div
    :class="[
      'relative mx-auto flex max-w-fit flex-row items-center justify-center rounded-[1.25rem] font-medium backdrop-blur transition-shadow duration-500 overflow-hidden cursor-pointer',
      'j-grad',
      className,
      pauseClass,
      showBorder && 'py-1 px-2',
    ]"
  >
    <div
      v-if="showBorder"
      class="z-0 absolute inset-0 rounded-[1.25rem] pointer-events-none"
      :style="layerStyle"
    >
      <div
        class="z-[-1] absolute bg-black rounded-[1.25rem]"
        :style="{
          width: 'calc(100% - 2px)',
          height: 'calc(100% - 2px)',
          left: '50%',
          top: '50%',
          transform: 'translate(-50%, -50%)',
        }"
      />
    </div>

    <div class="inline-block z-2 relative bg-clip-text text-transparent" :style="layerStyle">
      {{ text }}
    </div>
  </div>
</template>
