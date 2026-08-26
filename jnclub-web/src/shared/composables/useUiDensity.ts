import { ref, watch } from 'vue'
import { useUserPreferences } from './useUserPreferences'

export type UiDensity = 'comfortable' | 'compact'

/**
 * useUiDensity — 全局密度偏好（comfortable / compact）
 * 使用模块级 ref 共享状态，SideNav 与 AppWrapper 都能拿到同一份实时值。
 */
const density = ref<UiDensity>('comfortable')
let synced = false

export function useUiDensity() {
  const prefs = useUserPreferences()

  const syncFromPrefs = () => {
    density.value = prefs.get<UiDensity>('ui.density', 'comfortable')
    synced = true
  }

  // 后端偏好加载完成后，以后端权威值为准
  watch(
    () => prefs.ready.value,
    (ready) => {
      if (ready && !synced) syncFromPrefs()
    },
    { immediate: true },
  )

  const toggle = async () => {
    const next: UiDensity = density.value === 'compact' ? 'comfortable' : 'compact'
    density.value = next
    await prefs.set('ui.density', next)
  }

  return { density, toggle }
}