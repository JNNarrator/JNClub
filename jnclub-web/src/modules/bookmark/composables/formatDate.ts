/**
 * formatDate.ts — 公共日期格式化函数
 * 对后端可能返回的零值/无效值做前端兜底
 */

/** 日期校验：无效/零值/空字符串 -> 返回占位符 */
const isValidTs = (ts: unknown): ts is string => {
  if (!ts || (typeof ts === 'string' && ts.trim() === '')) return false
  const t = Number(ts)
  if (!Number.isFinite(t) || t === 0) return false
  return true
}

/** 格式化日期 — 年/月/日，用于卡片底部 */
export function formatDate(dateStr: string): string {
  if (!isValidTs(dateStr)) return '—'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/** 相对时间 — "刚刚"/"X 分钟前"/"X 天前"/月/日，用于列表行 */
export function formatRelativeTime(dateStr: string): string {
  if (!isValidTs(dateStr)) return '—'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return '—'
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 0) return formatDate(dateStr)
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`
  if (diff < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / (24 * 60 * 60 * 1000))} 天前`
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
