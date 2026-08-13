import { ref } from 'vue'
import { useUserPreferences } from './useUserPreferences'

/**
 * useParticlesSettings — 粒子背景全局单例状态
 * 风格偏好 particles.style（'none' | 'float-dots' | 'snow' | 'petals'）走 useUserPreferences 持久化
 * enabled 需同时满足：style 非 none、非触屏（pointer:coarse）、非 prefers-reduced-motion
 */
export type ParticlesStyle = 'none' | 'float-dots' | 'snow' | 'petals'

const prefs = useUserPreferences()

/** 当前风格（由偏好驱动，设置抽屉可切换） */
const style = ref<ParticlesStyle>('none')

/** 触屏设备（无 hover 且性能弱，默认关闭） */
const coarsePointer = ref(false)
/** 用户偏好"减少动态效果"：关闭粒子 */
const reducedMotion = ref(false)

let inited = false

function init() {
  if (inited) return
  inited = true
  coarsePointer.value = window.matchMedia('(pointer: coarse)').matches
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  style.value = prefs.get<ParticlesStyle>('particles.style', 'none')
}

/** 切换风格并持久化（后端 + localStorage） */
function setStyle(s: ParticlesStyle) {
  style.value = s
  prefs.set('particles.style', s)
}

export function useParticlesSettings() {
  return {
    style,
    coarsePointer,
    reducedMotion,
    init,
    setStyle,
  }
}
