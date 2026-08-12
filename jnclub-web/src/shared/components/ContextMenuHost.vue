<script setup lang="ts">
/**
 * ContextMenuHost.vue — 全局右键菜单宿主（挂在布局壳，渲染单个手动模式 NDropdown）
 * 由 useContextMenu 单例驱动，悬浮于鼠标右键位置；选中后自动关闭，点击外部关闭。
 * 弹层继承全局 .n-popover 玻璃化样式，与 ⋯ 左键 NDropdown 风格一致。
 */
import { NDropdown } from 'naive-ui'
import { useContextMenu } from '../composables/useContextMenu'

const { visible, x, y, options, closeMenu, selectMenu } = useContextMenu()
</script>

<template>
  <NDropdown
    :show="visible"
    :x="x"
    :y="y"
    :options="options"
    placement="bottom-start"
    @select="selectMenu"
    @clickoutside="closeMenu"
    @update:show="(v: boolean) => { if (!v) closeMenu() }"
  />
</template>

<style scoped>
/* NDropdown 弹层已继承全局 .n-popover 玻璃化；此处可微调菜单项观感 */
:deep(.n-dropdown-menu-item) {
  font-size: var(--fs-base);
}
</style>
