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
        @click="emit('refresh')"
      >
        <NIcon :component="RefreshCw" size="16" />
      </button>
      <button
        v-if="theme"
        type="button"
        class="j-page-header-btn jnclub-bouncy"
        title="切换暗色模式"
        @click="emit('toggle-theme')"
      >
        <NIcon :component="isDark ? Sun : Moon" size="16" />
      </button>
      <button
        v-if="back"
        type="button"
        class="j-page-header-btn jnclub-bouncy"
        title="返回"
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
  padding: 4px 2px 14px;
}
.j-page-header-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.j-page-header-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-1);
  line-height: 1.3;
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
    gap: 10px;
  }
  .j-page-header-actions {
    width: 100%;
    overflow-x: auto;
  }
}
</style>
