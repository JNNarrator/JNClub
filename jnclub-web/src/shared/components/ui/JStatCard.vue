<script setup lang="ts">
/**
 * JStatCard.vue — 概览统计卡片基座
 * 统一数字统计卡：图标、数值（JCountUp）、标签、警告态。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import JCountUp from '../animation/JCountUp.vue'

const props = withDefaults(defineProps<{
  label: string
  value: number
  icon: any
  warn?: boolean
}>(), {
  warn: false,
})

/**
 * 数值字号按位数降档（技术债②）。
 * 背景：1440px 视口下 `.stat-grid` 为 6 列，单卡 169.3px，
 * 扣掉 `padding 16px 18px`×2 + icon 38px + `gap 14px` 后
 * `.j-stat-text` 可用宽度只有 81.3px；`.j-stat-value` 为 `white-space: nowrap`，
 * 实测 24px 下 6 位数字 `123456` 的 scrollWidth 82 已越界 1px。
 * 而统计数字是**会随使用增长的量**（收藏数、便签数持续累积），
 * 全局提到 32px 的做法实测连 4 位都放不下（见报告 11.1），
 * 因此采用「位数越多、字号越小」的降档：层级在小数字上保持不变，
 * 只有真正变长时才让出字号，换位数余量。
 */
const valueSizeClass = computed(() => {
  const digits = String(Math.trunc(props.value)).length
  if (digits >= 8) return 'is-xlong'  // 18px
  if (digits === 7) return 'is-long'  // 20px
  if (digits === 6) return 'is-mid'   // 22px
  return ''
})

const emit = defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    type="button"
    class="j-stat-card jnclub-bouncy"
    :class="{ 'j-stat-warn': warn }"
    @click="emit('click')"
  >
    <div class="j-stat-icon"><NIcon :component="icon" size="20" /></div>
    <div class="j-stat-text">
      <div class="j-stat-value" :class="valueSizeClass"><JCountUp :to="value" /></div>
      <div class="j-stat-label">{{ label }}<span v-if="warn" class="j-stat-warn-dot" /></div>
    </div>
  </button>
</template>

<style scoped>
.j-stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 96px;
  padding: 16px 18px;
  /* L1 结构面：实色 + 中性描边 + 轻阴影（命中反判据 N3「平铺卡片」）。
     原为玻璃 + 粉色描边 + 双重阴影。 */
  background: var(--surface-1);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
  text-align: left;
  font-family: inherit;
}
/* hover 统一到 L2 语言：描边加深一档 + 卡片阴影 + 抬升 1px。
   2026-10 起不再变粉（粉边框配额让位给「激活/选中」态）。 */
.j-stat-card:hover {
  border-color: var(--surface-border-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-1px);
}
.j-stat-card.j-stat-warn {
  border-color: color-mix(in srgb, var(--danger) 45%, transparent);
}
.j-stat-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.j-stat-icon {
  width: 38px; height: 38px; border-radius: var(--radius-sm);
  flex-shrink: 0;
  background: var(--brand-soft); color: var(--brand-text);
  display: flex; align-items: center; justify-content: center;
}
.j-stat-warn .j-stat-icon {
  background: var(--danger-soft);
  color: var(--danger-text);
}
.j-stat-value {
  font-size: var(--fs-5xl); font-weight: 800; color: var(--text-1); line-height: 1.1;
  white-space: nowrap;
}
/* 位数降档（见 script 中 valueSizeClass 的说明）：
   6 位 24px 实测溢出 1px，逐档让出 2px 字号；8 位起按 18px 封底，
   再长则依赖 `.j-stat-text` 的 min-width:0 与父级 overflow 裁切。 */
.j-stat-value.is-mid { font-size: var(--fs-4xl); }   /* 22px */
.j-stat-value.is-long { font-size: var(--fs-3xl); }  /* 20px */
.j-stat-value.is-xlong { font-size: var(--fs-2xl); } /* 18px */
.j-stat-label {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--fs-sm); color: var(--glass-text-secondary);
}
.j-stat-warn-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--danger);
  animation: j-stat-blink var(--dur-loop) infinite;
}
@keyframes j-stat-blink {
  50% { opacity: 0.3; }
}

@media (max-width: 767px) {
  .j-stat-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    min-height: 104px;
    padding: 14px;
  }
  .j-stat-icon { width: 32px; height: 32px; border-radius: var(--radius-sm); }
  .j-stat-value { font-size: var(--fs-4xl); }
}

.j-stat-card:active {
  transform: scale(0.985);
}
.j-stat-card:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: none;
}
</style>
