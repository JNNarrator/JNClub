/**
 * useDevtoolsStore — 开发者工具的标签页状态（含持久化）
 *
 * 迁移自 jnx `src/stores/tools.ts`，关键差异：
 * 1. jnx 的 store 没有任何持久化（Tauri 桌面端靠 KeepAlive 保活），
 *    JNClub 是 Web 环境，`App.vue` 对 `<router-view>` 用了 `:key="route.path"`，
 *    路由切换/刷新都会重建组件 —— 因此标签页与激活项必须外部持久化。
 * 2. 启动台（devtools-home）不是标签页：`activeTabId === HOME_ID` 表示停在启动台，
 *    `openTabIds` 只包含真实工具，避免出现一个不可关闭的伪标签。
 * 3. 持久化走 `useUserPreferences`（key `devtools.tabs`，后端 user_preference KV），
 *    它内部同时写 localStorage，所以刷新/断网/未登录场景都能恢复。
 *
 * 恢复顺序（重要）：
 *   hydrate() 先用同步 `get()` 读 localStorage 立即恢复（避免首屏闪烁），
 *   再 `await prefs.load()` 以后端为准校正一次。在 `hydrated` 置位前禁止写盘，
 *   否则首屏的默认值会把后端已存的标签页覆盖掉。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ALL_TOOLS, DEVTOOLS_HOME, TOOL_IDS, type ToolTab } from '../types'
import { useUserPreferences } from '@/shared/composables/useUserPreferences'
import { useRecentTools } from '../composables/useRecentTools'

const HOME_ID = DEVTOOLS_HOME.id
const PREF_KEY = 'devtools.tabs'
/** 标签页数量上限，防止误操作把内存撑爆 */
const MAX_OPEN_TABS = 12

interface PersistedTabs {
  openTabIds: string[]
  activeTabId: string
}

