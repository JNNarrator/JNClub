/**
 * formatDate.ts — 公共日期格式化函数
 * 对后端可能返回的零值/无效值做前端兜底
 * 支持多种后端序列化格式：ISO 8601、yyyy-MM-dd HH:mm:ss、时间戳数组、毫秒数
 */

/** 日期校验：无效/零值/空字符串 -> 返回占位符 */
const isValidTs = (ts: unknown): boolean => {
  if (!ts) return false
  if (typeof ts === 'string' && ts.trim() === '') return false
  if (Array.isArray(ts)) {
    // Jackson 数组格式 [2025, 7, 31, 14, 30] — 年在前即有效
    return ts.length >= 3 && typeof ts[0] === 'number'
  }
  if (typeof ts === 'number') return Number.isFinite(ts) && ts > 0
  if (typeof ts === 'string') {
    const t = Number(ts)
    if (Number.isFinite(t) && t > 0) return true
    // 也可能是日期字符串格式 yyyy-MM-dd HH:mm:ss 或 ISO 8601
    return !isNaN(Date.parse(ts))
  }
  return false
}

/** 从各种格式解析为 Date */
function parseDate(ts: unknown): Date | null {
  if (Array.isArray(ts)) {
    // Jackson LocalDateTime 数组: [2025, 7, 31, 14, 30]
    const [y, m = 1, d = 1, h = 0, min = 0, s = 0] = ts as number[]
    return new Date(y, m - 1, d, h, min, s)
  }
  if (typeof ts === 'number') {
    return new Date(ts)
  }
  if (typeof ts === 'string') {
    const ms = Number(ts)
    if (Number.isFinite(ms) && ms > 0) return new Date(ms)
    // yyyy-MM-dd HH:mm:ss、ISO 8601 等
    const d = new Date(ts)
    if (!isNaN(d.getTime())) return d
  }
  return null
}

/** 格式化日期 — 年/月/日，用于卡片底部 */
export function formatDate(dateStr: string): string {
  if (!isValidTs(dateStr)) return '—'
  const date = parseDate(dateStr)
  if (!date) return '—'
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/** 相对时间 — "刚刚"/"X 分钟前"/"X 天前"/月/日，用于列表行 */
export function formatRelativeTime(dateStr: string): string {
  if (!isValidTs(dateStr)) return '—'
  const date = parseDate(dateStr)
  if (!date) return '—'
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 0) return formatDate(dateStr)
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`
  if (diff < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / (24 * 60 * 60 * 1000))} 天前`
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
