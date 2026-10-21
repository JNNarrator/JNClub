/**
 * useToolDraft.ts — 工具状态记忆 composable（浏览器版）
 *
 * 迁移自 jnx `src/composables/useToolDraft.ts`。jnx 用 Tauri SQLite（plugin-sql）
 * 存草稿，JNClub 是纯 Web 环境，改为 localStorage：
 * - key 前缀 `jnclub-devtools:draft:`，value 为 `{ payload, updatedAt }`
 * - 保留原实现的三道 guard（hydrating / suppressNextWrite / flushing）与防抖写盘，
 *   避免 hydrate 期间用 initial 覆盖磁盘草稿、以及 reset 后被 watch 写回
 * - 保留 30 天过期清理（对应 jnx 的 pruneToolStates）
 *
 * 生命周期时序：
 *   1. setup 同步：state = ref(clone(initial)), isRestored = false, isHydrating = true
 *   2. setup 内 onMounted：读 localStorage → 命中则 merge 进 state → isRestored = true
 *   3. watch(state, deep)：仅 isRestored && !isHydrating && enabled 时防抖写盘
 *   4. onDeactivated / onBeforeUnmount：取消防抖并立即 flush
 *   5. onActivated：仅恢复 watcher（不重新 hydrate）
 *   6. resetDraft：state 回 initial → 删 localStorage → 抑制下一次 watch
 */
import {
  ref,
  watch,
  computed,
  onMounted,
  onBeforeUnmount,
  onActivated,
  onDeactivated,
  type Ref,
  type WatchStopHandle,
} from 'vue'
import { useDevtoolsSettingsStore } from '../stores/settings'

const STORAGE_PREFIX = 'jnclub-devtools:draft:'
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000

export interface UseToolDraftOptions<T> {
  /** 写盘防抖间隔（毫秒），默认 800 */
  debounceMs?: number
  /**
   * 是否启用记忆。关闭时不 hydrate、不写盘。可传响应式 Ref。
   * 不传时跟随「工具设置 → 记住输入内容」的总开关（settings.values.toolMemory）。
   */
  enabled?: Ref<boolean> | boolean
  /** 存盘前脱敏/裁剪（剔除敏感字段、超大字段）。默认无操作 */
  sanitize?: (state: T) => T
  /** 读盘后校验形状，防止旧版本/脏数据 hydrate 出错 */
  validate?: (raw: unknown) => raw is T
  /** payload 大小保护阈值（字节），超过则拒存并 console.warn。默认 1 MB */
  maxPayloadBytes?: number
}

export interface UseToolDraftReturn<T> {
  state: Ref<T>
  resetDraft: () => void
  isRestored: Ref<boolean>
}

interface StoredDraft {
  payload: string
  updatedAt: number
}

function readRaw(toolId: string): StoredDraft | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + toolId)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<StoredDraft>
    if (typeof parsed?.payload !== 'string') return null
    const updatedAt = typeof parsed.updatedAt === 'number' ? parsed.updatedAt : 0
    if (updatedAt && Date.now() - updatedAt > MAX_AGE_MS) {
      localStorage.removeItem(STORAGE_PREFIX + toolId)
      return null
    }
    return { payload: parsed.payload, updatedAt }
  } catch {
    return null
  }
}

function writeRaw(toolId: string, payload: string): void {
  try {
    const entry: StoredDraft = { payload, updatedAt: Date.now() }
    localStorage.setItem(STORAGE_PREFIX + toolId, JSON.stringify(entry))
  } catch {
    /* 配额超限：静默降级，内存态仍可用 */
  }
}

function removeRaw(toolId: string): void {
  try {
    localStorage.removeItem(STORAGE_PREFIX + toolId)
  } catch {
    /* 忽略 */
  }
}

/** 清空所有工具草稿（设置页「清除全部记忆」用） */
export function clearAllDrafts(): void {
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k && k.startsWith(STORAGE_PREFIX)) keys.push(k)
    }
    keys.forEach((k) => localStorage.removeItem(k))
  } catch {
    /* 忽略 */
  }
}

