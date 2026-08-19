/**
 * useAppShortcuts.ts — JNClub 应用级全局快捷键注册（壳层：AppWrapper / RecycleLayout 共用）
 * - 平台检测同步进快捷键引擎（mod = ⌘ / Ctrl）
 * - 模块切换、音乐跳转、新建便签、切换主题、锁定密码库、帮助面板
 * - 搜索快捷键由 Home 注册（搜索抽屉挂在 Home 内）
 */
import { useRouter } from 'vue-router'
import { useKeyboardShortcut, setShortcutPlatform } from './useKeyboardShortcut'
import { usePlatform } from './usePlatform'
import { useVaultStore } from '../../modules/bookmark/stores/vault'
import { GLOBAL_SHORTCUTS } from '../shortcuts'

export interface AppShortcutOptions {
  /** 切换主题（由壳层向上 emit） */
  onToggleTheme: () => void
  /** 切换模块（AppWrapper 内部实现 / RecycleLayout 跳回主页） */
  onModuleChange: (module: 'bookmarks' | 'notes' | 'files' | 'vault') => void
  /** 打开快捷键帮助面板 */
  onOpenHelp: () => void
}

export function useAppShortcuts(opts: AppShortcutOptions) {
  const router = useRouter()
  const { isWindows } = usePlatform()

  // 平台同步到快捷键引擎
  setShortcutPlatform(isWindows.value ? 'win' : 'mac')

  const find = (action: string) => GLOBAL_SHORTCUTS.find((s) => s.action === action)

  // 模块切换：⌘1~⌘4
  const modules: Array<'bookmarks' | 'notes' | 'files' | 'vault'> = ['bookmarks', 'notes', 'files', 'vault']
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

  // 锁定密码库：⌘⇧L
  const lockDef = find('vault.lock')
  if (lockDef) {
    useKeyboardShortcut(lockDef.action, lockDef.chord, () => {
      const vault = useVaultStore()
      if (vault.masterStatus?.unlocked) vault.lock()
    }, { skipWhenEditing: lockDef.skipWhenEditing })
  }

  // 帮助：⌘/
  const helpDef = find('help')
  if (helpDef) {
    useKeyboardShortcut(helpDef.action, helpDef.chord, () => opts.onOpenHelp(), {
      skipWhenEditing: helpDef.skipWhenEditing,
    })
  }
}
