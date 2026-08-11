<script setup lang="ts">
/**
 * ViewSwitcher.vue — 多视图分段控件（氛围升级版）
 * 激活态：渐变品牌底 + 白字 + bouncy 过渡
 */
export type ViewMode = 'grid' | 'list'

const props = defineProps<{
  modelValue: ViewMode
}>()

const emit = defineEmits<{
  'update:modelValue': [mode: ViewMode]
}>()

const modes: { key: ViewMode; label: string }[] = [
  { key: 'grid', label: '卡片' },
  { key: 'list', label: '极简' },
]
</script>

<template>
  <div class="view-switcher" role="radiogroup">
    <button
      v-for="m in modes"
      :key="m.key"
      :class="['switcher-btn', 'jnclub-bouncy', { active: props.modelValue === m.key }]"
      role="radio"
      :aria-checked="props.modelValue === m.key"
      @click="emit('update:modelValue', m.key)"
    >
      {{ m.label }}
    </button>
  </div>
</template>

<style scoped>
.view-switcher {
  display: inline-flex;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  gap: 2px;
  padding: 3px;
}

.switcher-btn {
  border: none;
  background: transparent;
  padding: 5px 15px;
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-2);
  cursor: pointer;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.switcher-btn:hover:not(.active) {
  color: var(--text-1);
  background: var(--glass-chip-bg);
  box-shadow: var(--shadow-1);
}

.switcher-btn.active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
  box-shadow: none;
}
</style>
