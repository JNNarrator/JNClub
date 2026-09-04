/**
 * useSearchActions.ts — 全局搜索抽屉与命令面板动作
 * 收敛 Home 中的搜索状态、跳转逻辑和快捷动作。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKeyboardShortcut } from '../../../shared/composables/useKeyboardShortcut'
import { useVaultStore } from '../stores/vault'

export type SearchJumpModule = 'bookmarks' | 'notes' | 'files' | 'vault' | 'music'

export function useSearchActions(opts: {
  onModuleChange: (module: 'bookmarks' | 'notes' | 'files' | 'vault') => void
  onToggleTheme: () => void
}) {
  const router = useRouter()
  const showSearch = ref(false)
  /** 搜索结果跳转：切模块后待选中的目录 */
  const pendingDirId = ref<number | null>(null)

  // 搜索快捷键走全局注册中心（与 ⌘1~5 / ⌘⇧T 同一引擎；Home 挂载期间生效，输入框内也响应）
  useKeyboardShortcut('search', { mods: ['mod'], key: 'k' }, () => {
    showSearch.value = true
  }, { skipWhenEditing: true })

  const handleSearchJump = (module: SearchJumpModule, directoryId: number | null) => {
    if (module === 'music') {
      router.push('/music')
      return
    }
    pendingDirId.value = directoryId
    opts.onModuleChange(module)
  }

  /** 命令面板快捷动作 */
  const handleCommand = (key: string) => {
    const nav: Record<string, 'bookmarks' | 'notes' | 'files' | 'vault'> = {
      'module.bookmarks': 'bookmarks',
      'module.notes': 'notes',
      'module.files': 'files',
      'module.vault': 'vault',
    }
    switch (key) {
      case 'note.new': opts.onModuleChange('notes'); break
      case 'bookmark.new': opts.onModuleChange('bookmarks'); break
      case 'vault.lock': useVaultStore().lock(); break
      case 'theme.toggle': opts.onToggleTheme(); break
      case 'module.music': router.push('/music'); break
      case 'todo.new': router.push('/todos'); break
      case 'go.todos': router.push('/todos'); break
      case 'go.recycle': router.push('/recycle'); break
      case 'go.overview': router.push('/overview'); break
      case 'go.extension': router.push('/extension'); break
      default:
        if (nav[key]) opts.onModuleChange(nav[key])
    }
  }

  return { showSearch, pendingDirId, handleSearchJump, handleCommand }
}
