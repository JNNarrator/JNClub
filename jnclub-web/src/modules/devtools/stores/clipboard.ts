/**
 * useClipboardStore — 剪贴板历史（浏览器版）
 *
 * jnx 是 Tauri 桌面端：用 `plugin-clipboard-manager` 的 `readText()` 后台轮询，
 * 历史落在 SQLite `clipboard_history` 表。浏览器没有等价能力：
 *
 * - `navigator.clipboard.readText()` 需要**用户手势 + 权限授予**，且页面失焦时
 *   读取会抛 `NotAllowedError`，所以无法像桌面端那样静默后台监听。
 * - 因此这里做降级：历史存 localStorage（上限 200 条），并提供
 *   `capture()` 手动抓取；`startWatching()` 只在**页面可见且有权限**时轮询，
 *   一旦读取失败就自动暂停并把原因暴露给 UI（`lastError`），不做无限重试。
 *
 * 对外 API 与 jnx 版保持一致（items / loading / watching / loadHistory /
 * startWatching / stopWatching / removeItem / clearAll），另外新增 capture()。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const STORAGE_KEY = 'jnclub-devtools:clipboard-history'
const MAX_ITEMS = 200
const DEFAULT_INTERVAL = 2000

export interface ClipboardItem {
  /** 单调递增序号，仅用于 v-for key 与删除定位 */
  id: number
  content: string
  source: string
  /** UTC 时间串（无 Z 后缀，与 jnx 一致，展示侧自行补 Z） */
  created_at: string
}

function utcStamp(d = new Date()): string {
  return d.toISOString().slice(0, 19).replace('T', ' ')
}

function readStorage(): ClipboardItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (it): it is ClipboardItem =>
        !!it && typeof it.content === 'string' && typeof it.id === 'number',
    )
  } catch {
    return []
  }
}

function writeStorage(items: ClipboardItem[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_ITEMS)))
  } catch {
    /* 配额超限：静默降级，内存态仍可用 */
  }
}

/** 是否有可用的异步剪贴板读取能力 */
function canReadClipboard(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    !!navigator.clipboard &&
    typeof navigator.clipboard.readText === 'function'
  )
}

export const useClipboardStore = defineStore('devtoolsClipboard', () => {
  const items = ref<ClipboardItem[]>(readStorage())
  const loading = ref(false)
  const watching = ref(false)
  /** 最近一次监听失败原因（null 表示正常） */
  const lastError = ref<string | null>(null)

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let lastContent = items.value[0]?.content ?? ''

  const available = computed(canReadClipboard)
  const count = computed(() => items.value.length)

  function nextId(): number {
    return items.value.reduce((max, it) => Math.max(max, it.id), 0) + 1
  }

  function addEntry(content: string, source = '监听'): ClipboardItem | null {
    const text = content ?? ''
    if (!text.trim()) return null
    // 已在最前面则只更新时间，避免重复刷屏
    const head = items.value[0]
    if (head && head.content === text) {
      head.created_at = utcStamp()
      writeStorage(items.value)
      return head
    }
    const entry: ClipboardItem = {
      id: nextId(),
      content: text,
      source,
      created_at: utcStamp(),
    }
    items.value = [entry, ...items.value].slice(0, MAX_ITEMS)
    writeStorage(items.value)
    lastContent = text
    return entry
  }

  function loadHistory(): void {
    loading.value = true
    try {
      items.value = readStorage()
      lastContent = items.value[0]?.content ?? ''
    } finally {
      loading.value = false
    }
  }

  /**
   * 手动抓取一次剪贴板并入库。
   * @returns 成功返回条目，失败返回 null（原因写入 lastError）
   */
  async function capture(source = '手动'): Promise<ClipboardItem | null> {
    if (!canReadClipboard()) {
      lastError.value = '当前浏览器不支持读取剪贴板，请直接粘贴到工具中'
      return null
    }
    try {
      const text = await navigator.clipboard.readText()
      lastError.value = null
      if (!text.trim()) {
        lastError.value = '剪贴板为空'
        return null
      }
      return addEntry(text, source)
    } catch (err) {
      const name = (err as { name?: string })?.name
      lastError.value =
        name === 'NotAllowedError'
          ? '浏览器拒绝了剪贴板读取（需页面聚焦并授权）'
          : '读取剪贴板失败'
      return null
    }
  }

  function startWatching(intervalMs = DEFAULT_INTERVAL): void {
    if (!canReadClipboard()) {
      lastError.value = '当前浏览器不支持后台读取剪贴板，可手动记录'
      return
    }
    if (watching.value || pollTimer) return
    watching.value = true
    lastError.value = null

    pollTimer = setInterval(async () => {
      // 页面不可见时不读，避免无谓的权限报错
      if (typeof document !== 'undefined' && document.visibilityState !== 'visible') return
      try {
        const text = await navigator.clipboard.readText()
        if (text && text !== lastContent) {
          lastContent = text
          addEntry(text, '监听')
        }
        lastError.value = null
      } catch (err) {
        const name = (err as { name?: string })?.name
        // 权限被拒：自动停止，避免每 2s 抛一次错
        lastError.value =
          name === 'NotAllowedError'
            ? '剪贴板读取被拒绝，监听已暂停（点击「记录当前」可手动记录）'
            : '读取剪贴板失败'
        stopWatching()
      }
    }, intervalMs)
  }

  function stopWatching(): void {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    watching.value = false
  }

  function removeItem(id: number): void {
    items.value = items.value.filter((it) => it.id !== id)
    writeStorage(items.value)
  }

  function clearAll(): void {
    items.value = []
    lastContent = ''
    writeStorage([])
  }

  return {
    items,
    loading,
    watching,
    lastError,
    available,
    count,
    loadHistory,
    capture,
    startWatching,
    stopWatching,
    removeItem,
    clearAll,
  }
})
