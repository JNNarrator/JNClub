<script setup lang="ts">
/**
 * JEmptyState.vue — 全局统一空状态（玻璃卡片版）
 * 飘浮花瓣 + 玻璃渐变卡底 + 品牌色环形图标 + CTA
 * variant 区分三场景：empty 列表为空 / search 搜索无结果 / error 错误重试
 *
 * M4-1：原位于 modules/bookmark/components/EmptyState.vue，被 shared 层反向依赖
 * （JEmptyState 只是它的透传包装，而 VaultView/DiskView 又直接引它）。
 * 二者 props 完全一致，故直接上移为 shared/ui 的实现本体，删除包装层。
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 场景：列表为空 / 搜索无结果 / 错误重试 */
  variant?: 'empty' | 'search' | 'error'
  message?: string
  hint?: string
  /** 图标类型：bookmark / note / file / vault / search / error */
  icon?: 'bookmark' | 'note' | 'file' | 'vault' | 'search' | 'error'
  ctaLabel?: string
  retryLabel?: string
  /** 是否显示底部 CTA 按钮；错误场景下自动隐藏，仅保留重试 */
  showCta?: boolean
  /** 错误场景下是否显示“重试”按钮；默认显示 */
  showRetry?: boolean
}>(), {
  variant: 'empty',
  showCta: true,
  showRetry: true,
})

const emit = defineEmits<{
  create: []
  retry: []
}>()

/** 各场景默认文案；调用方可传 message / hint 覆盖 */
const variantText = computed(() => {
  if (props.variant === 'search') {
    return { message: '搜索无结果', hint: '换个关键词或筛选条件再试试吧' }
  }
  if (props.variant === 'error') {
    return { message: '出错了', hint: '加载数据失败，请检查网络后重试' }
  }
  return { message: '这里空空如也～', hint: '里面还没有内容，去添加一些吧 ✨' }
})

const resolvedIcon = computed(() => (
  props.icon ?? (props.variant === 'search' ? 'search' : props.variant === 'error' ? 'error' : 'bookmark')
))
</script>

