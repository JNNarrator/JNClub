/**
 * useShortcutBindingsStore — 开发者工具快捷键自定义绑定
 *
 * 迁移自 jnx `src/stores/shortcutBindings.ts`。差异：
 * - 持久化从 Tauri SQLite 的 `settings` 表改为 `useUserPreferences`
 *   （key `devtools.shortcutOverrides`，内部同时写 localStorage + 后端 KV）
 * - 平台判定改用 JNClub `usePlatform()`（纯 UA 检测）
 * - 表驱动来源改为本模块的 `DEVTOOLS_SHORTCUTS`
 *
 * 存储结构：`{ mac: { action: Chord }, win: { action: Chord } }`，
 * 按平台分开存，这样在 Mac 上改键不会污染 Windows 的默认体验。
 */
import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import { useUserPreferences } from '@/shared/composables/useUserPreferences'
import type { Chord } from '@/shared/composables/useKeyboardShortcut'
import { usePlatform } from '@/shared/composables/usePlatform'
import {
  DEVTOOLS_SHORTCUTS,
  DEVTOOLS_SHORTCUT_ACTIONS,
  DEVTOOLS_SHORTCUT_LABELS,
  type DevtoolsShortcutAction,
} from '../shortcuts'

type Platform = 'mac' | 'win'
const PREF_KEY = 'devtools.shortcutOverrides'

/** 文本编辑类保留组合，禁止把工具键绑到这些上（会破坏输入） */
const RESERVED_EDIT: Record<string, string> = {
  'mod|c': '复制',
  'mod|v': '粘贴',
  'mod|x': '剪切',
  'mod|z': '撤销',
  'mod|shift|z': '重做',
  'mod|a': '全选',
  'mod|s': '保存',
  'mod|f': '查找',
}

/** 浏览器 / 操作系统已知冲突组合（mac） */
const SYSTEM_CONFLICTS_MAC: Record<string, string> = {
  'mod|h': 'macOS 隐藏当前窗口',
  'mod|j': '浏览器下载管理',
  'mod|d': '浏览器加书签 / macOS 分屏',
  'mod|w': '关闭浏览器标签页',
  'mod|t': '新建浏览器标签页',
  'mod|r': '刷新页面',
  'mod|q': '退出',
  'mod|m': '最小化',
  'mod|l': '聚焦地址栏',
  'mod|n': '新建窗口',
}
for (let i = 1; i <= 9; i++) SYSTEM_CONFLICTS_MAC[`mod|${i}`] = `切到浏览器第 ${i} 个标签页`

/** 浏览器已知冲突组合（win/linux） */
const SYSTEM_CONFLICTS_WIN: Record<string, string> = {
  'mod|w': '关闭浏览器标签页',
  'mod|t': '新建浏览器标签页',
  'mod|r': '刷新页面',
  'mod|n': '新建窗口',
  'mod|l': '聚焦地址栏',
  'mod|d': '加书签',
  'mod|h': '历史记录',
  'mod|j': '下载管理',
}
for (let i = 1; i <= 9; i++) SYSTEM_CONFLICTS_WIN[`mod|${i}`] = `切到浏览器第 ${i} 个标签页`

function signature(c: Chord): string {
  const mods = [...c.mods].sort().join(',')
  return `${mods}|${c.key.toLowerCase()}`
}

