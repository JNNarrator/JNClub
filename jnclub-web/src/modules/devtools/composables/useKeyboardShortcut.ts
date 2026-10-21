/**
 * useKeyboardShortcut.ts — 开发者工具的快捷键适配层
 *
 * 为什么需要这一层：
 * jnx（Tauri 桌面端）自带一套快捷键引擎，签名是
 *   `useKeyboardShortcut(action, handler, opts)`
 *   `useLegacyShortcut({ key, mod?, alt?, shift?, skipWhenEditing? }, handler)`
 * 工具页面（ToolJson / ToolCurl …）都是按这个签名写的。JNClub 的
 * `shared/composables/useKeyboardShortcut` 是另一套签名：
 *   `useKeyboardShortcut(action, chord, handler, opts)`
 *
 * 本文件做三件事：
 * 1. 保留 jnx 旧签名，让迁移过来的工具视图零改动；
 * 2. chord 不再写死在代码里，改为从 `DEVTOOLS_SHORTCUTS` 表 + 用户自定义绑定
 *    （`useShortcutBindingsStore`）解析，所以设置页改键后立刻生效；
 * 3. 补上 `useLegacyShortcut`，并让绑定的键位变化时自动重新注册。
 *
 * 语义对齐（重要）：`skipWhenEditing` 在两边含义相同 —— true 表示「编辑态跳过」。
 */
import { getCurrentScope, onScopeDispose, watch } from 'vue'
import {
  registerShortcut,
  type Chord,
  type ShortcutOpts,
} from '@/shared/composables/useKeyboardShortcut'
import { useShortcutBindingsStore } from '../stores/shortcutBindings'
import { useDevtoolsStore } from '../stores/tools'
import { shortcutScope, type DevtoolsShortcutAction } from '../shortcuts'

/** 绑定 store 的 chord 转成引擎 chord（mods 里 opt 归一为 alt） */
function normalize(chord: Chord): Chord {
  return { mods: [...chord.mods], key: chord.key }
}

/**
 * jnx 旧签名：按动作注册快捷键。
 * chord 来自 `DEVTOOLS_SHORTCUTS[action]`，用户自定义绑定优先。
 */
export function useKeyboardShortcut(
  action: DevtoolsShortcutAction,
  handler: () => void,
  opts: ShortcutOpts = {},
): void {
  const bindings = useShortcutBindingsStore()
  const devtools = useDevtoolsStore()
  let unregister: (() => void) | null = null

  /**
   * 作用域门禁：KeepAlive 会把所有打开过的工具同时保活，
   * 于是 `⌘↵` 在 JSON 与 HTTP 工具里各注册了一条。分发器按注册序匹配、
   * 命中即停，必须让失活的工具主动「让位」（返回 false），否则切到 HTTP 后
   * 按 ⌘↵ 可能触发的是后台 JSON 工具的执行。
   */
  const gate = (): boolean => {
    const scope = shortcutScope(action)
    return scope === 'global' || devtools.activeTabId === scope
  }

  const sync = () => {
    unregister?.()
    unregister = registerShortcut(
      `devtools:${action}`,
      normalize(bindings.chordFor(action)),
      () => {
        if (!gate()) return false
        handler()
        return true
      },
      opts,
    )
  }

  sync()

  // 改键 / 切平台后重新注册（chordFor 依赖 overrides 与 platform，可被追踪）
  const stopWatch = watch(
    () => {
      const c = bindings.chordFor(action)
      return `${c.mods.join(',')}|${c.key}`
    },
    sync,
  )

  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopWatch()
      unregister?.()
      unregister = null
    })
  }
}

export interface LegacyShortcutDef {
  key: string
  /** mod = ⌘(mac) / Ctrl(win) */
  mod?: boolean
  alt?: boolean
  shift?: boolean
  /** 编辑态是否跳过；默认 false（编辑态仍生效，与 jnx 一致） */
  skipWhenEditing?: boolean
  /** 作用域：工具 id 表示只在该工具激活时生效；不填则始终生效 */
  scope?: string
}

/**
 * jnx 旧签名：直接按键位注册（不走动作表）。
 * 用于工具内一次性绑定，例如 HTTP 工具的 ⌘↵ 发送。
 */
export function useLegacyShortcut(def: LegacyShortcutDef, handler: () => void): void {
  const mods: Chord['mods'] = []
  if (def.mod) mods.push('mod')
  if (def.shift) mods.push('shift')
  if (def.alt) mods.push('alt')

  const devtools = useDevtoolsStore()
  const unregister = registerShortcut(
    `devtools:legacy:${mods.join('+')}|${def.key}`,
    { mods, key: def.key },
    () => {
      if (def.scope && devtools.activeTabId !== def.scope) return false
      handler()
      return true
    },
    { skipWhenEditing: def.skipWhenEditing ?? false },
  )

  if (getCurrentScope()) onScopeDispose(unregister)
}
