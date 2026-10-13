/**
 * gestureLock.ts — 全局手势互斥锁
 *
 * 触屏上一次按压只应产生一种结果：要么长按唤起上下文菜单，要么拖拽排序。
 * 两者的判定窗口天然重叠——长按是「500ms 内位移 < 10px」，而 SortableJS
 * 只要位移达到 touchStartThreshold（本项目 3px）就启动拖拽。于是存在这样
 * 的序列：手指按住不动 500ms → 菜单弹出 → 手指继续移动 → SortableJS
 * 此时才收到 pointermove 并进入排序态，菜单与拖拽同时存在（M4-4）。
 *
 * 由于两者共用同一个 pointerdown，无法在起始时预判，只能让「先判定成功」
 * 的一方通知另一方让位。这里提供一处模块级共享状态：
 * - useLongPress 在菜单真正弹出时 lockGesture()，手势结束后 unlockGesture()；
 * - useDraggableSort 订阅状态，锁定期间销毁 Sortable 实例，解锁后重建。
 *
 * 状态放在模块作用域而非组件内，是因为长按发生在列表项上、Sortable 挂在
 * 列表容器上，二者不是同一个组件实例。
 */

let locked = false
const listeners = new Set<(locked: boolean) => void>()

/** 当前是否有长按菜单占用手势（供非订阅场景查询） */
export function isGestureLocked(): boolean {
  return locked
}

/** 长按菜单弹出：通知所有拖拽容器让位 */
export function lockGesture(): void {
  if (locked) return
  locked = true
  listeners.forEach((fn) => fn(true))
}

/** 手势结束（抬手/取消/新手势落下）：恢复拖拽容器 */
export function unlockGesture(): void {
  if (!locked) return
  locked = false
  listeners.forEach((fn) => fn(false))
}

/**
 * 订阅手势锁变化
 * @returns 取消订阅函数
 */
export function onGestureLockChange(fn: (locked: boolean) => void): () => void {
  listeners.add(fn)
  return () => {
    listeners.delete(fn)
  }
}
