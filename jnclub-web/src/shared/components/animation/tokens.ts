/**
 * 动画统一 Token —— vue-bits 适配层单一来源
 *
 * 设计原则：
 * - 颜色不在此重复定义，直接引用全局 CSS 变量（--brand-* / --glass-text-* 等），
 *   由 App.vue 按亮/暗主题注入 :root，与 JNClub 保持一致。
 * - 节奏（easing / 时长 / 错峰）在此统一，业务侧只 import J* 组件，不直接碰 vue-bits。
 */

/** GSAP / vue-bits 缓动与节奏基准 */
export const motion = {
  /** GSAP ease 字符串（SplitText / AnimatedContent 等使用） */
  gsapEase: 'power3.out',
  /** 入场基准时长（秒） */
  duration: 0.8,
  /** 逐字/逐词错峰延迟（毫秒） */
  stagger: 40,
}

/** 品牌/文字色 —— 用 CSS 变量字符串，随主题自动切换 */
export const themeColors = {
  /** 主文字色（标题/正文） */
  textMain: 'var(--glass-text-main)',
  /** 次要文字色 */
  textSub: 'var(--glass-text-sub)',
  /** 品牌色（光泽/渐变） */
  brand: 'var(--brand-500)',
  brandStrong: 'var(--brand-600)',
  /** 高光色（ShinyText 反光） */
  shine: 'var(--glass-text-main)',
}
