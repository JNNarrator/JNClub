<script setup lang="ts">
/**
 * FloatingActions.vue — 右侧悬浮操作按钮（氛围升级版）
 * 品牌粉圆 FAB + 呼吸动画 + 底部光晕
 */
import { NIcon, NTooltip } from 'naive-ui'
import { Plus } from 'lucide-vue-next'

defineProps<{
  addLabel?: string
}>()

const emit = defineEmits<{
  add: []
}>()
</script>

<template>
  <div class="fab-wrap">
    <NTooltip placement="left">
      <template #trigger>
        <button class="fab-primary" @click="emit('add')" :aria-label="addLabel || '添加'">
          <NIcon :component="Plus" size="22" color="#fff" />
        </button>
      </template>
      {{ addLabel || '添加收藏' }}
    </NTooltip>

    <div class="fab-glow" aria-hidden="true"></div>
  </div>
</template>

<style scoped>
.fab-wrap {
  position: fixed;
  right: 24px;
  bottom: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  z-index: 100;
  max-width: calc(100vw - 32px);
}

.fab-primary {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--brand) !important;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  animation: jnclub-breathe 2.6s ease-in-out infinite;
  transition: transform var(--dur) var(--ease-bouncy);
}

.fab-primary:hover {
  animation-play-state: paused;
  transform: rotate(5deg) scale(1.05);
  box-shadow: var(--shadow-fab-hover) !important;
}

.fab-primary:active {
  transform: scale(0.96);
}

.fab-glow {
  width: 44px;
  height: 20px;
  margin-top: -6px;
  background: var(--gradient-fab-glow);
  border-radius: var(--radius-pill);
  pointer-events: none;
}
</style>