<template>
  <div class="empty-state-card">
    <!-- 飘浮花瓣 -->
    <span class="petal petal-1"></span>
    <span class="petal petal-2"></span>
    <span class="petal petal-3"></span>
    <span class="petal petal-4"></span>

    <div class="empty-content">
      <!-- 品牌色环形图标 -->
      <div class="empty-icon-ring">
        <svg v-if="resolvedIcon === 'bookmark'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="6" width="32" height="36" rx="4" stroke="currentColor" stroke-width="2" />
          <line x1="14" y1="16" x2="34" y2="16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="14" y1="23" x2="28" y2="23" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="14" y1="30" x2="22" y2="30" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <circle cx="38" cy="10" r="3" fill="currentColor" opacity="0.7" />
        </svg>
        <svg v-else-if="resolvedIcon === 'note'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="10" y="6" width="28" height="36" rx="3" stroke="currentColor" stroke-width="2" />
          <line x1="16" y1="16" x2="32" y2="16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="16" y1="22" x2="28" y2="22" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="16" y1="28" x2="24" y2="28" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <line x1="16" y1="34" x2="20" y2="34" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".3" />
          <circle cx="34" cy="12" r="2.5" fill="currentColor" opacity="0.7" />
        </svg>
        <svg v-else-if="resolvedIcon === 'file'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M10 8h17l9 9v22a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z" stroke="currentColor" stroke-width="2" />
          <path d="M26 8v10h10" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          <rect x="18" y="23" width="12" height="8" rx="1.5" stroke="currentColor" stroke-width="1.5" opacity=".7" />
          <circle cx="38" cy="8" r="3" fill="currentColor" opacity="0.7" />
        </svg>
        <svg v-else-if="resolvedIcon === 'vault'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M24 6l14 6v10c0 8-5.5 14-14 17-8.5-3-14-9-14-17V12l14-6z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          <circle cx="24" cy="21" r="4.5" stroke="currentColor" stroke-width="1.8" />
          <path d="M24 25.5V31" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          <circle cx="39" cy="8" r="3" fill="currentColor" opacity="0.7" />
        </svg>
        <svg v-else-if="resolvedIcon === 'search'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="21" cy="21" r="10" stroke="currentColor" stroke-width="2.5" />
          <line x1="28.5" y1="28.5" x2="38" y2="38" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="39" cy="9" r="3" fill="currentColor" opacity="0.7" />
        </svg>
        <svg v-else-if="resolvedIcon === 'error'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="24" cy="24" r="17" stroke="currentColor" stroke-width="2.5" />
          <line x1="24" y1="16" x2="24" y2="27" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
          <circle cx="24" cy="33.5" r="1.8" fill="currentColor" />
          <circle cx="39" cy="8" r="3" fill="currentColor" opacity="0.7" />
        </svg>
        <svg v-else width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="6" width="32" height="36" rx="4" stroke="currentColor" stroke-width="2" />
          <line x1="14" y1="16" x2="34" y2="16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="14" y1="23" x2="28" y2="23" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="14" y1="30" x2="22" y2="30" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <circle cx="38" cy="10" r="3" fill="currentColor" opacity="0.7" />
        </svg>
      </div>

      <h3 class="empty-title">{{ message || variantText.message }}</h3>
      <p class="empty-sub">{{ hint || variantText.hint }}</p>

      <button v-if="showCta && variant !== 'error'" class="empty-cta glass-pill-btn jnclub-bouncy-slow" @click="emit('create')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {{ ctaLabel || '添加' }}
      </button>

      <button v-if="showRetry && variant === 'error'" class="empty-cta empty-cta-ghost" @click="emit('retry')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 12a9 9 0 1 1-2.64-6.36" />
          <path d="M21 3v6h-6" />
        </svg>
        {{ retryLabel || '重试' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.empty-state-card {
  position: relative;
  overflow: hidden;
  /* L1 结构面：实色 + 中性描边 + 轻阴影。
     2026-10 起删除径向粉光（P2-1）与 blur —— 空态是「平铺卡片」，命中反判据 N3。 */
  background: var(--surface-1);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  padding: 64px 20px 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: var(--shadow-1);
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
}

.empty-icon-ring {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--gradient-btn);
  border: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: center;
  /* 图标描边统一走 currentColor，颜色由此处给 —— SVG 的 presentation attribute
     不认 var()，只能靠继承，所以不写成 stroke="var(--…)"。 */
  color: var(--icon-on-brand);
  /* 外发光用柔光档：--focus-ring 自 M1 起是实色，直接当 24px 光晕会糊成一圈实心粉 */
  box-shadow: 0 0 24px var(--focus-ring-soft), var(--shadow-2);
  margin-bottom: 24px;
  animation: jnclub-breathe var(--dur-breathe) var(--ease-smooth) infinite;
}

.empty-title {
  font-size: var(--fs-2xl);
  font-weight: 700;
  color: var(--brand-text);
  margin: 0 0 8px;
  letter-spacing: 0.5px;
}

.empty-sub {
  font-size: var(--fs-md);
  color: var(--text-3);
  max-width: 280px;
  line-height: 1.7;
  margin: 0 0 28px;
}

.empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-base);
  padding: 11px 28px;
  cursor: pointer;
}

.empty-cta-ghost {
  background: var(--glass-chip-bg);
  border: 1px solid var(--surface-border);
  color: var(--brand-text);
  /* 与同组件的 .empty-cta.glass-pill-btn 同为「空态 CTA」，圆角必须同档。
     原为 --radius-md(16px)，与 pill 的 999px 差两档；两者虽由 variant 互斥、
     不同屏出现，但同一组件内的主按钮不应有两种形状（P2-3）。 */
  border-radius: var(--radius-pill);
  transition: border-color var(--dur) var(--ease), background var(--dur) var(--ease);
}

.empty-cta-ghost:hover {
  border-color: var(--brand-text);
  background: var(--brand-soft);
}
</style>