export const useDevtoolsStore = defineStore('devtools', () => {
  const prefs = useUserPreferences()
  const { recordToolUse } = useRecentTools()

  const openTabIds = ref<string[]>([])
  const activeTabId = ref<string>(HOME_ID)
  const hydrated = ref(false)

  const allTabs = ALL_TOOLS
  /** 已打开且仍存在的工具（过滤掉被删掉的工具 id） */
  const openTabs = computed<ToolTab[]>(() =>
    openTabIds.value.map((id) => ALL_TOOLS.find((t) => t.id === id)).filter((t): t is ToolTab => !!t),
  )
  const activeTab = computed<ToolTab | undefined>(() =>
    activeTabId.value === HOME_ID ? undefined : ALL_TOOLS.find((t) => t.id === activeTabId.value),
  )
  const isHomeActive = computed(() => activeTabId.value === HOME_ID)

  /* ─── 持久化 ─── */
  let persistTimer: ReturnType<typeof setTimeout> | null = null

  function sanitize(data: unknown): PersistedTabs | null {
    if (!data || typeof data !== 'object') return null
    const raw = data as Partial<PersistedTabs>
    const ids = Array.isArray(raw.openTabIds)
      ? raw.openTabIds.filter((id): id is string => typeof id === 'string' && TOOL_IDS.includes(id))
      : []
    // 去重并保序
    const unique = Array.from(new Set(ids)).slice(0, MAX_OPEN_TABS)
    const active =
      typeof raw.activeTabId === 'string' && (raw.activeTabId === HOME_ID || unique.includes(raw.activeTabId))
        ? raw.activeTabId
        : unique[unique.length - 1] ?? HOME_ID
    return { openTabIds: unique, activeTabId: active }
  }

  function apply(data: PersistedTabs | null): void {
    if (!data) return
    openTabIds.value = data.openTabIds
    activeTabId.value = data.activeTabId
  }

  function snapshot(): PersistedTabs {
    return { openTabIds: [...openTabIds.value], activeTabId: activeTabId.value }
  }

  function persistNow(): void {
    if (persistTimer) {
      clearTimeout(persistTimer)
      persistTimer = null
    }
    if (!hydrated.value) return
    void prefs.set(PREF_KEY, snapshot())
  }

  /** 防抖写盘：连续切标签时只落最后一次 */
  function schedulePersist(): void {
    if (!hydrated.value) return
    if (persistTimer) clearTimeout(persistTimer)
    persistTimer = setTimeout(() => {
      persistTimer = null
      void prefs.set(PREF_KEY, snapshot())
    }, 250)
  }

  /**
   * 恢复标签页状态。幂等，可被 DevtoolsLayout 在每次挂载时调用。
   * @returns 恢复后的激活标签 id
   */
  async function hydrate(): Promise<string> {
    if (hydrated.value) return activeTabId.value

    // 第一段：同步读 localStorage（prefs.get 在未 load 完成时走 localStorage）
    apply(sanitize(prefs.get<PersistedTabs | null>(PREF_KEY, null)))

    // 第二段：以后端偏好为准（load 幂等，MainLayout 可能已在拉取，复用 inflight）
    try {
      await prefs.load()
      apply(sanitize(prefs.get<PersistedTabs | null>(PREF_KEY, null)))
    } catch {
      /* 后端不可用：保留 localStorage 结果 */
    }

    // 兜底：激活项必须存在
    if (activeTabId.value !== HOME_ID && !openTabIds.value.includes(activeTabId.value)) {
      activeTabId.value = openTabIds.value[openTabIds.value.length - 1] ?? HOME_ID
    }
    hydrated.value = true
    return activeTabId.value
  }

  /* ─── 标签页操作 ─── */
  function setActiveTab(id: string): void {
    if (id === HOME_ID) {
      activeTabId.value = HOME_ID
      schedulePersist()
      return
    }
    if (!TOOL_IDS.includes(id)) return
    if (!openTabIds.value.includes(id)) {
      openTabIds.value = [...openTabIds.value, id].slice(-MAX_OPEN_TABS)
    }
    activeTabId.value = id
    recordToolUse(id)
    schedulePersist()
  }

  /** 打开工具；若已打开则只切激活 */
  function openTool(id: string): void {
    setActiveTab(id)
  }

  function closeTab(id: string): void {
    const idx = openTabIds.value.indexOf(id)
    if (idx < 0) return
    const next = [...openTabIds.value]
    next.splice(idx, 1)
    openTabIds.value = next
    if (activeTabId.value === id) {
      // 关闭激活项：优先右邻，否则左邻，都没有则回启动台
      activeTabId.value = next[idx] ?? next[idx - 1] ?? HOME_ID
    }
    schedulePersist()
  }

  /** 关闭除指定项外的其它标签（中键/右键菜单用） */
  function closeOthers(id: string): void {
    openTabIds.value = openTabIds.value.includes(id) ? [id] : []
    activeTabId.value = openTabIds.value.length ? id : HOME_ID
    schedulePersist()
  }

  function closeAll(): void {
    openTabIds.value = []
    activeTabId.value = HOME_ID
    schedulePersist()
  }

  /** 把标签移到目标索引（标签栏拖拽排序） */
  function moveTab(from: number, to: number): void {
    const ids = [...openTabIds.value]
    if (from < 0 || from >= ids.length || to < 0 || to >= ids.length || from === to) return
    const [moved] = ids.splice(from, 1)
    ids.splice(to, 0, moved)
    openTabIds.value = ids
    schedulePersist()
  }

  function goHome(): void {
    activeTabId.value = HOME_ID
    schedulePersist()
  }

  /** 清空持久化（设置页「重置标签页」用） */
  function resetPersisted(): void {
    openTabIds.value = []
    activeTabId.value = HOME_ID
    persistNow()
  }

  return {
    HOME_ID,
    allTabs,
    openTabIds,
    openTabs,
    activeTabId,
    activeTab,
    isHomeActive,
    hydrated,
    hydrate,
    setActiveTab,
    openTool,
    closeTab,
    closeOthers,
    closeAll,
    moveTab,
    goHome,
    persistNow,
    resetPersisted,
  }
})
