<script setup lang="ts">
/**
 * FloatingActions.vue — 右侧悬浮操作按钮
 * 品牌粉圆 FAB + 次级圆钮列
 * fixed + right 安全边距，z-index 防遮挡
 */
import { NButton, NIcon, NTooltip } from 'naive-ui'
import { AddOutline, OpenOutline } from '@vicons/ionicons5'

defineProps<{
  addLabel?: string
}>()

const emit = defineEmits<{
  add: []
  help: []
}>()
</script>

<template>
  <div class="floating-actions">
    <div class="secondary-actions">
      <NTooltip placement="left">
        <template #trigger>
          <NButton circle size="small" class="secondary-btn" @click="emit('help')">
            <template #icon>
              <NIcon :component="OpenOutline" size="16" />
            </template>
          </NButton>
        </template>
        帮助
      </NTooltip>
    </div>

    <NTooltip placement="left">
      <template #trigger>
        <NButton circle class="fab-primary" @click="emit('add')">
          <template #icon>
            <NIcon :component="AddOutline" size="22" />
          </template>
        </NButton>
      </template>
      {{ addLabel || '添加收藏' }}
    </NTooltip>
  </div>
</template>

<style scoped>
.floating-actions {
  position: fixed;
  right: 24px;
  bottom: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  z-index: 100;
  max-width: calc(100vw - 32px);
}
.secondary-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.secondary-btn {
  background: var(--bg-card) !important;
  border: 1px solid var(--border) !important;
  color: var(--text-2) !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: all var(--dur) var(--ease);
}
.secondary-btn:hover {
  color: var(--text-1) !important;
  border-color: var(--brand) !important;
  box-shadow: 0 4px 12px rgba(236, 91, 142, 0.12);
}
.fab-primary {
  background: var(--brand) !important;
  color: #fff !important;
  width: 48px !important;
  height: 48px !important;
  box-shadow: 0 4px 16px rgba(236, 91, 142, 0.3);
  transition: all var(--dur) var(--ease);
}
.fab-primary:hover {
  background: var(--brand-hover) !important;
  box-shadow: 0 6px 24px rgba(236, 91, 142, 0.4);
  transform: scale(1.05);
}
.fab-primary:active {
  background: var(--brand-press) !important;
  transform: scale(0.95);
}
</style>
