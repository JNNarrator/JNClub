import { ref } from 'vue'
import { useUserPreferences } from './useUserPreferences'

/**
 * useCustomCursor — 全局自定义光标单例状态（可爱光标）
 * CursorHost.vue 渲染主光标 + 光环/emoji，本 composable 管理位置与交互状态：
 *   - 风格偏好 cursor.style（'dot-halo' | 'emoji' | 'crosshair' | 'ring' | 'star'）走 useUserPreferences 持久化
 *   - 点击特效 cursor.clickEffect（'none' | 'star' | 'heart' | 'flower'）
 *   - 轨迹特效 cursor.trailEffect（'none' | 'rainbow' | 'brand' | 'pastel'）
 *   - 触屏设备（pointer: coarse）自动禁用（无 hover 概念）
 *   - 输入框内隐藏自定义光标，露出原生 I-beam
 * 主光标即时跟随 mousemove；光环/emoji 由宿主的 rAF 循环 lerp 延迟追赶（弹性拖尾）。
 */
export type CursorStyle = 'dot-halo' | 'emoji' | 'crosshair' | 'ring' | 'star'
export type ClickEffectType = 'none' | 'star' | 'heart' | 'flower'
export type TrailEffectType = 'none' | 'rainbow' | 'brand' | 'pastel'

const prefs = useUserPreferences()

/** 触屏/无精细指针设备禁用自定义光标 */
export const cursorEnabled = ref(false)
/** 是否显示（鼠标在窗口内） */
export const cursorVisible = ref(false)
/** 按下状态 */
export const cursorPressed = ref(false)
/** 是否悬停可交互元素（光环放大 / emoji 切换） */
export const cursorHovering = ref(false)
/** 是否在输入框内（露出原生 I-beam） */
export const cursorInInput = ref(false)

/** 主光标目标位置（mousemove 即时更新） */
export const cursorX = ref(0)
export const cursorY = ref(0)
/** 光环/emoji 当前缓动位置（宿主 rAF lerp 追赶） */
export const haloX = ref(0)
export const haloY = ref(0)

/** 当前风格（由偏好驱动，设置抽屉可切换） */
export const cursorStyle = ref<CursorStyle>('dot-halo')
/** 点击粒子特效（由偏好驱动，设置抽屉可切换） */
export const cursorClickEffect = ref<ClickEffectType>('none')
/** 鼠标轨迹特效（由偏好驱动，设置抽屉可切换） */
export const cursorTrailEffect = ref<TrailEffectType>('none')
/** 用户偏好"减少动态效果"：禁用弹性动画 */
export const reducedMotion = ref(false)

let inited = false
let disposed = false

/** 可交互元素选择器（悬停时放大光环 / 切换 emoji；输入框单独处理） */
const INTERACTIVE = 'a, button, select, [role="button"], [tabindex], label, summary, [role="radio"], [role="checkbox"]'

function isInteractive(el: EventTarget | null): boolean {
  if (!el || !(el instanceof Element)) return false
  const target = el.closest(INTERACTIVE)
  return !!target
}

function onMove(e: MouseEvent) {
  cursorX.value = e.clientX
  cursorY.value = e.clientY
  if (!cursorVisible.value) {
    // 首次进入直接落位，避免从 (0,0) 飞入
    haloX.value = e.clientX
    haloY.value = e.clientY
  }
  cursorVisible.value = true
  // 输入框内隐藏自定义光标，露出原生 I-beam
  const t = e.target as HTMLElement | null
  cursorInInput.value = !!(t && t.closest && t.closest('input, textarea, [contenteditable]'))
}

function onOver(e: MouseEvent) {
  cursorHovering.value = isInteractive(e.target)
}

function onDown() { cursorPressed.value = true }
function onUp() { cursorPressed.value = false }
/** 鼠标离开窗口（relatedTarget 为空）→ 隐藏光标 */
function onOut(e: MouseEvent) {
  if (!e.relatedTarget) {
    cursorVisible.value = false
    cursorHovering.value = false
  }
}

/** 由 CursorHost 挂载时调用一次 */
function init() {
  if (inited || disposed) return
  inited = true
  const coarsePointer = window.matchMedia('(pointer: coarse)').matches
  // 保留 Windows 自定义光标（美观）；拖拽时通过 .dragging 自动恢复系统光标
  const defaultEnabled = !coarsePointer
  cursorEnabled.value = prefs.get<boolean>('cursor.enabled', defaultEnabled)
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  cursorStyle.value = prefs.get<CursorStyle>('cursor.style', 'dot-halo')
  cursorClickEffect.value = prefs.get<ClickEffectType>('cursor.clickEffect', 'none')
  cursorTrailEffect.value = prefs.get<TrailEffectType>('cursor.trailEffect', 'none')

  window.addEventListener('mousemove', onMove, { passive: true })
  window.addEventListener('mouseover', onOver, { passive: true })
  window.addEventListener('mousedown', onDown)
  window.addEventListener('mouseup', onUp)
  document.addEventListener('mouseout', onOut)
}

/** 由 CursorHost 卸载时调用 */
function destroy() {
  if (disposed) return
  disposed = true
  inited = false
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseover', onOver)
  window.removeEventListener('mousedown', onDown)
  window.removeEventListener('mouseup', onUp)
  document.removeEventListener('mouseout', onOut)
}

/** 供设置抽屉调用：切换是否启用自定义光标 */
function setEnabled(value: boolean) {
  cursorEnabled.value = value
  prefs.set('cursor.enabled', value)
}

/** 供设置抽屉调用：切换风格并持久化（后端 + localStorage） */
function setStyle(s: CursorStyle) {
  cursorStyle.value = s
  prefs.set('cursor.style', s)
}

/** 供设置抽屉调用：切换点击特效并持久化 */
function setClickEffect(e: ClickEffectType) {
  cursorClickEffect.value = e
  prefs.set('cursor.clickEffect', e)
}

/** 供设置抽屉调用：切换轨迹特效并持久化 */
function setTrailEffect(t: TrailEffectType) {
  cursorTrailEffect.value = t
  prefs.set('cursor.trailEffect', t)
}

export function useCustomCursor() {
  return {
    enabled: cursorEnabled,
    visible: cursorVisible,
    pressed: cursorPressed,
    hovering: cursorHovering,
    inInput: cursorInInput,
    x: cursorX,
    y: cursorY,
    haloX,
    haloY,
    style: cursorStyle,
    clickEffect: cursorClickEffect,
    trailEffect: cursorTrailEffect,
    reducedMotion,
    init,
    destroy,
    setEnabled,
    setStyle,
    setClickEffect,
    setTrailEffect,
  }
}
