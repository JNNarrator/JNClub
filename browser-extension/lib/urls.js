/**
 * lib/urls.js — URL 工具（popup / batch 等多个页面共享）
 */

/** 浏览器内置协议页不可注入/不可用 */
export const BROWSER_SCHEMES = /^(chrome|chrome-extension|edge|about|devtools|view-source|moz-extension|opera):/

/** 规范化 URL：解析为绝对 href 并去尾斜杠（用于去重比对） */
export function normalizeUrl(u) {
  try {
    return new URL(u).href.replace(/\/$/, '')
  } catch {
    return u
  }
}