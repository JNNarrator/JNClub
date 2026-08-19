/**
 * shortcuts/index.ts — JNClub 全局快捷键定义（共享注册表 + 帮助面板数据源）
 * 键位原则（参照 jnx）：避开浏览器/系统冲突组合；mod = ⌘(mac) / Ctrl(win)
 */
import type { Chord } from '../composables/useKeyboardShortcut'

export interface ShortcutDef {
  action: string
  label: string
  /** 帮助面板展示的键位文案（如 "⌘K" / "Ctrl+Shift+N"） */
  display: string
  chord: Chord
  /** 编辑态是否仍生效（搜索/帮助/主题等全局键） */
  skipWhenEditing?: boolean
}

/** 平台键位文案 */
export function platformKey(isMac: boolean): string {
  return isMac ? '⌘' : 'Ctrl+'
}

export const GLOBAL_SHORTCUTS: ShortcutDef[] = [
  { action: 'search', label: '全局搜索', display: '⌘K / Ctrl+K', chord: { mods: ['mod'], key: 'k' }, skipWhenEditing: true },
  { action: 'module.bookmarks', label: '切换到收藏夹', display: '⌘1 / Ctrl+1', chord: { mods: ['mod'], key: '1' } },
  { action: 'module.notes', label: '切换到便签', display: '⌘2 / Ctrl+2', chord: { mods: ['mod'], key: '2' } },
  { action: 'module.files', label: '切换到云盘', display: '⌘3 / Ctrl+3', chord: { mods: ['mod'], key: '3' } },
  { action: 'module.vault', label: '切换到密码库', display: '⌘4 / Ctrl+4', chord: { mods: ['mod'], key: '4' } },
  { action: 'module.music', label: '打开音乐', display: '⌘5 / Ctrl+5', chord: { mods: ['mod'], key: '5' } },
  { action: 'note.new', label: '新建便签（切到便签）', display: '⌘⇧N / Ctrl+Shift+N', chord: { mods: ['mod', 'shift'], key: 'n' } },
  { action: 'theme.toggle', label: '切换主题', display: '⌘⇧T / Ctrl+Shift+T', chord: { mods: ['mod', 'shift'], key: 't' }, skipWhenEditing: true },
  { action: 'vault.lock', label: '锁定密码库', display: '⌘⇧L / Ctrl+Shift+L', chord: { mods: ['mod', 'shift'], key: 'l' }, skipWhenEditing: true },
  { action: 'help', label: '快捷键帮助', display: '⌘/ / Ctrl+/', chord: { mods: ['mod'], key: '/' }, skipWhenEditing: true },
]

/** 按 action 查定义 */
export function findShortcut(action: string): ShortcutDef | undefined {
  return GLOBAL_SHORTCUTS.find((s) => s.action === action)
}
