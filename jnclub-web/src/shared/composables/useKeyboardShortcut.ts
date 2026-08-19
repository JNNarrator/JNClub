/**
 * useKeyboardShortcut.ts — 全局键盘快捷键引擎（注册中心 + 捕获分发）
 * 参照 jnx 的成熟实现简化而来：
 * - 单例 window keydown 捕获监听，注册条目按注册序匹配，命中即 preventDefault
 * - mod 修饰键按平台展开（mac → Meta，win → Ctrl），避免 ⌘/Ctrl 双按误触发
 * - 输入框守卫：编辑态默认跳过（防止影响打字），skipWhenEditing=true 的全局键仍生效
 * - 组件卸载（onScopeDispose）自动注销
 */
import { onScopeDispose, getCurrentScope } from 'vue'

export type Mod = 'mod' | 'shift' | 'alt' | 'opt'

export interface Chord {
  mods: Mod[]
  key: string
}

export interface ShortcutOpts {
  /** 编辑态（input/textarea/contenteditable 聚焦）是否仍生效；默认 false */
  skipWhenEditing?: boolean
}

interface Entry {
  action: string
  chord: Chord
  handler: (e: KeyboardEvent) => void
  skipWhenEditing: boolean
}

let platform: 'mac' | 'win' = 'mac'

export function setShortcutPlatform(p: 'mac' | 'win') {
  platform = p
}

const registry: Entry[] = []
let listening = false

function isEditing(el: EventTarget | null): boolean {
  if (!(el instanceof Element)) return false
  const tag = el.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return true
  return (el as HTMLElement).isContentEditable === true
}

function matchesChord(e: KeyboardEvent, c: Chord): boolean {
  const key = e.key.toLowerCase()
  if (key !== c.key.toLowerCase()) return false

  const isMac = platform === 'mac'
  const wantMod = c.mods.includes('mod')
  const wantShift = c.mods.includes('shift')
  const wantAlt = c.mods.includes('alt') || c.mods.includes('opt')

  const hasMeta = e.metaKey
  const hasCtrl = e.ctrlKey

  if (wantMod) {
    if (isMac ? !hasMeta : !hasCtrl) return false
    // 禁止另一平台主修饰键同时按下，避免 Win 下 Ctrl+⌘ 之类误触发
    if (isMac ? hasCtrl : hasMeta) return false
  } else {
    if (isMac ? hasMeta : hasCtrl) return false
  }

  if (wantShift !== e.shiftKey) return false
  if (wantAlt !== e.altKey) return false
  return true
}

function dispatch(e: KeyboardEvent) {
  const editing = isEditing(e.target)
  for (const entry of registry) {
    if (!matchesChord(e, entry.chord)) continue
    if (editing && entry.skipWhenEditing) continue
    e.preventDefault()
    e.stopPropagation()
    entry.handler(e)
    return
  }
}

function ensureListener() {
  if (listening) return
  listening = true
  window.addEventListener('keydown', dispatch, true)
}

/** 注册一条全局快捷键；组件卸载时自动注销 */
export function useKeyboardShortcut(
  action: string,
  chord: Chord,
  handler: (e: KeyboardEvent) => void,
  opts?: ShortcutOpts,
) {
  ensureListener()
  const entry: Entry = {
    action,
    chord,
    handler,
    skipWhenEditing: opts?.skipWhenEditing ?? false,
  }
  registry.push(entry)

  if (getCurrentScope()) {
    onScopeDispose(() => {
      const i = registry.indexOf(entry)
      if (i >= 0) registry.splice(i, 1)
    })
  }
}

/** 供调试：打印当前已注册条目 */
export function debugShortcuts() {
  console.table(registry.map(({ action, chord }) => ({ action, chord: chord.mods.join('+') + '+' + chord.key })))
}
