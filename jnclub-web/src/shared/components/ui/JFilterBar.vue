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
  /* chip 语言：**透明底** + 1px 淡粉描边 + 粉字；只有激活态才给 --brand-soft 底。
     原先是「粉底 + 粉边 + 粉字」三层粉，一排 chip 就吃掉大半个粉色面积预算。 */
  border: 1px solid var(--glass-chip-border);
  background: transparent;
  color: var(--glass-chip-text);
  font-size: var(--fs-sm);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  flex-shrink: 0;
}
.j-filter-chip:hover {
  border-color: var(--brand-text);
  background: var(--brand-soft);
  color: var(--brand-text);
}
.j-filter-chip-active {
  background: var(--brand-soft);
  border-color: var(--brand-text);
  color: var(--brand-text);
  font-weight: 600;
}
.j-filter-count {
  font-size: var(--fs-2xs);
  line-height: 1;
  padding: 2px 5px;
  border-radius: var(--radius-pill);
  /* 计数徽标去粉底：chip 本身已是淡粉描边，徽标再叠一层粉底会让每个 chip
     出现两个粉色层级。改为中性底 + 继承文字色，激活态才跟随品牌色。 */
  background: var(--hover-bg);
  color: var(--text-3);
}
.j-filter-chip-active .j-filter-count,
.j-filter-chip:hover .j-filter-count {
  background: transparent;
  color: var(--brand-text);
}

/* 三态：j-filter-chip-active 是「选中」态，与 CSS :active（按下）不同，此前缺失 */
.j-filter-chip:active {
  transform: scale(0.96);
}
.j-filter-chip:disabled,
.j-filter-chip[aria-disabled='true'] {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: none;
}
</style>
