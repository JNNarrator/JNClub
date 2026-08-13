/**
 * 动画统一 Token —— vue-bits 适配层单一来源
 *
 * 设计原则：
 * - 颜色不在此重复定义，直接引用全局 CSS 变量（--text-1/--text-2/--brand-* 等），
 *   由 App.vue 按亮/暗主题注入 :root，两项目通用。
 *   ⚠️ 勿用 --glass-text-*（那是 SSO 认证页专属 token，JNClub 未定义，会致渐变失效）。
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

/** 品牌/文字色 —— 用 CSS 变量字符串，随主题自动切换（两项目通用 token） */
export const themeColors = {
  /** 主文字色（标题/正文） */
  textMain: 'var(--text-1)',
  /** 次要文字色 */
  textSub: 'var(--text-2)',
  /** 品牌色（光泽/渐变） */
  brand: 'var(--brand)',
  brandStrong: 'var(--brand-hover)',
  /** 高光色（ShinyText 反光） */
  shine: 'var(--text-2)',
}
