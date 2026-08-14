/**
 * usePlatform — 平台检测 composable
 * 单例模式，提供 isWindows / isMac 等平台信息，用于运行时性能优化决策。
 * 通过在 <html> 上设置 data-platform 属性，CSS 可以据此做平台特定优化。
 */
import { ref } from 'vue'

export type Platform = 'mac' | 'windows' | 'linux' | 'unknown'

const platform = ref<Platform>('unknown')
const isWindows = ref(false)
const isMac = ref(false)
const isLinux = ref(false)
let inited = false

function detect(): Platform {
  const ua = navigator.userAgent.toLowerCase()
  if (ua.includes('win') || ua.includes('windows')) return 'windows'
  if (ua.includes('mac') || ua.includes('darwin')) return 'mac'
  if (ua.includes('linux') || ua.includes('x11')) return 'linux'
  return 'unknown'
}

function init() {
  if (inited) return
  inited = true
  const p = detect()
  platform.value = p
  isWindows.value = p === 'windows'
  isMac.value = p === 'mac'
  isLinux.value = p === 'linux'
  // 在 <html> 上设置 data-platform 属性，供 CSS 选择器使用
  document.documentElement.setAttribute('data-platform', p)
}

export function usePlatform() {
  return {
    platform,
    isWindows,
    isMac,
    isLinux,
    init,
  }
}
