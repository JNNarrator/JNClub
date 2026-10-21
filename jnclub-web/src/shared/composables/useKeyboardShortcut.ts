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

/**
 * 处理器可以返回 `false` 表示「这一条不认这个键」，分发会继续找下一条。
 * 用途：KeepAlive 里同时存活的多个工具可能注册了同一组合（⌘↵ 在 JSON 是执行、
 * 在 HTTP 是发送），失活的那个返回 false 让位给激活的那个。
 */
export type ShortcutHandler = (e: KeyboardEvent) => unknown

interface Entry {
  action: string
  chord: Chord
  handler: ShortcutHandler
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

/**
 * 把 KeyboardEvent 归一成「可能是这个键」的几个候选，用于比较主键。
 *
 * 为什么不能只看 `e.key`：macOS 上按住 Option 会改变 `e.key` 的字面值
 * （⌥W → '∑'、⌥← → 'ArrowLeft' 以外的组合也会变），于是 ⌥W 这类
 * 绑定永远匹配不上。`e.code` 是物理键位，不受修饰键影响，所以两者都算候选。
 */
function keyCandidates(e: KeyboardEvent): string[] {
  const out: string[] = []
  if (e.key) out.push(e.key.toLowerCase())
  const code = e.code || ''
  if (code.startsWith('Key')) out.push(code.slice(3).toLowerCase())
  else if (code.startsWith('Digit')) out.push(code.slice(5))
  else if (code.startsWith('Numpad')) out.push(code.slice(6).toLowerCase())
  else if (code) out.push(code.toLowerCase())
  return out
}

function matchesChord(e: KeyboardEvent, c: Chord): boolean {
  const want = c.key.toLowerCase()
  if (!keyCandidates(e).includes(want)) return false

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
    // 先让处理器表态：返回 false 表示「我不认这个键」，继续找下一条
    const handled = entry.handler(e)
    if (handled === false) continue
    e.preventDefault()
    e.stopPropagation()
    return
  }
}

function ensureListener() {
  if (listening) return
  listening = true
  window.addEventListener('keydown', dispatch, true)
}

/**
 * 注册一条快捷键，返回注销函数。
 * 与 `useKeyboardShortcut` 的区别：不绑定组件生命周期，适合需要「改键后重新注册」
 * 或非 setup 上下文（如 devtools 的快捷键适配层）的场景。
 */
export function registerShortcut(
  action: string,
  chord: Chord,
  handler: ShortcutHandler,
  opts?: ShortcutOpts,
): () => void {
  ensureListener()
  const entry: Entry = {
    action,
    chord,
    handler,
    skipWhenEditing: opts?.skipWhenEditing ?? false,
  }
  registry.push(entry)
  return () => {
    const i = registry.indexOf(entry)
    if (i >= 0) registry.splice(i, 1)
  }
}

/** 注册一条全局快捷键；组件卸载时自动注销 */
export function useKeyboardShortcut(
  action: string,
  chord: Chord,
  handler: ShortcutHandler,
  opts?: ShortcutOpts,
) {
  const unregister = registerShortcut(action, chord, handler, opts)
  if (getCurrentScope()) onScopeDispose(unregister)
}

/** 供上层复用的键位匹配（如 devtools 的 useLegacyShortcut） */
export function chordMatches(e: KeyboardEvent, c: Chord): boolean {
  return matchesChord(e, c)
}

/** 当前是否为编辑态（input/textarea/contenteditable） */
export function isEditingTarget(el: EventTarget | null): boolean {
  return isEditing(el)
}

/** 供调试：打印当前已注册条目 */
export function debugShortcuts() {
  console.table(registry.map(({ action, chord }) => ({ action, chord: chord.mods.join('+') + '+' + chord.key })))
}
