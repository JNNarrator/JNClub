/**
 * shortcuts/index.ts — 开发者工具快捷键表
 *
 * 迁移自 jnx `src/shortcuts/index.ts`，做了三处收敛：
 * 1. **只保留工具内动作与工具间导航**。jnx 的 `global.search` / `app.settings` /
 *    `theme.cycle` 在 JNClub 已由 `shared/shortcuts` 承担（⌘K、⌘⇧T、⌘/ 等），
 *    重复注册会导致同一次按键触发两个处理器。
 * 2. **导航键统一 ⌘⇧ 前缀**，避开 JNClub 全局的 ⌘1–⌘5（模块切换）。
 * 3. **每个动作带 `scope`**：`'global'` 表示在开发者工具页内始终生效；
 *    填工具 id 表示仅该工具激活时生效。这样 `⌘↵` 在 JSON 工具是「执行」，
 *    在 HTTP 工具是「发送」，两个工具同时被 KeepAlive 保活也不会互相抢键。
 */
import type { Chord } from '@/shared/composables/useKeyboardShortcut'

export interface PlatformChords {
  mac: Chord
  win: Chord
}

export interface DevtoolsShortcutDef {
  /** 展示分组 */
  group: string
  label: string
  /** 生效作用域：'global' 或工具 id */
  scope: string
  chords: PlatformChords
}

export const DEVTOOLS_SHORTCUTS = {
  /* ─── 工具间导航（页内全局） ─── */
  'nav.toHome': {
    group: '导航',
    label: '回到启动台',
    scope: 'global',
    chords: { mac: { mods: ['mod', 'shift'], key: '1' }, win: { mods: ['mod', 'shift'], key: '1' } },
  },
  'nav.toJson': {
    group: '导航',
    label: 'JSON 工具',
    scope: 'global',
    chords: { mac: { mods: ['mod', 'shift'], key: 'j' }, win: { mods: ['mod', 'shift'], key: 'j' } },
  },
  'nav.toHttp': {
    group: '导航',
    label: 'HTTP 请求',
    scope: 'global',
    chords: { mac: { mods: ['mod', 'shift'], key: 'h' }, win: { mods: ['mod', 'shift'], key: 'h' } },
  },
  'nav.toClipboard': {
    group: '导航',
    label: '剪贴板',
    scope: 'global',
    chords: { mac: { mods: ['mod', 'shift'], key: 'b' }, win: { mods: ['mod', 'shift'], key: 'b' } },
  },
  'nav.toConverter': {
    group: '导航',
    label: '格式互转',
    scope: 'global',
    chords: { mac: { mods: ['mod', 'shift'], key: 'e' }, win: { mods: ['mod', 'shift'], key: 'e' } },
  },
  'nav.toCron': {
    group: '导航',
    label: 'Cron 表达式',
    scope: 'global',
    chords: { mac: { mods: ['mod', 'shift'], key: 'x' }, win: { mods: ['mod', 'shift'], key: 'x' } },
  },
  'nav.toSettings': {
    group: '导航',
    label: '工具设置',
    scope: 'global',
    // ⌘⇧S 已被 converter.swap 占用（同一键盘只有一条注册能命中），改用 ⌘, 这个通用「偏好设置」键
    chords: { mac: { mods: ['mod'], key: ',' }, win: { mods: ['mod'], key: ',' } },
  },
  'nav.toShortcuts': {
    group: '导航',
    label: '快捷键设置',
    scope: 'global',
    // ⌘/ 是 JNClub 全局帮助面板的键位，页内再注册会抢键，故用 ⌘⇧/
    chords: { mac: { mods: ['mod', 'shift'], key: '/' }, win: { mods: ['mod', 'shift'], key: '/' } },
  },
  'nav.closeTab': {
    group: '导航',
    label: '关闭当前标签页',
    scope: 'global',
    // ⌘W / Ctrl+W 会被浏览器直接吃掉（关浏览器标签页），preventDefault 也拦不住，
    // 因此改用 Option+W / Alt+W —— 这是 Web 端唯一能真正生效的选择。
    chords: { mac: { mods: ['alt'], key: 'w' }, win: { mods: ['alt'], key: 'w' } },
  },
  'nav.cycleNext': {
    group: '导航',
    label: '切换到下一个标签页',
    scope: 'global',
    chords: { mac: { mods: ['alt'], key: 'ArrowRight' }, win: { mods: ['alt'], key: 'ArrowRight' } },
  },
  'nav.cyclePrev': {
    group: '导航',
    label: '切换到上一个标签页',
    scope: 'global',
    chords: { mac: { mods: ['alt'], key: 'ArrowLeft' }, win: { mods: ['alt'], key: 'ArrowLeft' } },
  },

  'palette.toggle': {
    group: '导航',
    label: '命令面板',
    scope: 'global',
    // ⌘⇧P 与 jnx 一致；JNClub 全局未占用此键位
    chords: { mac: { mods: ['mod', 'shift'], key: 'p' }, win: { mods: ['mod', 'shift'], key: 'p' } },
  },

  /* ─── 启动台内动作 ─── */
  'home.search': {
    group: '工具内动作',
    label: '搜索工具',
    // 启动台激活时 activeTabId === 'devtools-home'，正好可用作 scope
    scope: 'devtools-home',
    chords: { mac: { mods: ['mod'], key: 'k' }, win: { mods: ['mod'], key: 'k' } },
  },

  /* ─── 工具内动作 ─── */
  'json.run': {
    group: '工具内动作',
    label: '执行 / 格式化 JSON',
    scope: 'json',
    chords: { mac: { mods: ['mod'], key: 'Enter' }, win: { mods: ['mod'], key: 'Enter' } },
  },
  'json.format': {
    group: '工具内动作',
    label: '美化 JSON',
    scope: 'json',
    chords: { mac: { mods: ['mod', 'shift'], key: 'f' }, win: { mods: ['mod', 'shift'], key: 'f' } },
  },
  'curl.send': {
    group: '工具内动作',
    label: '发送 HTTP 请求',
    scope: 'curl',
    chords: { mac: { mods: ['mod'], key: 'Enter' }, win: { mods: ['mod'], key: 'Enter' } },
  },
  'curl.import': {
    group: '工具内动作',
    label: '导入 cURL 命令',
    scope: 'curl',
    chords: { mac: { mods: ['mod', 'shift'], key: 'i' }, win: { mods: ['mod', 'shift'], key: 'i' } },
  },
  'curl.focusUrl': {
    group: '工具内动作',
    label: '聚焦 URL 输入框',
    scope: 'curl',
    // ⌘L 被浏览器地址栏占用，改用 ⌘⇧L（开发者工具页内不注册 vault.lock，无冲突）
    chords: { mac: { mods: ['mod', 'shift'], key: 'l' }, win: { mods: ['mod', 'shift'], key: 'l' } },
  },
  'converter.swap': {
    group: '工具内动作',
    label: '交换源 / 目标格式',
    scope: 'converter',
    chords: { mac: { mods: ['mod', 'shift'], key: 's' }, win: { mods: ['mod', 'shift'], key: 's' } },
  },
  'cron.preview': {
    group: '工具内动作',
    label: '刷新执行时间预览',
    scope: 'cron',
    chords: { mac: { mods: ['mod'], key: 'Enter' }, win: { mods: ['mod'], key: 'Enter' } },
  },
} as const satisfies Record<string, DevtoolsShortcutDef>

