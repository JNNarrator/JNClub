/**
 * useCursorTrailEffect — 鼠标轨迹特效状态管理
 * 订阅 cursor.x/y 变化，追加轨迹点到环形缓冲区，
 * rAF 循环推进各点 age，空闲时自动停止。
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
let rafId = 0
let lastT = 0
let hueCounter = 0
/** 模块级 watch 只注册一次（首次调用），避免重复挂载时叠加监听 */
let registered = false

function onFrame(t: number) {
  const dt = lastT ? Math.min((t - lastT) / 1000, 0.05) : 1 / 60
  lastT = t
  // 推进所有点的 age
  for (let i = points.value.length - 1; i >= 0; i--) {
    points.value[i].age += AGE_SPEED * dt
  }
  // 移除 age >= 1 的点
  points.value = points.value.filter(p => p.age < 1)
  if (points.value.length === 0) {
    rafId = 0
    return
  }
  // 有活跃点时继续循环
  if (rafId) {
    rafId = requestAnimationFrame(onFrame)
  }
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
        if (!rafId) {
          lastT = 0
          rafId = requestAnimationFrame(onFrame)
        }
      },
      { flush: 'sync' },
    )
  }

  return {
    points,
  }
}
