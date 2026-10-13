/**
 * useAppShortcuts.ts — JNClub 应用级全局快捷键注册（壳层：AppWrapper / RecycleLayout 共用）
 * - 平台检测同步进快捷键引擎（mod = ⌘ / Ctrl）
 * - 模块切换、音乐跳转、新建便签、切换主题、锁定密码库、帮助面板
 * - 搜索快捷键由 Home 注册（搜索抽屉挂在 Home 内）
 */
import { useRouter } from 'vue-router'
import { useKeyboardShortcut, setShortcutPlatform } from './useKeyboardShortcut'
import { usePlatform } from './usePlatform'
import { GLOBAL_SHORTCUTS } from '../shortcuts'
import { MODULE_KEYS, type ModuleKey } from '../types/nav'

export interface AppShortcutOptions {
  /** 切换主题（由壳层向上 emit） */
  onToggleTheme: () => void
  /** 切换模块（AppWrapper 内部实现 / RecycleLayout 跳回主页） */
  onModuleChange: (module: ModuleKey) => void
  /** 打开快捷键帮助面板 */
  onOpenHelp: () => void
  /**
   * 锁定密码库（M4-1：由调用方注入，本 composable 不直接依赖 vault store）。
   * 密码库是 bookmark 模块的能力，壳层知道它、通用 composable 不必知道。
   */
  onLockVault?: () => void
}

export function useAppShortcuts(opts: AppShortcutOptions) {
  const router = useRouter()
  const { isWindows } = usePlatform()

  // 平台同步到快捷键引擎
  setShortcutPlatform(isWindows.value ? 'win' : 'mac')

  const find = (action: string) => GLOBAL_SHORTCUTS.find((s) => s.action === action)

  // 模块切换：⌘1~⌘4
  const modules = MODULE_KEYS
  modules.forEach((m) => {
    const def = find(`module.${m}`)
    if (!def) return
    useKeyboardShortcut(def.action, def.chord, () => opts.onModuleChange(m))
  })

  // 音乐：⌘5 → /music
  const musicDef = find('module.music')
  if (musicDef) {
    useKeyboardShortcut(musicDef.action, musicDef.chord, () => router.push('/music'))
  }

  // 新建便签：⌘⇧N → 切到便签模块
  const newNoteDef = find('note.new')
  if (newNoteDef) {
    useKeyboardShortcut(newNoteDef.action, newNoteDef.chord, () => opts.onModuleChange('notes'))
  }

  // 切换主题：⌘⇧T
  const themeDef = find('theme.toggle')
  if (themeDef) {
    useKeyboardShortcut(themeDef.action, themeDef.chord, () => opts.onToggleTheme(), {
      skipWhenEditing: themeDef.skipWhenEditing,
    })
  }

  // 锁定密码库：⌘⇧L（未注入回调时该项不注册）
  const lockDef = find('vault.lock')
  if (lockDef && opts.onLockVault) {
    useKeyboardShortcut(lockDef.action, lockDef.chord, () => opts.onLockVault?.(), {
      skipWhenEditing: lockDef.skipWhenEditing,
    })
  }

  // 帮助：⌘/
  const helpDef = find('help')
  if (helpDef) {
    useKeyboardShortcut(helpDef.action, helpDef.chord, () => opts.onOpenHelp(), {
      skipWhenEditing: helpDef.skipWhenEditing,
    })
  }
}
