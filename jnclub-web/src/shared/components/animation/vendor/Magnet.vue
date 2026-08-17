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
 * 性能优化：缓存 getBoundingClientRect，帧内不再读 DOM。
 * 在挂载 / resize / scroll 时刷新，避免高频 mousemove 里触发布局读取
 * （这是 Windows 高 DPI/高刷下指针不跟手的主要来源之一）。
 */
let rect = { left: 0, top: 0, width: 0, height: 0 };
let rectFresh = false;
let ro: ResizeObserver | null = null;
let scrollRaf = 0;

const refreshRect = () => {
  const el = magnetRef.value;
  if (!el) return;
  const r = el.getBoundingClientRect();
  rect = { left: r.left, top: r.top, width: r.width, height: r.height };
  rectFresh = true;
};

// rAF 合并 scroll/resize 刷新，避免高频事件重复读取
const scheduleRectRefresh = () => {
  if (scrollRaf) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0;
    refreshRect();
  });
};

let raf = 0;
let pendingEvent: MouseEvent | null = null;

const processMove = () => {
  raf = 0;
  const e = pendingEvent;
  pendingEvent = null;
  if (!e || props.disabled) return;
  if (!rectFresh) refreshRect();

  const centerX = rect.left + rect.width / 2;
  const centerY = rect.top + rect.height / 2;

  const distX = Math.abs(centerX - e.clientX);
  const distY = Math.abs(centerY - e.clientY);

  if (distX < rect.width / 2 + props.padding && distY < rect.height / 2 + props.padding) {
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
  refreshRect();
  window.addEventListener('mousemove', handleMouseMove, { passive: true });
  window.addEventListener('resize', scheduleRectRefresh, { passive: true });
  window.addEventListener('scroll', scheduleRectRefresh, { passive: true, capture: true });
  if (typeof ResizeObserver !== 'undefined') {
    ro = new ResizeObserver(scheduleRectRefresh);
    if (magnetRef.value) ro.observe(magnetRef.value);
  }
});

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove);
  window.removeEventListener('resize', scheduleRectRefresh);
  window.removeEventListener('scroll', scheduleRectRefresh, { capture: true } as EventListenerOptions);
  ro?.disconnect();
  ro = null;
});

onBeforeUnmount(reset);

watch(
  () => props.disabled,
  newDisabled => {
    if (newDisabled) reset();
  }
);
</script>
