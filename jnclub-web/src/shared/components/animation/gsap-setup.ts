import { gsap } from 'gsap'

/**
 * 全局 GSAP ticker 配置（两项目一致）：
 * - lagSmoothing(500, 33)：低帧率/卡顿时平滑补帧，最长 500ms 阈值、33ms 步进，
 *   避免在 Windows 低帧率下动画跳变，同时不引入主线程负担。
 * 只被 animation/index.ts 引用一次，保证在任何 J* 组件使用前生效。
 */
gsap.ticker.lagSmoothing(500, 33)
