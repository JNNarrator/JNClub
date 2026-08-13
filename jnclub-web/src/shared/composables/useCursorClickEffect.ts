/**
 * useCursorClickEffect — 点击粒子特效状态管理
 * 监听 cursor.pressed 变化（mousedown），在点击位置 spawn 粒子，
 * rAF 循环更新粒子生命期与位置，空闲时自动停止。
 */
import { ref, watch } from 'vue'
import { useCustomCursor } from './useCustomCursor'
import { spawnParticles, updateParticles, recycleParticles, type Particle } from '../utils/cursorParticles'

const particles = ref<Particle[]>([])
let rafId = 0
let lastT = 0
/** 模块级 watch 只注册一次（首次调用），避免重复挂载时叠加监听 */
let registered = false

function onFrame(t: number) {
  const dt = lastT ? Math.min((t - lastT) / 1000, 0.05) : 1 / 60
  lastT = t
  updateParticles(particles.value, dt)
  // 移除已死亡粒子并回收
  const dead = particles.value.filter(p => p.life >= 1)
  if (dead.length > 0) {
    recycleParticles(dead)
    particles.value = particles.value.filter(p => p.life < 1)
  }
  if (particles.value.length > 0) {
    rafId = requestAnimationFrame(onFrame)
  } else {
    rafId = 0
  }
}

export function useCursorClickEffect() {
  const cursor = useCustomCursor()

  // 监听按下事件（pressed: false → true）触发粒子生成
  if (!registered) {
    registered = true
    watch(() => cursor.pressed.value, (pressed) => {
      if (
        pressed
        && cursor.clickEffect.value !== 'none'
        && !cursor.inInput.value
        && !cursor.reducedMotion.value
      ) {
        const spawned = spawnParticles(
          cursor.x.value,
          cursor.y.value,
          cursor.clickEffect.value,
        )
        particles.value.push(...spawned)
        if (!rafId) {
          lastT = 0
          rafId = requestAnimationFrame(onFrame)
        }
      }
    })
  }

  return {
    particles,
  }
}
