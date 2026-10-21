/**
 * useRecentTools.ts — 「继续上次」最近使用工具
 *
 * 迁移自 jnx `src/composables/useRecentTools.ts`，仅换 localStorage 前缀。
 * 独立于 `shared/composables/useRecentItems`（后者 kind 固定为 note/bookmark/file）。
 */
import { computed, ref } from 'vue'
import { ALL_TOOLS } from '../types'

const STORAGE_KEY = 'jnclub-devtools:recent-tools'
const MAX_ENTRIES = 8

export interface RecentToolEntry {
  toolId: string
  lastUsedAt: number
  useCount: number
}

function readStorage(): RecentToolEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (e): e is RecentToolEntry =>
        !!e && typeof e.toolId === 'string' && typeof e.lastUsedAt === 'number',
    )
  } catch {
    return []
  }
}

function writeStorage(entries: RecentToolEntry[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries))
  } catch {
    /* 配额超限：静默 */
  }
}

const recent = ref<RecentToolEntry[]>(readStorage())

export function useRecentTools() {
  const recentTools = computed(() => {
    const map = new Map(ALL_TOOLS.map((t) => [t.id, t]))
    return recent.value
      .map((e) => ({ ...e, tool: map.get(e.toolId) }))
      .filter((e): e is RecentToolEntry & { tool: NonNullable<ReturnType<typeof map.get>> } => !!e.tool)
  })

  function recordToolUse(toolId: string): void {
    const now = Date.now()
    const idx = recent.value.findIndex((e) => e.toolId === toolId)
    if (idx >= 0) {
      recent.value[idx].lastUsedAt = now
      recent.value[idx].useCount++
    } else {
      recent.value.unshift({ toolId, lastUsedAt: now, useCount: 1 })
    }
    recent.value.sort((a, b) => b.lastUsedAt - a.lastUsedAt)
    if (recent.value.length > MAX_ENTRIES) recent.value.length = MAX_ENTRIES
    writeStorage(recent.value)
  }

  function clearRecent(): void {
    recent.value = []
    writeStorage([])
  }

  return { recentTools, recordToolUse, clearRecent }
}
