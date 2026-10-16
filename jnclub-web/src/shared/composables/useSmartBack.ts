/**
 * useSmartBack.ts — 「返回」按钮的语义化实现
 *
 * 背景（报告 IA-5）：概览页与回收站的顶栏返回按钮原先一律 `router.push('/')`，
 * 而 '/' 解析为主壳默认模块（收藏夹）。于是「从便签进概览 → 点返回」会落到收藏夹，
 * 而不是来时的便签 —— 按钮图标（左箭头）、title、aria-label 三者一致承诺「返回上一页」，
 * 实际行为违背了该承诺。
 *
 * 本 composable 让「返回」真的返回：
 *   - 有可回退的历史记录时 → router.back()，回到真实来路；
 *   - 无可回退历史时（如直接刷新/新标签页打开 /overview）→ 回退到 fallback（默认收藏夹），
 *     避免出现「点了没反应」的死按钮。
 *
 * 判据用 window.history.state.back：Vue Router 4 在每次导航后把 { back, current, forward,
 * position } 写进 history.state；直接打开页面时 back 为 null。
 */
import { useRouter } from 'vue-router'

export function useSmartBack(fallback = '/') {
  const router = useRouter()

  const goBack = () => {
    // 直接打开该 URL（无来路）时 history.state.back 为 null，此时退回 fallback。
    if (window.history.state?.back != null) router.back()
    else router.push(fallback)
  }

  return { goBack }
}
