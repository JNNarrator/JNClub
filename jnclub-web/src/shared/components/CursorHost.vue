<script setup lang="ts">
/**
 * CursorHost.vue — 全局自定义光标宿主（可爱光标，挂在 App.vue，全路由生效）
 * 统一管理一个 rAF 循环，合并光环追赶 + 轨迹衰减 + 点击粒子物理。
 * 触屏(pointer:coarse)不渲染；prefers-reduced-motion 时禁弹性直接落位；输入框内隐藏露出 I-beam。
 * 启用时给 <html> 加 .custom-cursor-active → 全局 cursor:none（规则见 main.css）。
 */
import { computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useCustomCursor } from '../composables/useCustomCursor'
import { useCursorTrailEffect } from '../composables/useCursorTrailEffect'
import { useCursorClickEffect } from '../composables/useCursorClickEffect'

const cursor = useCustomCursor()
const { points, updateTrail } = useCursorTrailEffect()
const { particles, updateClickParticles } = useCursorClickEffect()

/** 光环追赶速率：越大越跟手（时间无关阻尼，与刷新率解耦，60/30fps 手感一致） */
const SMOOTHING = 30
/** rAF 句柄 */
let raf = 0
/** 上一帧时间戳，用于计算 dt 做时间无关阻尼 */
let lastT = 0

const loop = (t: number) => {
  // 未启用自定义光标时彻底停掉 rAF，避免后台空转（Step C 性能优化）
  if (!cursor.enabled.value) {
    raf = 0
    return
  }
  // 限制 dt 上限，避免切后台回前台时产生一次大幅跳变
  const dt = lastT ? Math.min((t - lastT) / 1000, 0.05) : 1 / 60
  lastT = t

  // 1. 光环缓动追赶
  if (cursor.reducedMotion.value) {
    cursor.haloX.value = cursor.x.value
    cursor.haloY.value = cursor.y.value
  } else {
    const k = 1 - Math.exp(-SMOOTHING * dt)
    cursor.haloX.value += (cursor.x.value - cursor.haloX.value) * k
    cursor.haloY.value += (cursor.y.value - cursor.haloY.value) * k
  }

  // 2. 轨迹点衰减（仅当有轨迹特效且有点时）
  if (cursor.trailEffect.value !== 'none' && points.value.length > 0) {
    updateTrail(dt)
  }

  // 3. 点击粒子物理（仅当有粒子时）
  if (particles.value.length > 0) {
    updateClickParticles(dt)
  }

  raf = requestAnimationFrame(loop)
}

/** 启停 rAF：禁用时彻底停止，启用时重新拉起（避免后台空转） */
function startLoop() {
  if (!cursor.enabled.value || raf) return
  lastT = 0
  raf = requestAnimationFrame(loop)
}
function stopLoop() {
  if (raf) {
    cancelAnimationFrame(raf)
    raf = 0
  }
}

/** emoji 模式文案：按下/悬停/默认 */
const emoji = computed(() => {
  if (cursor.pressed.value) return '✨'
  if (cursor.hovering.value) return '💗'
  return '🐾'
})

/** 十字准心样式：中心点即时 + 十字线跟随 halo 缓动 */
const crosshairStyle = computed(() => ({
  transform: `translate3d(${cursor.haloX.value}px, ${cursor.haloY.value}px, 0)`,
}))
const crosshairDotStyle = computed(() => ({
  transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)`,
}))

/** 圆环样式：外环 lerp + 内环即时 */
const ringOuterStyle = computed(() => ({
  transform: `translate3d(${cursor.haloX.value}px, ${cursor.haloY.value}px, 0)`,
}))
const ringInnerStyle = computed(() => ({
  transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)`,
}))

