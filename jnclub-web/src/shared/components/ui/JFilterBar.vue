<script setup lang="ts">
/**
 * JFilterBar.vue — 通用筛选条（目录 chip / 标签 chip / 状态筛选）
 * items 的 value 可为 string | number；null 用于「全部」。
 */
import { NIcon } from 'naive-ui'

export interface FilterItem {
  label: string
  value: string | number | null
  count?: number
  icon?: any
}

defineProps<{
  items: FilterItem[]
  modelValue: string | number | null
  allLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | number | null]
  select: [value: string | number | null]
}>()

const handleSelect = (value: string | number | null) => {
  emit('update:modelValue', value)
  emit('select', value)
}
</script>

<template>
  <div class="j-filter-bar">
    <button
      v-if="allLabel !== undefined"
      type="button"
      :class="['j-filter-chip', 'jnclub-bouncy', { 'j-filter-chip-active': modelValue === null }]"
      @click="handleSelect(null)"
    >
      {{ allLabel }}
    </button>
    <button
      v-for="item in items"
      :key="String(item.value)"
      type="button"
      :class="['j-filter-chip', 'jnclub-bouncy', { 'j-filter-chip-active': modelValue === item.value }]"
      @click="handleSelect(item.value)"
    >
      <NIcon v-if="item.icon" :component="item.icon" size="13" />
      {{ item.label }}
      <span v-if="item.count" class="j-filter-count">{{ item.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.j-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.j-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-chip-border);
  background: var(--glass-chip-bg);
  color: var(--glass-chip-text);
  font-size: var(--fs-sm);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.j-filter-chip:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.j-filter-chip-active {
  background: var(--brand-soft);
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 600;
}
.j-filter-count {
  font-size: 10px;
  line-height: 1;
  padding: 2px 5px;
  border-radius: var(--radius-pill);
  background: var(--brand-soft);
  color: var(--brand);
}
</style>
