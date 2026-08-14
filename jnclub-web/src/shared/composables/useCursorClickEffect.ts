/**
 * useCursorClickEffect — 点击粒子特效状态管理
 * 监听 cursor.pressed 变化（mousedown），在点击位置 spawn 粒子。
 * 不再管理自己的 rAF 循环，由 CursorHost 的统一动画循环驱动 updateClickParticles(dt)。
 */
import { ref, watch } from 'vue'
import { useCustomCursor } from './useCustomCursor'
import { spawnParticles, updateParticles, recycleParticles, type Particle } from '../utils/cursorParticles'

const particles = ref<Particle[]>([])
/** 模块级 watch 只注册一次（首次调用），避免重复挂载时叠加监听 */
let registered = false

/**
 * 由 CursorHost 的统一动画循环调用，更新所有粒子的位置与生命期
 * @param dt 帧间隔（秒）
 */
function updateClickParticles(dt: number) {
  if (particles.value.length === 0) return
  const clampedDt = Math.min(dt, 0.05)
  updateParticles(particles.value, clampedDt)
  // 移除已死亡粒子并回收
  const dead = particles.value.filter(p => p.life >= 1)
  if (dead.length > 0) {
    recycleParticles(dead)
    particles.value = particles.value.filter(p => p.life < 1)
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
      }
    })
  }

  return {
    particles,
    updateClickParticles,
  }
}
