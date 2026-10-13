import { readonly, ref, type DeepReadonly, type Ref } from 'vue'

/**
 * useReducedMotion — 响应式的 prefers-reduced-motion 查询
 *
 * 为什么需要它：CSS 层的 `@media (prefers-reduced-motion: reduce)` 只能关掉
 * CSS 动画/过渡，管不到 JS 驱动的动画（rAF 计数、鼠标跟随、GSAP、motion-v）。
 * 那些组件必须自己读这个开关，并在开启时直接落到终态。
 *
 * 监听器全局只注册一份（媒体查询变化极少），因此组件外也可安全调用，
 * 无需依赖 setup 生命周期来注销。
 */
const mediaQuery =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)')
    : null

const prefersReduced = ref(mediaQuery?.matches ?? false)

if (mediaQuery) {
  const onChange = (event: MediaQueryListEvent) => {
    prefersReduced.value = event.matches
  }
  // Safari < 14 只有已废弃的 addListener
  if (typeof mediaQuery.addEventListener === 'function') {
    mediaQuery.addEventListener('change', onChange)
  } else if (typeof (mediaQuery as MediaQueryList & { addListener?: unknown }).addListener === 'function') {
    ;(mediaQuery as MediaQueryList & { addListener: (cb: (e: MediaQueryListEvent) => void) => void }).addListener(onChange)
  }
}

/** 是否处于「减少动态效果」偏好下；只读，直接用于模板或计算属性 */
export function useReducedMotion(): DeepReadonly<Ref<boolean>> {
  return readonly(prefersReduced) as DeepReadonly<Ref<boolean>>
}
