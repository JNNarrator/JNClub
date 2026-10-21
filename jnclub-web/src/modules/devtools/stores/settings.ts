/**
 * useDevtoolsSettingsStore — 开发者工具的偏好设置
 *
 * 迁移自 jnx `src/stores/settings.ts`，做减法：
 * - **不迁主题设置**：JNClub 主题由 `App.vue` + `localStorage['theme']` 统一管理，
 *   在这里再放一份会双写冲突。
 * - **不迁侧栏宽度/折叠**：JNClub 的 SideNav 已有自己的 prefs。
 * - **不迁标签栏行数/每行数量**：Web 端标签栏是单行横向滚动，不需要分页参数。
 *
 * 保留并新增的都是真实生效的项：
 * - `toolMemory`：工具草稿记忆总开关（useToolDraft 的默认 enabled）
 * - `reopenLastTab`：进入开发者工具时是否自动回到上次的标签页
 * - `clipboardInterval`：剪贴板监听轮询间隔（浏览器端只在页面可见时轮询，间隔可调）
 */
import { defineStore } from 'pinia'
import { computed } from 'vue'
import { useUserPreferences } from '@/shared/composables/useUserPreferences'

const PREF_KEY = 'devtools.settings'

export interface DevtoolsSettings {
  /** 工具草稿记忆总开关 */
  toolMemory: boolean
  /** 进入开发者工具时是否恢复上次的标签页（false 则总是停在启动台） */
  reopenLastTab: boolean
  /** 剪贴板监听轮询间隔（毫秒），范围 500 ~ 30000 */
  clipboardInterval: number
}

export const DEFAULT_DEVTOOLS_SETTINGS: DevtoolsSettings = {
  toolMemory: true,
  reopenLastTab: true,
  clipboardInterval: 2000,
}

export const CLIPBOARD_INTERVAL_MIN = 500
export const CLIPBOARD_INTERVAL_MAX = 30_000

function clampInterval(v: unknown): number {
  const n = typeof v === 'number' && Number.isFinite(v) ? Math.round(v) : DEFAULT_DEVTOOLS_SETTINGS.clipboardInterval
  return Math.min(CLIPBOARD_INTERVAL_MAX, Math.max(CLIPBOARD_INTERVAL_MIN, n))
}

export const useDevtoolsSettingsStore = defineStore('devtoolsSettings', () => {
  const prefs = useUserPreferences()

  const values = computed<DevtoolsSettings>(() => {
    const stored = prefs.get<Partial<DevtoolsSettings> | null>(PREF_KEY, null)
    if (!stored || typeof stored !== 'object') return { ...DEFAULT_DEVTOOLS_SETTINGS }
    return {
      toolMemory:
        typeof stored.toolMemory === 'boolean' ? stored.toolMemory : DEFAULT_DEVTOOLS_SETTINGS.toolMemory,
      reopenLastTab:
        typeof stored.reopenLastTab === 'boolean'
          ? stored.reopenLastTab
          : DEFAULT_DEVTOOLS_SETTINGS.reopenLastTab,
      clipboardInterval: clampInterval(stored.clipboardInterval),
    }
  })

  /** 读取一次后端偏好（幂等） */
  async function load(): Promise<void> {
    try {
      await prefs.load()
    } catch {
      /* 后端不可用：localStorage 兜底 */
    }
  }

  async function update<K extends keyof DevtoolsSettings>(key: K, value: DevtoolsSettings[K]): Promise<void> {
    await prefs.set(PREF_KEY, { ...values.value, [key]: value })
  }

  async function reset(): Promise<void> {
    await prefs.set(PREF_KEY, { ...DEFAULT_DEVTOOLS_SETTINGS })
  }

  return { values, load, update, reset }
})
