<script setup lang="ts">
/**
 * CursorClickParticles.vue — 点击粒子特效渲染
 * 挂载在 App.vue，与 CursorHost 同级（z-index:11000, pointer-events:none）。
 * 遍历 useCursorClickEffect 返回的粒子数组，用 CSS 绘制三种形状（star/heart/flower）。
 * 每粒子仅用 translate3d + opacity，不触发重排。
 */
import { useCustomCursor } from '../composables/useCustomCursor'
import { useCursorClickEffect } from '../composables/useCursorClickEffect'

const cursor = useCustomCursor()
const { particles } = useCursorClickEffect()
</script>

<template>
  <div
    v-if="cursor.enabled.value && particles.length > 0"
    class="cursor-click-particles"
    aria-hidden="true"
  >
    <div
      v-for="(p, i) in particles"
      :key="i"
      class="particle"
      :class="`particle-${p.type}`"
      :style="{
        transform: `translate3d(${p.x}px, ${p.y}px, 0) rotate(${p.rotation}deg)`,
        opacity: Math.max(0, 1 - p.life),
        width: p.size + 'px',
        height: p.size + 'px',
        marginTop: -(p.size / 2) + 'px',
        marginLeft: -(p.size / 2) + 'px',
        '--particle-color': p.color,
      }"
    />
  </div>
</template>

<style scoped>
.cursor-click-particles {
  position: fixed;
  inset: 0;
  z-index: 11000;
  pointer-events: none;
}

.particle {
  position: absolute;
  top: 0;
  left: 0;
  will-change: transform, opacity;
}

/* 五角星：clip-path 裁切 */
.particle-star {
  background: var(--particle-color, #FFD700);
  clip-path: polygon(
    50% 0%, 61% 35%, 98% 35%, 68% 57%,
    79% 91%, 50% 70%, 21% 91%, 32% 57%,
    2% 35%, 39% 35%
  );
  filter: drop-shadow(0 0 4px var(--particle-color));
}

/* 爱心：用伪元素模拟 */
.particle-heart {
  background: none;
  color: var(--particle-color, #FF6B8A);
}
.particle-heart::before,
.particle-heart::after {
  content: '';
  position: absolute;
  top: 0;
  width: 50%;
  height: 80%;
  border-radius: 50% 50% 0 0;
  background: var(--particle-color, #FF6B8A);
}
.particle-heart::before {
  left: 0;
  transform: rotate(-45deg);
  transform-origin: bottom right;
}
.particle-heart::after {
  right: 0;
  transform: rotate(45deg);
  transform-origin: bottom left;
}

/* 花朵：圆形 + box-shadow 多重投影 */
.particle-flower {
  background: var(--particle-color, #FFB7D5);
  border-radius: 50%;
  box-shadow:
    calc(var(--particle-size, 14px) * 0.5) 0 0 -1px var(--particle-color),
    calc(var(--particle-size, 14px) * -0.5) 0 0 -1px var(--particle-color),
    0 calc(var(--particle-size, 14px) * 0.5) 0 -1px var(--particle-color),
    0 calc(var(--particle-size, 14px) * -0.5) 0 -1px var(--particle-color);
}
</style>
