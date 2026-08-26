/**
 * useRecentItems.ts — 「最近打开」本地记录（单例）
 * 记录最近访问过的便签 / 收藏 / 文件 / 待办，供搜索抽屉空输入时快速回跳。
 * 纯 localStorage 记录（不新增后端接口）；按 kind-id 去重置顶，最多保留 12 条。
 */
import { ref } from 'vue'

export type RecentItemKind = 'note' | 'bookmark' | 'file' | 'todo'

export interface RecentItem {
  /** 唯一键：${kind}-${id} */
  key: string
  kind: RecentItemKind
  id: number
  title: string
  /** 仅收藏需要外链跳转 */
  url?: string
  /** 最近访问时间戳 */
  at: number
}

const STORAGE_KEY = 'jn-recent-open'
const MAX_ITEMS = 12

const items = ref<RecentItem[]>(load())

function load(): RecentItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const list: unknown = JSON.parse(raw)
    if (!Array.isArray(list)) return []
    return (list as RecentItem[]).filter((i): i is RecentItem =>
      !!i
      && typeof i.key === 'string'
      && (i.kind === 'note' || i.kind === 'bookmark' || i.kind === 'file' || i.kind === 'todo')
      && typeof i.id === 'number'
      && typeof i.title === 'string')
  } catch {
    return []
  }
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items.value))
  } catch {
    /* localStorage 不可用（隐私模式等）时静默失败 */
  }
}

/** 记入最近打开：同一条（kind-id）去重置顶，超出上限裁掉最旧 */
function record(item: Omit<RecentItem, 'key' | 'at'>) {
  const key = `${item.kind}-${item.id}`
  items.value = [
    { ...item, key, at: Date.now() },
    ...items.value.filter(i => i.key !== key),
  ].slice(0, MAX_ITEMS)
  persist()
}

function clear() {
  items.value = []
  persist()
}

/** 模块级单例：各处引入共享同一份响应式数据 */
export function useRecentItems() {
  return { items, record, clear }
}