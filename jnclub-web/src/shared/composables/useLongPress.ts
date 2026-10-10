/**
 * useLongPress.ts — 触屏长按手势（移动端唤起上下文菜单等）
 *
 * 桌面鼠标不接管（继续走原生 @contextmenu），仅对 touch/pen 生效。
 * 用法（模板内联绑定，一行即可）：
 *   const longPress = useLongPress()
 *   <div
 *     @contextmenu.prevent="openMenu($event, dropdownOptions, handleDropdown)"
 *     v-on="longPress((ev) => openMenuAt(ev.clientX, ev.clientY, dropdownOptions, handleDropdown))"
 *   />
 *
 * 行为：
 * - 按住 duration（默认 500ms）且位移不超过 moveThreshold（默认 10px）→ 触发 handler；
 * - 抬手（pointerup）时若长按已触发，才注册「吃掉紧随那次 click」的守卫——
 *   锚在抬手时刻而非触发时刻，因此按住多久都不会漏抑制；
 * - 守卫只在点击落在原长按元素内部时生效，不会误吞随后弹出的菜单项点击；
 * - 新手势落下（pointerdown）或守卫超时即释放，避免残留吞掉正常点击；
 * - 位移超阈值（判定为滚动/拖拽）或 pointerup / pointercancel → 取消。
 */
import { onBeforeUnmount } from 'vue'

export interface LongPressOptions {
  /** 触发所需按住时长（毫秒），默认 500 */
  duration?: number
  /** 手指位移超过该像素即取消，默认 10 */
  moveThreshold?: number
}

export function useLongPress(options: LongPressOptions = {}) {
  const { duration = 500, moveThreshold = 10 } = options

  /** 当前存活的 click 守卫释放函数（组件卸载时统一调用） */
  let releaseGuard: (() => void) | null = null

  /** 捕获阶段拦截紧随长按抬手的那次 click，避免误触元素自身行为（如打开书签） */
  const swallowNextClick = (target: Element | null) => {
    let guard: ReturnType<typeof setTimeout> | null = null

    function remove() {
      document.removeEventListener('click', onCapture, true)
      document.removeEventListener('pointerdown', onNextPointerDown, true)
      if (guard !== null) clearTimeout(guard)
      if (releaseGuard === remove) releaseGuard = null
    }

    function onCapture(ev: MouseEvent) {
      const t = ev.target as Element | null
      // 只拦截落在原长按元素内的点击；菜单项等浮层不在其内，不受影响
      if (target && t && !target.contains(t)) {
        remove()
        return
      }
      ev.preventDefault()
      ev.stopPropagation()
      remove()
    }

    /** 新手势落下 = 上一个长按已结束，立即释放守卫 */
    function onNextPointerDown() {
      remove()
    }

    document.addEventListener('click', onCapture, true)
    document.addEventListener('pointerdown', onNextPointerDown, true)
    // 兜底：若浏览器长按后既不派发 click 也没有新的按下，短超时后自行清理
    guard = setTimeout(remove, 400)
    releaseGuard = remove
  }

  onBeforeUnmount(() => {
    if (releaseGuard) releaseGuard()
  })

  /** 为一个元素生成事件处理器集合，模板中 v-on="longPress(handler)" 绑定 */
  return function bind(handler: (e: PointerEvent) => void) {
    // 状态放在 bind 内部：v-for 中每个元素各自独立，多指同时按下互不干扰
    let timer: ReturnType<typeof setTimeout> | null = null
    let startX = 0
    let startY = 0
    /** 本次按压是否已触发长按（决定抬手时是否注册 click 守卫） */
    let fired = false

    const clearTimer = () => {
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
    }

    return {
      pointerdown(e: PointerEvent) {
        // 鼠标不接管，交给原生右键菜单
        if (e.pointerType === 'mouse') return
        startX = e.clientX
        startY = e.clientY
        fired = false
        clearTimer()
        timer = setTimeout(() => {
          timer = null
          // 已进入拖拽排序（SortableJS 在 documentElement 上加 .dragging）→ 让位给排序
          if (document.documentElement.classList.contains('dragging')) return
          fired = true
          handler(e)
        }, duration)
      },
      pointermove(e: PointerEvent) {
        if (timer === null) return
        if (
          Math.abs(e.clientX - startX) > moveThreshold ||
          Math.abs(e.clientY - startY) > moveThreshold
        ) {
          clearTimer()
        }
      },
      pointerup(e: PointerEvent) {
        clearTimer()
        if (!fired) return
        fired = false
        // click 在 pointerup 之后派发，此处注册守卫可精确覆盖它
        swallowNextClick(e.currentTarget as Element | null)
      },
      pointercancel() {
        clearTimer()
        fired = false
      },
      pointerleave: clearTimer,
    }
  }
}