export type DevtoolsShortcutAction = keyof typeof DEVTOOLS_SHORTCUTS

export const DEVTOOLS_SHORTCUT_ACTIONS = Object.keys(DEVTOOLS_SHORTCUTS) as DevtoolsShortcutAction[]

/** 按分组聚合（设置页 / 速查面板数据源） */
export const DEVTOOLS_SHORTCUT_GROUPS = (() => {
  const order: string[] = []
  const map = new Map<string, { action: DevtoolsShortcutAction; label: string }[]>()
  for (const action of DEVTOOLS_SHORTCUT_ACTIONS) {
    const def = DEVTOOLS_SHORTCUTS[action]
    if (!map.has(def.group)) {
      map.set(def.group, [])
      order.push(def.group)
    }
    map.get(def.group)!.push({ action, label: def.label })
  }
  return order.map((name) => ({ name, items: map.get(name)! }))
})()

/** 启动台「快捷键速查」展示项 */
export const HOME_CHEATSHEET: { action: DevtoolsShortcutAction; desc: string }[] = [
  { action: 'palette.toggle', desc: '命令面板' },
  { action: 'nav.toJson', desc: 'JSON 工具' },
  { action: 'nav.toHttp', desc: 'HTTP 请求' },
  { action: 'nav.toConverter', desc: '格式互转' },
  { action: 'nav.toCron', desc: 'Cron 表达式' },
  { action: 'nav.closeTab', desc: '关闭当前标签' },
  { action: 'nav.toHome', desc: '回到启动台' },
]

/** 动作 → 展示标签 */
export const DEVTOOLS_SHORTCUT_LABELS: Record<string, string> = (() => {
  const m: Record<string, string> = {}
  for (const a of DEVTOOLS_SHORTCUT_ACTIONS) m[a] = DEVTOOLS_SHORTCUTS[a].label
  return m
})()

export function shortcutScope(action: string): string {
  return (DEVTOOLS_SHORTCUTS as Record<string, DevtoolsShortcutDef>)[action]?.scope ?? 'global'
}