/* ─── Singleton: 启动时全局调用一次，清理过期草稿 ─── */
let _pruned = false
export function pruneAllDrafts(): void {
  if (_pruned) return
  _pruned = true
  try {
    const stale: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (!k || !k.startsWith(STORAGE_PREFIX)) continue
      const entry = readRaw(k.slice(STORAGE_PREFIX.length))
      if (entry === null) stale.push(k)
    }
    stale.forEach((k) => localStorage.removeItem(k))
  } catch {
    /* 忽略 */
  }
}

export function useToolDraft<T extends Record<string, any>>(
  toolId: string,
  initial: T,
  opts: UseToolDraftOptions<T> = {},
): UseToolDraftReturn<T> {
  const {
    debounceMs = 800,
    enabled,
    sanitize,
    validate,
    maxPayloadBytes = 1_048_576,
  } = opts

  // 总开关：设置页关掉「记住输入内容」后，所有工具立刻停止读写草稿。
  // getActivePinia 在非组件上下文（如单测）可能为 null，此时按开启处理。
  const memoryEnabled = computed<boolean>(() => {
    try {
      return useDevtoolsSettingsStore().values.toolMemory
    } catch {
      return true
    }
  })

  const enabledRef: Ref<boolean> =
    enabled === undefined
      ? memoryEnabled
      : typeof enabled === 'boolean'
        ? ref(enabled)
        : enabled

  const state = ref<T>(structuredClone(initial)) as Ref<T>
  const isRestored = ref(false)
  const isHydrating = ref(true)

  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let flushing = false
  let suppressNextWrite = false
  let watchStop: WatchStopHandle | null = null

  function writeNow(): void {
    if (flushing) return
    if (suppressNextWrite) {
      suppressNextWrite = false
      return
    }
    if (!enabledRef.value) return
    flushing = true
    try {
      const payload = sanitize ? sanitize(state.value) : state.value
      const json = JSON.stringify(payload)
      if (json.length > maxPayloadBytes) {
        console.warn(
          `[useToolDraft] payload for "${toolId}" exceeds ${maxPayloadBytes} bytes (${json.length}), skipping save`,
        )
        return
      }
      writeRaw(toolId, json)
    } catch {
      /* 序列化失败（循环引用等）：跳过本次 */
    } finally {
      flushing = false
    }
  }

  function scheduleWrite(): void {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(writeNow, debounceMs)
  }

  function cancelWrite(): void {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  function startWatcher(): void {
    if (watchStop) return
    watchStop = watch(
      state,
      () => {
        if (!isRestored.value || isHydrating.value) return
        if (suppressNextWrite) {
          suppressNextWrite = false
          return
        }
        if (flushing) return
        if (!enabledRef.value) return
        scheduleWrite()
      },
      { deep: true },
    )
  }

  function stopWatcher(): void {
    if (watchStop) {
      watchStop()
      watchStop = null
    }
  }

  function flushWrite(): void {
    cancelWrite()
    writeNow()
  }

  onMounted(() => {
    isHydrating.value = true
    try {
      const entry = readRaw(toolId)
      if (entry) {
        const parsed: unknown = JSON.parse(entry.payload)
        if (parsed && typeof parsed === 'object') {
          if (validate ? validate(parsed) : true) {
            state.value = { ...structuredClone(initial), ...(parsed as object) } as T
          }
        }
      }
    } catch {
      /* 脏数据：忽略，用 initial */
    } finally {
      isHydrating.value = false
      isRestored.value = true
    }
  })

  startWatcher()

  onDeactivated(() => {
    flushWrite()
    stopWatcher()
  })

  onActivated(() => {
    if (!watchStop) startWatcher()
  })

  onBeforeUnmount(() => {
    flushWrite()
    stopWatcher()
  })

  function resetDraft(): void {
    cancelWrite()
    suppressNextWrite = true
    state.value = structuredClone(initial) as T
    suppressNextWrite = false
    removeRaw(toolId)
  }

  return { state, resetDraft, isRestored }
}
