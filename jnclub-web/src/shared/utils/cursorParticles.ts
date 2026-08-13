/**
 * cursorParticles.ts — 点击粒子物理引擎
 * 纯函数，无状态依赖。粒子对象池避免 GC 抖动。
 * 三种形状（star/heart/flower）由 CSS class 控制，引擎只管位置/生命期/旋转。
 */

export type ParticleType = 'star' | 'heart' | 'flower'

export interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  life: number        // 0→1 生命周期进度（1 = 死亡）
  decay: number       // 衰减速率（越大越快消失）
  size: number
  rotation: number
  rotationSpeed: number
  type: ParticleType
  color: string
}

/** 对象池：避免频繁 new/GC */
const pool: Particle[] = []
const MAX_POOL = 60

/** 三种粒子的配色方案 */
const PALETTE: Record<ParticleType, string[]> = {
  star: ['#FFD700', '#FFA500', '#FF6347', '#FF69B4'],
  heart: ['#FF6B8A', '#FF8FAB', '#EC5B8E', '#FFB3C6'],
  flower: ['#FFB7D5', '#FF8FAB', '#EC5B8E', '#FFC3A0', '#FFDAB9'],
}

/** 创建初始粒子对象 */
function createParticle(): Particle {
  return {
    x: 0, y: 0, vx: 0, vy: 0,
    life: 0, decay: 0, size: 0,
    rotation: 0, rotationSpeed: 0,
    type: 'star', color: '#fff',
  }
}

/** 重置粒子状态（从池取出后调用） */
function resetParticle(
  p: Particle,
  cx: number, cy: number,
  type: ParticleType,
  index: number, total: number,
) {
  const angle = (index / total) * Math.PI * 2 + (Math.random() - 0.5) * 0.5
  const speed = 120 + Math.random() * 80 // 120-200 px/s

  p.x = cx
  p.y = cy
  p.vx = Math.cos(angle) * speed
  p.vy = Math.sin(angle) * speed - 40 // 略向上偏移
  p.life = 0
  p.decay = 1.2 + Math.random() * 0.6 // 0.8-1.2s 生命周期
  p.size = 10 + Math.random() * 8     // 10-18px
  p.rotation = Math.random() * 360
  p.rotationSpeed = (Math.random() - 0.5) * 400 // -200~200 deg/s
  p.type = type
  p.color = PALETTE[type][Math.floor(Math.random() * PALETTE[type].length)]
}

/**
 * 生成粒子：以 (cx, cy) 为圆心，均匀角度散开
 * @returns 新生成的粒子数组（引用已从池中取出）
 */
export function spawnParticles(
  cx: number, cy: number,
  type: ParticleType,
  count: number = 8,
): Particle[] {
  const spawned: Particle[] = []
  for (let i = 0; i < count; i++) {
    const p = pool.pop() ?? createParticle()
    resetParticle(p, cx, cy, type, i, count)
    spawned.push(p)
  }
  return spawned
}

/**
 * 更新粒子：位置积分 + 生命周期推进
 * @param dt 帧间隔（秒）
 * @param gravity 重力加速度（默认 0.3 px/s²）
 */
export function updateParticles(
  particles: Particle[],
  dt: number,
  gravity: number = 0.3,
) {
  const friction = 0.97
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i]
    p.life += p.decay * dt
    p.vx *= friction
    p.vy *= friction
    p.vy += gravity * 60 * dt // 重力
    p.x += p.vx * dt
    p.y += p.vy * dt
    p.rotation += p.rotationSpeed * dt
  }
}

/** 回收死亡粒子到对象池 */
export function recycleParticles(particles: Particle[]) {
  for (const p of particles) {
    if (pool.length < MAX_POOL) pool.push(p)
  }
}
