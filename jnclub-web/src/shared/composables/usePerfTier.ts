import { ref } from 'vue'

/**
 * usePerfTier — 轻量 FPS 看门狗（能力自适应，替代平台嗅探）
 * 两平台同一套代码、同一套视觉起点：只在实测长期低帧率时才自动降级，
 * 不再按操作系统区分（Windows 与 macOS 默认体验一致）。
 *
 *   tier 0 = full     全部效果
 *   tier 1 = reduced  关粒子等持续耗能效果
 *   tier 2 = minimal  再关连续流光动画（保留入场/悬停一次性动画）
 *
 * 采样以 1 秒为窗口；连续 2 个窗口平均 FPS < 45 → 升一档；连续 4 个窗口 ≥ 55 → 降一档（恢复）。
 * prefers-reduced-motion 直接置 minimal，且不启动采样。
 * 状态同步到 <html data-perf-tier="0|1|2">，供 CSS 做分级降级。
 */
export type PerfTier = 0 | 1 | 2

const tier = ref<PerfTier>(0)
let inited = false
let raf = 0
let frames = 0
let windowT0 = 0
let lowWindows = 0
let goodWindows = 0

const LOW_FPS = 45
const RECOVER_FPS = 55
const UPGRADE_AFTER = 2
const RECOVER_AFTER = 4

function bump(dir: 1 | -1) {
  const next = Math.max(0, Math.min(2, tier.value + dir)) as PerfTier
  if (next !== tier.value) {
    tier.value = next
    document.documentElement.setAttribute('data-perf-tier', String(next))
  }
}

function sample(ts: number) {
  if (!raf) return
  if (windowT0 === 0) windowT0 = ts
  frames++
  const elapsed = (ts - windowT0) / 1000
  if (elapsed < 1) {
    raf = requestAnimationFrame(sample)
    return
  }
  const fps = frames / elapsed

  if (fps < LOW_FPS) {
    lowWindows++
    goodWindows = 0
    if (lowWindows >= UPGRADE_AFTER) {
      bump(1)
      lowWindows = 0
    }
  } else if (fps >= RECOVER_FPS) {
    goodWindows++
    lowWindows = 0
    if (goodWindows >= RECOVER_AFTER) {
      bump(-1)
      goodWindows = 0
    }
  } else {
    lowWindows = 0
    goodWindows = 0
  }

  frames = 0
  windowT0 = ts
  raf = requestAnimationFrame(sample)
}

function setTier(t: PerfTier) {
  tier.value = t
  document.documentElement.setAttribute('data-perf-tier', String(t))
}

function init() {
  if (inited) return
  inited = true
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    setTier(2)
    return
  }
  setTier(0)
  raf = requestAnimationFrame(sample)
}

export function usePerfTier() {
  return { tier, init }
}
