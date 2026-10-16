import { ref } from 'vue'
import axios from 'axios'

/**
 * useUserPreferences — 通用用户偏好（后端 user_preference 通用 KV，JSON 值）
 * 后端为权威；localStorage 仅作首屏快速恢复兜底（load 完成后以后端为准）
 * Key 规范：模块.场景（如 module.activeModule、view.notes）
 */
const cache = ref<Record<string, any>>({})
let loaded = false
/** 进行中的请求：并发调用 load() 时复用同一个 Promise，避免重复打后端 */
let inflight: Promise<void> | null = null
const ready = ref(false)

export function useUserPreferences() {
  /**
   * 拉取后端偏好（幂等）。
   * 之所以要幂等：load() 由 MainLayout 在挂载时调用，而 MainLayout 是
   * AppWrapper / OverviewLayout / RecycleLayout 三个页壳的共同根，
   * 路由切换会让它重新挂载 —— 没有这道保护就会每次切页都重发一次请求。
   */
  const load = async () => {
    if (loaded) return
    if (inflight) return inflight
    inflight = (async () => {
      try {
        const res = await axios.get('/api/user-preferences')
        if (res.data.code === 200) {
          cache.value = res.data.data || {}
          loaded = true
        }
      } catch {
        /* 静默：未加载时由 localStorage 兜底 */
      } finally {
        ready.value = true
        inflight = null
      }
    })()
    return inflight
  }

  const get = <T = any>(key: string, fallback: T): T => {
    if (loaded && key in cache.value) return cache.value[key] as T
    try {
      const raw = localStorage.getItem('jn-pref-' + key)
      if (raw !== null) return JSON.parse(raw) as T
    } catch { /* 忽略非法 JSON */ }
    return fallback
  }

  const set = async (key: string, value: any) => {
    cache.value[key] = value
    try { localStorage.setItem('jn-pref-' + key, JSON.stringify(value)) } catch { /* 忽略 */ }
    try {
      await axios.put('/api/user-preferences', [{ key, value }])
    } catch {
      /* 静默：下次 load 以服务端为准 */
    }
  }

  return { load, get, set, ready }
}