/** 星星样式 */
const starStyle = computed(() => ({
  transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)`,
}))

const syncCursorClass = () => {
  const root = document.documentElement
  if (cursor.enabled.value) {
    root.classList.add('custom-cursor-active')
  } else {
    root.classList.remove('custom-cursor-active')
  }
}

watch(() => cursor.enabled.value, (v) => {
  syncCursorClass()
  if (v) startLoop()
})

onMounted(() => {
  cursor.init()
  syncCursorClass()
  startLoop()
})

onBeforeUnmount(() => {
  stopLoop()
  document.documentElement.classList.remove('custom-cursor-active')
  cursor.destroy()
})
</script>

<template>
  <div
    v-if="cursor.enabled.value && cursor.visible.value && !cursor.inInput.value"
    class="cursor-host"
    aria-hidden="true"
  >
    <!-- 风格一：圆点 + 光环 -->
    <template v-if="cursor.style.value === 'dot-halo'">
      <div
        class="cursor-dot"
        :class="{ pressed: cursor.pressed.value }"
        :style="{ transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)` }"
      />
      <div
        class="cursor-halo"
        :class="{ hovering: cursor.hovering.value, pressed: cursor.pressed.value }"
        :style="{ transform: `translate3d(${cursor.haloX.value}px, ${cursor.haloY.value}px, 0)` }"
      />
    </template>
    <!-- 风格二：emoji 跟随 -->
    <div
      v-else-if="cursor.style.value === 'emoji'"
      class="cursor-emoji"
      :style="{ transform: `translate3d(${cursor.x.value}px, ${cursor.y.value}px, 0)` }"
    >
      <span class="cursor-emoji-inner" :class="{ hovering: cursor.hovering.value, pressed: cursor.pressed.value }">{{ emoji }}</span>
    </div>
    <!-- 风格三：十字准心 -->
    <template v-else-if="cursor.style.value === 'crosshair'">
      <div class="cursor-crosshair" :class="{ pressed: cursor.pressed.value }" :style="crosshairStyle">
        <div class="crosshair-line crosshair-h" />
        <div class="crosshair-line crosshair-v" />
      </div>
      <div
        class="cursor-dot"
        :class="{ pressed: cursor.pressed.value }"
        :style="crosshairDotStyle"
      />
    </template>
    <!-- 风格四：双层圆环 -->
    <template v-else-if="cursor.style.value === 'ring'">
      <div
        class="cursor-ring-outer"
        :class="{ hovering: cursor.hovering.value, pressed: cursor.pressed.value }"
        :style="ringOuterStyle"
      />
      <div
        class="cursor-ring-inner"
        :class="{ pressed: cursor.pressed.value }"
        :style="ringInnerStyle"
      />
    </template>
    <!-- 风格五：星星光标 -->
    <div
      v-else
      class="cursor-star"
      :style="starStyle"
    >
      <span
        class="cursor-star-icon"
        :class="{ hovering: cursor.hovering.value, pressed: cursor.pressed.value }"
      >⭐</span>
    </div>
  </div>
</template>

<style scoped>
.cursor-host {
  position: fixed;
  inset: 0;
  z-index: 11000;
  pointer-events: none;
}

/* 主圆点：12px，即时跟随（translate3d 合成层，不触发重排） */
.cursor-dot {
  position: absolute;
  top: 0;
  left: 0;
  width: 12px;
  height: 12px;
  margin: -6px 0 0 -6px;
  border-radius: 50%;
  background: var(--brand);
  box-shadow: 0 0 8px color-mix(in srgb, var(--brand) 55%, transparent);
  transition: width var(--dur) var(--ease-bouncy), height var(--dur) var(--ease-bouncy),
    margin var(--dur) var(--ease-bouncy), background-color var(--dur) var(--ease);
  will-change: transform;
}
.cursor-dot.pressed {
  width: 8px;
  height: 8px;
  margin: -4px 0 0 -4px;
}

/* 光环：36px 透明环，lerp 延迟追赶形成弹性拖尾 */
.cursor-halo {
  position: absolute;
  top: 0;
  left: 0;
  width: 36px;
  height: 36px;
  margin: -18px 0 0 -18px;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--brand) 70%, transparent);
  background: color-mix(in srgb, var(--brand) 8%, transparent);
  transition: width var(--dur) var(--ease-bouncy), height var(--dur) var(--ease-bouncy),
    margin var(--dur) var(--ease-bouncy), border-color var(--dur) var(--ease),
    background-color var(--dur) var(--ease);
  will-change: transform;
}
.cursor-halo.hovering {
  width: 52px;
  height: 52px;
  margin: -26px 0 0 -26px;
  border-color: var(--brand);
  background: color-mix(in srgb, var(--brand) 14%, transparent);
}
.cursor-halo.pressed {
  width: 24px;
  height: 24px;
  margin: -12px 0 0 -12px;
  border-color: var(--brand);
  background: color-mix(in srgb, var(--brand) 20%, transparent);
}

