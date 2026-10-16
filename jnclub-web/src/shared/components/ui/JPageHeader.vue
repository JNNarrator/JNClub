<script setup lang="ts">
/**
 * JPageHeader.vue — 全局页面头
 * 统一页面标题 / 副标题 / 右侧操作区，替代各 Layout 里重复的面包屑/标题结构。
 * 内置可选：刷新 / 主题切换 / 返回；也可通过 actions 插槽放自定义操作。
 */
import { NIcon } from 'naive-ui'
import { RefreshCw, Sun, Moon, ArrowLeft } from 'lucide-vue-next'

withDefaults(defineProps<{
  title: string
  subtitle?: string
  refresh?: boolean
  theme?: boolean
  isDark?: boolean
  back?: boolean
}>(), {
  subtitle: '',
  refresh: false,
  theme: false,
  isDark: false,
  back: false,
})

const emit = defineEmits<{
  refresh: []
  'toggle-theme': []
  back: []
}>()
</script>

<template>
  <header class="j-page-header">
    <div class="j-page-header-main">
      <h1 class="j-page-header-title">{{ title }}</h1>
      <p v-if="subtitle" class="j-page-header-subtitle">{{ subtitle }}</p>
    </div>
    <div class="j-page-header-actions">
      <slot name="actions" />
      <button
        v-if="refresh"
        type="button"
        class="j-page-header-btn jnclub-bouncy"
        title="刷新"
        :aria-label="'刷新'"
        @click="emit('refresh')"
      >
        <NIcon :component="RefreshCw" size="16" />
      </button>
      <button
        v-if="theme"
        type="button"
        class="j-page-header-btn jnclub-bouncy"
        title="切换暗色模式"
        :aria-label="'切换暗色模式'"
        @click="emit('toggle-theme')"
      >
        <NIcon :component="isDark ? Sun : Moon" size="16" />
      </button>
      <button
        v-if="back"
        type="button"
        class="j-page-header-btn jnclub-bouncy"
        title="返回"
        :aria-label="'返回'"
        @click="emit('back')"
      >
        <NIcon :component="ArrowLeft" size="16" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.j-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  /* 与主壳 AppHeader 对齐：同一 --header-height 的顶栏条 + 同一条底部通栏分隔线。
     负 margin 抵消 JPageShell 的水平 padding，让分隔线通栏；
     --shell-gutter 由 JPageShell 在移动端注入，缺失时回退到页面 gutter。 */
  height: var(--header-height, 60px);
  padding: 0 var(--shell-gutter, var(--layout-page-gutter, 24px));
  margin: 0 calc(-1 * var(--shell-gutter, var(--layout-page-gutter, 24px)));
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.j-page-header-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.j-page-header-title {
  margin: 0;
  /* 原为 --fs-3xl(20px)/800 的落地页大标题；收到顶栏语境（面包屑条）同量级 */
  font-size: var(--fs-2xl);
  font-weight: 700;
  color: var(--text-1);
  line-height: 1.25;
}
.j-page-header-subtitle {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.j-page-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.j-page-header-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
}
.j-page-header-btn:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}

@media (max-width: 767px) {
  .j-page-header {
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 8px;
    /* 标题 + 副标题两行时允许撑高，但不得低于主壳顶栏高度 */
    height: auto;
    min-height: var(--header-height, 52px);
    padding: 8px var(--shell-gutter, var(--layout-page-gutter, 24px));
  }
  .j-page-header-actions {
    width: 100%;
    overflow-x: auto;
  }
}

/* 三态：原先只有 :hover（原生 button 在触屏上无 hover，需要 active 反馈） */
.j-page-header-btn:active {
  transform: scale(0.94);
}
.j-page-header-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: none;
}
</style>
