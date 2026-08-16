<template>
  <div
    ref="magnetRef"
    :class="wrapperClassName"
    :style="{ position: 'relative', display: 'inline-block' }"
    v-bind="$attrs"
  >
    <div
      :class="innerClassName"
      :style="{
        transform: `translate3d(${position.x}px, ${position.y}px, 0)`,
        transition: transitionStyle,
        willChange: 'transform'
      }"
    >
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onBeforeUnmount, watch, useTemplateRef } from 'vue';

interface Props {
  padding?: number;
  disabled?: boolean;
  magnetStrength?: number;
  activeTransition?: string;
  inactiveTransition?: string;
  wrapperClassName?: string;
  innerClassName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  padding: 100,
  disabled: false,
  magnetStrength: 2,
  activeTransition: 'transform 0.3s ease-out',
  inactiveTransition: 'transform 0.5s ease-in-out',
  wrapperClassName: '',
  innerClassName: ''
});

defineOptions({
  inheritAttrs: false
});

const magnetRef = useTemplateRef<HTMLDivElement>('magnetRef');
const isActive = ref(false);
const position = ref({ x: 0, y: 0 });

const transitionStyle = computed(() => (isActive.value ? props.activeTransition : props.inactiveTransition));

/**
 * 性能优化：把高频 mousemove 合并到 rAF 里处理，避免每个鼠标事件都调用
 * getBoundingClientRect()。这在卡片/列表很多时能明显降低主线程压力。
 */
let raf = 0;
let pendingEvent: MouseEvent | null = null;

const processMove = () => {
  raf = 0;
  const e = pendingEvent;
  pendingEvent = null;
  if (!e || !magnetRef.value || props.disabled) return;

  const { left, top, width, height } = magnetRef.value.getBoundingClientRect();
  const centerX = left + width / 2;
  const centerY = top + height / 2;

  const distX = Math.abs(centerX - e.clientX);
  const distY = Math.abs(centerY - e.clientY);

  if (distX < width / 2 + props.padding && distY < height / 2 + props.padding) {
    isActive.value = true;
    const offsetX = (e.clientX - centerX) / props.magnetStrength;
    const offsetY = (e.clientY - centerY) / props.magnetStrength;
    position.value = { x: offsetX, y: offsetY };
  } else {
    isActive.value = false;
    position.value = { x: 0, y: 0 };
  }
};

const handleMouseMove = (e: MouseEvent) => {
  pendingEvent = e;
  if (!raf) raf = requestAnimationFrame(processMove);
};

const reset = () => {
  pendingEvent = null;
  if (raf) {
    cancelAnimationFrame(raf);
    raf = 0;
  }
  position.value = { x: 0, y: 0 };
  isActive.value = false;
};

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove);
});

onBeforeUnmount(reset);

watch(
  () => props.disabled,
  newDisabled => {
    if (newDisabled) reset();
  }
);
</script>
