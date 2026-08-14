/**
 * useCursorTrailEffect — 鼠标轨迹特效状态管理
 * 订阅 cursor.x/y 变化，追加轨迹点到环形缓冲区。
 * 不再管理自己的 rAF 循环，由 CursorHost 的统一动画循环驱动 updateTrail(dt)。
 */
import { ref, watch } from 'vue'
import { useCustomCursor } from './useCustomCursor'

export interface TrailPoint {
  x: number
  y: number
  age: number   // 0→1，越大越旧
  hue: number   // rainbow 模式色相
}

const MAX_POINTS = 25
const MIN_DISTANCE = 3 // 静止时不堆积点的距离阈值
const AGE_SPEED = 1.8  // age 递增速率（越大越快淡出）

const points = ref<TrailPoint[]>([])
let hueCounter = 0
/** 模块级 watch 只注册一次（首次调用），避免重复挂载时叠加监听 */
let registered = false

/**
 * 由 CursorHost 的统一动画循环调用，推进所有轨迹点的 age
 * @param dt 帧间隔（秒）
 */
function updateTrail(dt: number) {
  if (points.value.length === 0) return
  const clampedDt = Math.min(dt, 0.05)
  for (let i = points.value.length - 1; i >= 0; i--) {
    points.value[i].age += AGE_SPEED * clampedDt
  }
  points.value = points.value.filter(p => p.age < 1)
}

export function useCursorTrailEffect() {
  const cursor = useCustomCursor()

  let lastX = 0
  let lastY = 0

  // 订阅鼠标移动，追加轨迹点
  if (!registered) {
    registered = true
    watch(
      () => [cursor.x.value, cursor.y.value] as const,
      ([x, y]) => {
        if (
          cursor.trailEffect.value === 'none'
          || cursor.reducedMotion.value
          || cursor.inInput.value
        ) {
          return
        }
        const dx = x - lastX
        const dy = y - lastY
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MIN_DISTANCE) return
        lastX = x
        lastY = y
        hueCounter = (hueCounter + 4) % 360
        points.value.push({ x, y, age: 0, hue: hueCounter })
        if (points.value.length > MAX_POINTS) {
          points.value.shift()
        }
      },
      { flush: 'sync' },
    )
  }

  return {
    points,
    updateTrail,
  }
}
