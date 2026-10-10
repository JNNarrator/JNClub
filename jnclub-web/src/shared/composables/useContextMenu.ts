/**
 * useContextMenu.ts — 全局单例右键菜单状态
 * 各组件在根元素挂 @contextmenu.prevent="openMenu($event, options, onSelect)"，
 * 触屏长按则用 openMenuAt(x, y, options, onSelect)（见 useLongPress）。
 * 由 ContextMenuHost.vue（布局壳挂载）渲染单个手动模式 NDropdown，悬浮于触发位置。
 * 菜单项直接复用各组件现有 dropdownOptions，保证与 ⋯ 左键菜单完全一致。
 */
import { ref } from 'vue'
import type { DropdownOption } from 'naive-ui'

const visible = ref(false)
const x = ref(0)
const y = ref(0)
const options = ref<DropdownOption[]>([])
let onSelect: ((key: string) => void) | null = null

/**
 * 在指定屏幕坐标打开菜单（自动阻止浏览器默认菜单）。
 * 供触屏长按复用：桌面走 openMenu(e)，触屏走 openMenuAt(长按点坐标)。
 */
export function openMenuAt(
  px: number,
  py: number,
  menuOptions: DropdownOption[],
  selectHandler: (key: string) => void,
) {
  options.value = menuOptions
  onSelect = selectHandler
  x.value = px
  y.value = py
  visible.value = true
}

/** 在鼠标位置打开右键菜单（自动阻止浏览器默认菜单） */
export function openMenu(
  e: MouseEvent,
  menuOptions: DropdownOption[],
  selectHandler: (key: string) => void,
) {
  e.preventDefault()
  openMenuAt(e.clientX, e.clientY, menuOptions, selectHandler)
}

/** 关闭右键菜单 */
export function closeMenu() {
  visible.value = false
  onSelect = null
}

/** 菜单项选中：执行对应操作后关闭 */
export function selectMenu(key: string) {
  const handler = onSelect
  closeMenu()
  if (handler) handler(key)
}

export function useContextMenu() {
  return { visible, x, y, options, openMenu, openMenuAt, closeMenu, selectMenu }
}
