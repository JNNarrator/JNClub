<script setup lang="ts">
/**
 * JStatCard.vue — 概览统计卡片基座
 * 统一数字统计卡：图标、数值（JCountUp）、标签、警告态。
 */
import { NIcon } from 'naive-ui'
import JCountUp from '../animation/JCountUp.vue'

withDefaults(defineProps<{
  label: string
  value: number
  icon: any
  warn?: boolean
}>(), {
  warn: false,
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
      <div class="j-stat-value"><JCountUp :to="value" /></div>
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
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1), var(--glass-shadow);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
  text-align: left;
  font-family: inherit;
}
.j-stat-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-2), var(--glass-shadow);
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
  width: 38px; height: 38px; border-radius: 12px;
  flex-shrink: 0;
  background: var(--brand-soft); color: var(--brand);
  display: flex; align-items: center; justify-content: center;
}
.j-stat-warn .j-stat-icon {
  background: var(--danger-soft);
  color: var(--danger-text);
}
.j-stat-value {
  font-size: 24px; font-weight: 800; color: var(--text-1); line-height: 1.1;
  white-space: nowrap;
}
.j-stat-label {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--fs-sm); color: var(--glass-text-secondary);
}
.j-stat-warn-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--danger);
  animation: j-stat-blink 1.2s infinite;
}
@keyframes j-stat-blink {
  50% { opacity: 0.3; }
}

@media (max-width: 699px) {
  .j-stat-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    min-height: 104px;
    padding: 14px;
  }
  .j-stat-icon { width: 32px; height: 32px; border-radius: 10px; }
  .j-stat-value { font-size: 22px; }
}
</style>
