<script setup lang="ts">
/**
 * JErrorState.vue — 全局统一错误占位
 * 用于请求失败场景：错误图标 + 文案 + 重试按钮。
 */
import { NIcon, NButton } from 'naive-ui'
import { AlertTriangle, RefreshCw } from 'lucide-vue-next'

withDefaults(defineProps<{
  message?: string
  hint?: string
  ctaLabel?: string
}>(), {
  message: '加载失败',
  hint: '请检查网络后重试',
  ctaLabel: '重试',
})

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="j-error-state glass-card">
    <div class="j-error-icon">
      <NIcon :component="AlertTriangle" size="30" />
    </div>
    <div class="j-error-title">{{ message }}</div>
    <p v-if="hint" class="j-error-hint">{{ hint }}</p>
    <NButton class="j-error-btn" @click="emit('retry')">
      <template #icon><NIcon :component="RefreshCw" size="14" /></template>
      {{ ctaLabel }}
    </NButton>
  </div>
</template>

<style scoped>
.j-error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 24px;
  text-align: center;
  border-radius: var(--radius-md);
  min-height: 220px;
}
.j-error-icon {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--danger-soft);
  color: var(--danger-text);
}
.j-error-title {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
}
.j-error-hint {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.j-error-btn {
  margin-top: 6px;
}
</style>
