<script setup lang="ts">
/**
 * ViewSwitcher.vue — 多视图分段控件（R 精髓 0.6）
 * 卡片 / 极简 两态可切换，选中态=品牌粉实底白字
 * 切换有过渡
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
      :class="['switcher-btn', { active: props.modelValue === m.key }]"
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
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  gap: 2px;
  padding: 2px;
}

.switcher-btn {
  border: none;
  background: transparent;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
  cursor: pointer;
  border-radius: 6px;
  transition: all var(--dur) var(--ease);
  white-space: nowrap;
}

.switcher-btn:hover:not(.active) {
  color: var(--text-1);
  background: var(--hover-bg);
}

.switcher-btn.active {
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}
</style>
