import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'jn.theme'

/** 与 index.html 首屏预判脚本里的字面量保持一致（那里 styles.css 尚未加载，读不到变量） */
const THEME_COLOR: Record<ThemeMode, string> = { dark: '#100c11', light: '#f4ecd8' }

/**
 * 初始主题优先级（PLAN-2026-19 M3-1）：
 *   `?theme=`（内嵌时由 JNClub 主站传入）> 本地偏好 > 默认暗色。
 * 用户在页面内手动切换后由 watch 写本地偏好；主站在 iframe 内再次切主题时
 * 用 postMessage 覆盖（见 App.vue 的 `jn:theme` 监听）。
 */
function detectInitial(): ThemeMode {
  if (typeof window === 'undefined') return 'dark'
  const fromUrl = new URLSearchParams(window.location.search).get('theme')
  if (fromUrl === 'light' || fromUrl === 'dark') return fromUrl
  const saved = window.localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return 'dark'
}

function apply(mode: ThemeMode) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.setAttribute('data-theme', mode)
  // color-scheme 影响滚动条与原生控件配色，必须与主题同步
  root.style.colorScheme = mode
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', THEME_COLOR[mode])
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(detectInitial())
  apply(mode.value)

  watch(mode, (m) => {
    apply(m)
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, m)
    }
  })

  function toggle() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
  }

  function set(next: ThemeMode) {
    mode.value = next
  }

  return { mode, toggle, set }
})
