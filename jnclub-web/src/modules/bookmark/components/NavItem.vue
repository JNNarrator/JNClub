<script setup lang="ts">
/**
 * NavItem.vue — 侧栏导航单项
 * 激活态：渐变光条背景 + 左侧品牌色 border
 * 折叠态：图标居中 + 品牌浅底圆角块
 */
import { NIcon } from 'naive-ui'
import type { Component } from 'vue'

defineProps<{
  icon: Component
  label: string
  active?: boolean
  collapsed?: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    :class="['nav-item', 'jnclub-bouncy', { active, collapsed }]"
    @click="$emit('click')"
  >
    <NIcon :size="20" :class="['nav-icon', { active }]">
      <component :is="icon" />
    </NIcon>
    <span v-if="!collapsed" :class="['nav-label', { active }]">
      {{ label }}
    </span>
  </button>
</template>

<style scoped>
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 16px;
  margin: 2px 0;
  border: none;
  border-left: 3px solid transparent;
  background: transparent;
  cursor: pointer;
  border-radius: var(--radius-sm);
  color: var(--text-2);
  text-align: left;
  font-size: 14px;
  line-height: 1.4;
}

.nav-item:hover:not(.active) {
  background: var(--hover-bg);
  color: var(--text-1);
}

/* 展开激活态：渐变光条 + 左侧品牌色 border */
.nav-item.active:not(.collapsed) {
  background: var(--gradient-nav-active);
  border-left-color: var(--brand);
  color: var(--text-1);
}
.nav-item.active:not(.collapsed) .nav-icon {
  color: var(--brand);
}
.nav-item.active:not(.collapsed) .nav-label {
  color: var(--text-1);
  font-weight: 600;
}

/* 折叠激活态 */
.nav-item.active.collapsed {
  justify-content: center;
  padding: 10px 0;
  background: var(--brand-soft);
  border-left-color: transparent;
}
.nav-item.active.collapsed .nav-icon {
  color: var(--brand);
}

.nav-icon {
  flex-shrink: 0;
  color: var(--text-3);
  transition: color var(--dur) var(--ease);
}

.nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  transition: color var(--dur) var(--ease);
}
</style>
