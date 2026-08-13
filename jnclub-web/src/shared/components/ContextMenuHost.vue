<script setup lang="ts">
/**
 * ContextMenuHost.vue — 全局右键菜单宿主（挂在布局壳，渲染单个手动模式 NDropdown）
 * 由 useContextMenu 单例驱动，悬浮于鼠标右键位置；选中后自动关闭，点击外部关闭。
 * 弹层继承全局 .n-popover 玻璃化样式，与 ⋯ 左键 NDropdown 风格一致。
 */
import { computed } from 'vue'
import { NDropdown, type DropdownProps } from 'naive-ui'
import { useContextMenu } from '../composables/useContextMenu'

const { visible, x, y, options, closeMenu, selectMenu } = useContextMenu()

// naive-ui 2.40 的 DropdownOption[] 与其 options 类型签名不兼容（MenuRenderOption 成员必填 type），
// 运行时对象完全合法，此处收敛到宿主单点断言，保持业务侧 openMenu 使用简单类型。
type DropdownMixedOption = NonNullable<DropdownProps['options']>
const menuOptions = computed<DropdownMixedOption>(() => options.value as unknown as DropdownMixedOption)
</script>

<template>
  <NDropdown
    :show="visible"
    :x="x"
    :y="y"
    :options="menuOptions"
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
