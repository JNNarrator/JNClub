/**
 * useBreakpoint.ts — 统一响应式断点（移动端 / 窄屏 / 触屏能力）
 *
 * 收敛此前散落在各组件里的 `window.innerWidth < 768` + resize 手工监听
 * （MainLayout、Home 等各自实现，无防抖、易不一致）。
 * 基于 matchMedia，视口变化自动更新，组件卸载时自动清理。
 *
 * 断点约定（与 CSS 媒体查询一致）：
 * - isMobile：`(max-width: 767px)` —— 与 MainLayout / MobileTabBar 的移动端壳同档
 * - isNarrow：`(max-width: 479px)` —— 小屏手机
 * - isCoarse：`(pointer: coarse)` —— 触屏/触控笔，用于手势与 hover 兜底判断
 *
 * 用法：
 *   const { isMobile, isNarrow, isCoarse } = useBreakpoint()
 */
import { useMediaQuery } from '@vueuse/core'

/** 移动端断点：视口 < 768px */
export const MOBILE_QUERY = '(max-width: 767px)'
/** 窄屏断点：视口 < 480px */
export const NARROW_QUERY = '(max-width: 479px)'
/** 触屏能力：粗指针（触摸屏 / 触控笔） */
export const COARSE_QUERY = '(pointer: coarse)'

export function useBreakpoint() {
  const isMobile = useMediaQuery(MOBILE_QUERY)
  const isNarrow = useMediaQuery(NARROW_QUERY)
  const isCoarse = useMediaQuery(COARSE_QUERY)

  return { isMobile, isNarrow, isCoarse }
}
