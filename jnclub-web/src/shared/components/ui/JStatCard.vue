<script setup lang="ts">
/**
 * JStatCard.vue — 概览统计卡片基座
 * 统一数字统计卡：图标、数值（JCountUp）、标签、警告态。
 */
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import JCountUp from '../animation/JCountUp.vue'

/**
 * 图标色调：承载「模块身份」。
 * - bookmark / note / file / vault → 模块色 soft 底 + --module-*-icon 描边色
 * - neutral → 中性档（--hover-bg 底 + --text-3），用于没有对应模块色的统计项
 *   （标签 / 回收站，见 DESIGN §11.2 N6）
 * ⚠ 图标盒背景**不是** --module-*-icon 本身：那会变成大面积模块色填充，
 *   违反 §3.5 的 3% 面积上限。soft 底（12% 混入 L1）视为中性面，不占配额。
 */
type StatTone = 'bookmark' | 'note' | 'file' | 'vault' | 'neutral'

const props = withDefaults(defineProps<{
  label: string
  value: number
  icon: any
  warn?: boolean
  tone?: StatTone
}>(), {
  warn: false,
  tone: 'neutral',
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
    :class="[`tone-${props.tone}`, { 'j-stat-warn': warn }]"
    @click="emit('click')"
  >
    <div class="j-stat-icon"><NIcon :component="icon" size="18" /></div>
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
/* 图标盒 38 → 32px：单卡内它是「身份标记」而非主视觉，
   缩小 6px 同时给 6 位数字让出横向空间（技术债②的降档逻辑不因此回归）。 */
.j-stat-icon {
  width: 32px; height: 32px; border-radius: var(--radius-sm);
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}
/* 模块色 soft 底 + 模块图标色（唯一允许的模块色用法：图标 + soft 底） */
.j-stat-card.tone-bookmark .j-stat-icon {
  background: color-mix(in srgb, var(--module-bookmark) 12%, var(--surface-1));
  color: var(--module-bookmark-icon);
}
.j-stat-card.tone-note .j-stat-icon {
  background: color-mix(in srgb, var(--module-note) 12%, var(--surface-1));
  color: var(--module-note-icon);
}
.j-stat-card.tone-file .j-stat-icon {
  background: color-mix(in srgb, var(--module-file) 12%, var(--surface-1));
  color: var(--module-file-icon);
}
.j-stat-card.tone-vault .j-stat-icon {
  background: color-mix(in srgb, var(--module-vault) 12%, var(--surface-1));
  color: var(--module-vault-icon);
}
.j-stat-card.tone-neutral .j-stat-icon {
  background: var(--hover-bg);
  color: var(--text-3);
}
/* 警告态（回收站有内容）优先于 tone：用语义色 --danger，不属品牌粉 */
.j-stat-warn .j-stat-icon {
  background: var(--danger-soft);
  color: var(--danger-text);
}
.j-stat-value {
  /* 字重 800 → 700：800 在 DM Sans 下笔画过厚，6 位数字会糊成一块；
     700 仍明显重于正文（正文 400/600），层级不变而可读性更好。
     字号档位（24px）不动 —— decisions-ia.md 决策 6 已闭环。 */
  font-size: var(--fs-5xl); font-weight: 700; color: var(--text-1); line-height: 1.1;
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
  /* 标签 12 → 13px，颜色由 --glass-text-secondary（65% 黑）改为 --text-2 实色：
     12px 是「辅助文字」档，13px 才是「可读标签」档；实色保证暗色下不发灰。 */
  font-size: var(--fs-md); color: var(--text-2);
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
  /* 移动端图标盒与桌面同档（32px），不再单独放大 */
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