export const useShortcutBindingsStore = defineStore('devtoolsShortcuts', () => {
  const prefs = useUserPreferences()
  const { isWindows } = usePlatform()

  const platform = ref<Platform>(isWindows.value ? 'win' : 'mac')
  const overrides = reactive<Record<string, Chord | undefined>>({})
  const loaded = ref(false)

  const allActions = DEVTOOLS_SHORTCUT_ACTIONS
  const labels = DEVTOOLS_SHORTCUT_LABELS

  function defaultChord(action: DevtoolsShortcutAction): Chord {
    const def = DEVTOOLS_SHORTCUTS[action]
    return platform.value === 'mac' ? def.chords.mac : def.chords.win
  }

  function chordFor(action: DevtoolsShortcutAction): Chord {
    return overrides[action] ?? defaultChord(action)
  }

  /** 供 Kbd 组件渲染的键位数组（mods + key） */
  function keysFor(action: DevtoolsShortcutAction): string[] {
    const c = chordFor(action)
    return [...c.mods, c.key]
  }

  /** Electron accelerator 风格文案（设置页展示 / 复制用） */
  function accelerator(action: DevtoolsShortcutAction): string {
    const c = chordFor(action)
    const parts: string[] = c.mods.map((m) =>
      m === 'mod'
        ? platform.value === 'mac'
          ? 'Cmd'
          : 'Ctrl'
        : m === 'opt'
          ? platform.value === 'mac'
            ? 'Option'
            : 'Alt'
          : m === 'alt'
            ? 'Alt'
            : 'Shift',
    )
    parts.push(c.key.length === 1 ? c.key.toUpperCase() : c.key)
    return parts.join('+')
  }

  function sanitize(data: unknown): Partial<Record<Platform, Record<string, Chord>>> | null {
    if (!data || typeof data !== 'object') return null
    const raw = data as Record<string, unknown>
    const out: Partial<Record<Platform, Record<string, Chord>>> = {}
    for (const plat of ['mac', 'win'] as Platform[]) {
      const group = raw[plat]
      if (!group || typeof group !== 'object') continue
      const map: Record<string, Chord> = {}
      for (const [action, chord] of Object.entries(group as Record<string, unknown>)) {
        if (!DEVTOOLS_SHORTCUT_ACTIONS.includes(action as DevtoolsShortcutAction)) continue
        const c = chord as Partial<Chord> | null
        if (!c || !Array.isArray(c.mods) || typeof c.key !== 'string') continue
        map[action] = { mods: c.mods as Chord['mods'], key: c.key }
      }
      out[plat] = map
    }
    return out
  }

  function apply(data: Partial<Record<Platform, Record<string, Chord>>> | null): void {
    for (const a of Object.keys(overrides)) delete overrides[a]
    const group = data?.[platform.value]
    if (!group) return
    for (const [action, chord] of Object.entries(group)) overrides[action] = chord
  }

  async function load(): Promise<void> {
    if (loaded.value) return
    apply(sanitize(prefs.get(PREF_KEY, null)))
    try {
      await prefs.load()
      apply(sanitize(prefs.get(PREF_KEY, null)))
    } catch {
      /* 后端不可用：保留 localStorage 结果 */
    }
    loaded.value = true
  }

  async function persist(): Promise<void> {
    const payload: Record<Platform, Record<string, Chord>> = { mac: {}, win: {} }
    // 先把另一平台的已存值读回来，避免覆盖
    const existing = sanitize(prefs.get(PREF_KEY, null))
    if (existing?.mac) payload.mac = { ...existing.mac }
    if (existing?.win) payload.win = { ...existing.win }
    payload[platform.value] = {}
    for (const action of Object.keys(overrides)) {
      const chord = overrides[action]
      if (chord) payload[platform.value][action] = chord
    }
    await prefs.set(PREF_KEY, payload)
  }

  function setBinding(action: DevtoolsShortcutAction, chord: Chord): void {
    overrides[action] = chord
    void persist()
  }

  function reset(action: DevtoolsShortcutAction): void {
    delete overrides[action]
    void persist()
  }

  function resetAll(): void {
    for (const a of Object.keys(overrides)) delete overrides[a]
    void persist()
  }

  function isDefault(action: DevtoolsShortcutAction): boolean {
    return !overrides[action]
  }

  /** 与其它动作的键位冲突检测 */
  function conflicts(action: DevtoolsShortcutAction, chord: Chord): DevtoolsShortcutAction | null {
    const sig = signature(chord)
    for (const a of DEVTOOLS_SHORTCUT_ACTIONS) {
      if (a === action) continue
      if (signature(chordFor(a)) === sig) return a
    }
    return null
  }

  /** 绑定时的告警文案（null 表示无风险） */
  function warn(action: DevtoolsShortcutAction, chord: Chord): string | null {
    const sig = signature(chord)
    if (RESERVED_EDIT[sig]) return `与文本编辑冲突：${RESERVED_EDIT[sig]}`
    const sys = platform.value === 'mac' ? SYSTEM_CONFLICTS_MAC : SYSTEM_CONFLICTS_WIN
    if (sys[sig]) return `系统/浏览器冲突：${sys[sig]}`
    const other = conflicts(action, chord)
    if (other) return `与「${labels[other] ?? other}」冲突`
    return null
  }

  /** 有自定义绑定的动作数 */
  const customizedCount = computed(() => Object.values(overrides).filter(Boolean).length)

  return {
    platform,
    overrides,
    loaded,
    allActions,
    labels,
    customizedCount,
    defaultChord,
    chordFor,
    keysFor,
    accelerator,
    load,
    persist,
    setBinding,
    reset,
    resetAll,
    isDefault,
    conflicts,
    warn,
  }
})
