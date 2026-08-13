<script setup lang="ts">
/**
 * NavItem.vue — 侧栏导航单项
 * 激活态：品牌浅底 pill + 品牌色图标/文字
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
    <NIcon :size="collapsed ? 22 : 20" :class="['nav-icon', { active }]">
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
  background: transparent;
  cursor: pointer;
  border-radius: var(--radius-pill);
  color: var(--text-3);
  text-align: left;
  font-size: var(--fs-base);
  line-height: 1.4;
}

.nav-item:hover:not(.active) {
  background: var(--hover-bg);
  color: var(--text-1);
}

/* 展开激活态：pill 形状 + 品牌底 + 左描边强化（选中项更醒目） */
.nav-item.active:not(.collapsed) {
  background: var(--gradient-nav-active);
  color: var(--text-1);
  font-weight: 600;
  box-shadow: inset 3px 0 0 var(--brand);
}
.nav-item.active:not(.collapsed) .nav-icon {
  color: var(--brand);
}

/* 折叠激活态：品牌浅底圆角块 + 更强边框 */
.nav-item.active.collapsed {
  justify-content: center;
  padding: 10px 0;
  background: var(--brand-soft);
  border-radius: var(--radius-sm);
  box-shadow: inset 0 0 0 1.5px var(--brand);
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
  font-size: var(--fs-base);
  font-weight: 500;
  transition: color var(--dur) var(--ease);
}
.nav-label.active {
  font-weight: 600;
  color: var(--text-1);
}
</style>