/* emoji 光标 */
.cursor-emoji {
  position: absolute;
  top: 0;
  left: 0;
  width: 28px;
  height: 28px;
  margin: -14px 0 0 -14px;
  display: flex;
  align-items: center;
  justify-content: center;
  will-change: transform;
}
.cursor-emoji-inner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  line-height: 1;
  filter: drop-shadow(0 2px 6px color-mix(in srgb, var(--brand) 30%, transparent));
  transition: transform var(--dur) var(--ease-bouncy);
}
.cursor-emoji-inner.hovering { transform: scale(1.25); }
.cursor-emoji-inner.pressed { transform: scale(0.85); }

/* 十字准心 */
.cursor-crosshair {
  position: absolute;
  top: 0;
  left: 0;
  width: 28px;
  height: 28px;
  margin: -14px 0 0 -14px;
  will-change: transform;
}
.crosshair-line {
  position: absolute;
  background: var(--brand);
  border-radius: 1px;
  opacity: 0.8;
  transition: opacity var(--dur) var(--ease);
}
.crosshair-h {
  top: 50%;
  left: 0;
  width: 100%;
  height: 1.5px;
  margin-top: -0.75px;
}
.crosshair-v {
  left: 50%;
  top: 0;
  width: 1.5px;
  height: 100%;
  margin-left: -0.75px;
}
.cursor-crosshair.pressed .crosshair-line {
  opacity: 1;
}

/* 双层圆环 */
.cursor-ring-outer {
  position: absolute;
  top: 0;
  left: 0;
  width: 36px;
  height: 36px;
  margin: -18px 0 0 -18px;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--brand) 50%, transparent);
  background: transparent;
  transition: width var(--dur) var(--ease-bouncy), height var(--dur) var(--ease-bouncy),
    margin var(--dur) var(--ease-bouncy), border-color var(--dur) var(--ease);
  will-change: transform;
}
.cursor-ring-outer.hovering {
  width: 48px;
  height: 48px;
  margin: -24px 0 0 -24px;
  border-color: var(--brand);
}
.cursor-ring-outer.pressed {
  width: 28px;
  height: 28px;
  margin: -14px 0 0 -14px;
  border-color: var(--brand);
  border-width: 2px;
}
.cursor-ring-inner {
  position: absolute;
  top: 0;
  left: 0;
  width: 8px;
  height: 8px;
  margin: -4px 0 0 -4px;
  border-radius: 50%;
  background: var(--brand);
  box-shadow: 0 0 6px color-mix(in srgb, var(--brand) 50%, transparent);
  transition: width var(--dur) var(--ease-bouncy), height var(--dur) var(--ease-bouncy),
    margin var(--dur) var(--ease-bouncy);
  will-change: transform;
}
.cursor-ring-inner.pressed {
  width: 6px;
  height: 6px;
  margin: -3px 0 0 -3px;
}

/* 星星光标 */
.cursor-star {
  position: absolute;
  top: 0;
  left: 0;
  width: 28px;
  height: 28px;
  margin: -14px 0 0 -14px;
  display: flex;
  align-items: center;
  justify-content: center;
  will-change: transform;
}
.cursor-star-icon {
  display: inline-flex;
  font-size: 20px;
  line-height: 1;
  filter: drop-shadow(0 1px 4px color-mix(in srgb, var(--brand) 30%, transparent));
  transition: transform var(--dur) var(--ease-bouncy);
}
.cursor-star-icon.hovering {
  transform: scale(1.3) rotate(15deg);
}
.cursor-star-icon.pressed {
  transform: scale(0.8) rotate(-10deg);
}
</style>
