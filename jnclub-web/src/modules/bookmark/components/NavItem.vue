<script setup lang="ts">
/**
 * NavItem.vue — 侧栏导航单项
 * 激活态两态同源（R 标准）：
 *   展开态 = 品牌粉实心圆角矩形 + 白字白图标
 *   折叠态 = 图标居中 + 品牌浅底圆角块，视觉同源
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
    :class="['nav-item', { active, collapsed }]"
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
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 10px 16px; margin: 2px 0;
  border: none; background: transparent; cursor: pointer;
  border-radius: var(--radius-sm); color: var(--text-2);
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
  text-align: left; font-size: 14px; line-height: 1.4;
}
.nav-item:hover:not(.active) { background: var(--hover-bg); color: var(--text-1); }
.nav-item.active:not(.collapsed) { background: var(--brand); color: #fff; }
.nav-item.active:not(.collapsed) .nav-icon { color: #fff; }
.nav-item.active:not(.collapsed) .nav-label { color: #fff; font-weight: 600; }
.nav-item.active.collapsed { justify-content: center; padding: 10px 0; background: var(--brand-soft); }
.nav-item.active.collapsed .nav-icon { color: var(--brand); }
.nav-icon { flex-shrink: 0; color: var(--text-3); transition: color var(--dur) var(--ease); }
.nav-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 500; transition: color var(--dur) var(--ease); }
</style>
