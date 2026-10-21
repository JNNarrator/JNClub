/* ─── Cron 表达式工具：单一数据源双向绑定 ───
 * 核心数据流 model -> buildExpression -> 文本框 <- parseExpression <- 文本框
 * 防死循环 guard：watch model 改文本时标记 isProgrammatic，watch 文本时先检查
 * buildExpression(model) === newText 再决定是否 parse，避免互相触发。
 */
import { Cron } from 'croner'
import { toString as cronstrue } from 'cronstrue'


/* ─── 方言 ─── */
export type Dialect = 'standard-5' | 'with-seconds-6' | 'quartz-7'

/* ─── 字段名 —— 按方言展开的字段列表 ─── */
export type FieldName = 'second' | 'minute' | 'hour' | 'dayOfMonth' | 'month' | 'dayOfWeek' | 'year'

/* ─── 配置模式 ─── */
export type FieldMode = 'every' | 'range' | 'step' | 'list'

/* ─── 单个时间字段状态 ─── */
export interface CronField {
  mode: FieldMode
  from: number
  to: number
  step: number
  set: number[]
  /* 若反解析遇到无法精确映射到四种 mode 的高级语法（L W # ?），保留原文 */
  raw?: string
}

/* ─── 完整 model（单一数据源） ─── */
export interface CronModel {
  dialect: Dialect
  fields: Partial<Record<FieldName, CronField>>
}

/* ─── 各段范围 ─── */
export const FIELD_RANGES: Record<FieldName, { min: number; max: number }> = {
  second:     { min: 0, max: 59 },
  minute:     { min: 0, max: 59 },
  hour:       { min: 0, max: 23 },
  dayOfMonth: { min: 1, max: 31 },
  month:      { min: 1, max: 12 },
  dayOfWeek:  { min: 0, max: 7 },   // 0 与 7 均为周日；Quartz 则 1-7 或 SUN-SAT
  year:       { min: 1970, max: 2099 },
}

/* ─── 方言 -> 字段名列表（有序） ─── */
export const DIALECT_FIELDS: Record<Dialect, FieldName[]> = {
  'standard-5':     ['minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek'],
  'with-seconds-6': ['second', 'minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek'],
  'quartz-7':       ['second', 'minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek', 'year'],
}

/* ─── 符号速查 ─── */
export interface SymbolInfo { symbol: string; name: string; desc: string; dialects: Dialect[] }
export const CRON_SYMBOLS: SymbolInfo[] = [
  { symbol: '*', name: '通配', desc: '该字段的每个可能值', dialects: ['standard-5', 'with-seconds-6', 'quartz-7'] },
  { symbol: ',', name: '列表', desc: '列出指定的多个值', dialects: ['standard-5', 'with-seconds-6', 'quartz-7'] },
  { symbol: '-', name: '范围', desc: '指定值的范围（a-b）', dialects: ['standard-5', 'with-seconds-6', 'quartz-7'] },
  { symbol: '/', name: '步长', desc: '从起始值开始每隔 n 执行（a/n 或 a-b/n）', dialects: ['standard-5', 'with-seconds-6', 'quartz-7'] },
  { symbol: '?', name: '任意/互斥', desc: 'Quartz 中用于日/周日互斥，一方为具体值时另一方必须为 ?', dialects: ['quartz-7'] },
  { symbol: 'L', name: '最后', desc: '日字段=该月最后一天；周字段=该月最后一个星期X', dialects: ['quartz-7'] },
  { symbol: 'W', name: '最近工作日', desc: '离指定日期最近的周一到周五', dialects: ['quartz-7'] },
  { symbol: '#', name: '第N个', desc: '该月第 N 个星期X（如 3#2 = 该月第二个周三）', dialects: ['quartz-7'] },
]

/* ─── 零填充策略 ─── */
export type ZeroPadStrategy = 'none' | 'auto'

/* ─── model -> expression（含 ? 互斥处理） ─── */
export function buildExpression(model: CronModel, zeroPad: ZeroPadStrategy = 'none'): string {
  const fields = DIALECT_FIELDS[model.dialect]
  const parts = fields.map(fn => buildFieldPart(fn, model, zeroPad))
  return parts.join(' ')
}

function buildFieldPart(fn: FieldName, model: CronModel, zeroPad: ZeroPadStrategy): string {
  const f = model.fields[fn]
  if (!f) return '*'

  /* 如果有 raw（高级语法原文），优先保留 */
  if (f.raw && f.mode === 'list' && f.set.length === 0) return f.raw

  switch (f.mode) {
    case 'every': return '*'
    case 'range': return padVal(f.from, zeroPad) + '-' + padVal(f.to, zeroPad)
    case 'step': {
      const base = f.from !== undefined && f.from > 0 ? padVal(f.from, zeroPad) : '*'
      return base + '/' + f.step
    }
    case 'list': {
      if (f.raw) return f.raw
      const sorted = [...f.set].sort((a, b) => a - b)
      return sorted.map(v => padVal(v, zeroPad)).join(',')
    }
  }
  return '*'
}

function padVal(v: number, strategy: ZeroPadStrategy): string {
  /* 秒/分总是两位数；日/月/时可选；年不补零 */
  if (strategy === 'none' && v < 10) return String(v)
  return v < 10 ? '0' + v : String(v)
}

/* ─── expression -> model（反解析；遇到高级语法标记 raw 保留） ─── */
export function parseExpression(expr: string, dialect: Dialect, tryDialect?: Dialect): {
  model: CronModel | null
  error: string | null
} {
  const trimmed = expr.trim()
  if (!trimmed) return { model: null, error: '表达式为空' }

  const parts = trimmed.split(/\s+/)
  const expectedFields = DIALECT_FIELDS[dialect]
  const actualLength = parts.length

  // 段数校验
  if (actualLength !== expectedFields.length) {
    if (tryDialect && tryDialect !== dialect) {
      // 允许尝试其他方言
      return tryParseAlternative(trimmed, dialect, tryDialect)
    }
    return {
      model: null,
      error: `段数不匹配：需要 ${expectedFields.length} 段（${dialect}），实际 ${actualLength} 段`,
    }
  }

  const fields: Partial<Record<FieldName, CronField>> = {}
  for (let i = 0; i < parts.length; i++) {
    const fn = expectedFields[i]
    const parsed = parseFieldPart(parts[i], fn, dialect)
    if (!parsed) {
      return { model: null, error: `第 ${i + 1} 段「${parts[i]}」解析失败（${fn}）` }
    }
    fields[fn] = parsed
  }

  // Quartz 日/周 ? 校验：两者不能同时为具体值
  if (dialect === 'quartz-7') {
    const dayField = fields.dayOfMonth
    const weekField = fields.dayOfWeek
    const daySpecific = dayField && dayField.mode !== 'every' && dayField.raw !== '?'
    const weekSpecific = weekField && weekField.mode !== 'every' && weekField.raw !== '?'
    if (daySpecific && weekSpecific) {
      return { model: null, error: `Quartz 中日与周日不能同时为具体值；一方应置 '?'` }
    }
    // 如果日或周其中之一为 ?，确保 raw 标记
    if (parts[3] === '?' && dayField && dayField.mode === 'every') dayField.raw = '?'
    if (parts[4] === '?' && weekField && weekField.mode === 'every') weekField.raw = '?'
  }

  return { model: { dialect, fields }, error: null }
}

function tryParseAlternative(trimmed: string, currentDialect: Dialect, tryDialect: Dialect) {
  // 简单补段尝试
  const parts = trimmed.split(/\s+/)
  const tryFields = DIALECT_FIELDS[tryDialect]
  if (parts.length === tryFields.length) {
    return parseExpression(trimmed, tryDialect)
  }
  return { model: null, error: `段数不匹配：需要 ${DIALECT_FIELDS[currentDialect].length} 段（${currentDialect}），实际 ${parts.length} 段` }
}

function parseFieldPart(part: string, fn: FieldName, dialect: Dialect): CronField | null {
  if (!part) return null
  const range = FIELD_RANGES[fn]

  // ? —— 仅 Quartz 中允许
  if (part === '?') {
    if (dialect !== 'quartz-7' || (fn !== 'dayOfMonth' && fn !== 'dayOfWeek')) return null
    return { mode: 'every', from: 0, to: 0, step: 1, set: [], raw: '?' }
  }

  // L / LW / 或周 L-# / 或 W —— 高级语法
  if (/[LW#]/.test(part)) {
    return { mode: 'list', from: 0, to: 0, step: 1, set: [], raw: part }
  }

  // * (every)
  if (part === '*') return { mode: 'every', from: range.min, to: range.max, step: 1, set: [] }

  // a-b/n (range + step)
  const rangeStepMatch = part.match(/^(\d+)-(\d+)\/(\d+)$/)
  if (rangeStepMatch) {
    const from = parseInt(rangeStepMatch[1], 10)
    const to = parseInt(rangeStepMatch[2], 10)
    const step = parseInt(rangeStepMatch[3], 10)
    if (isNaN(from) || isNaN(to) || isNaN(step) || step < 1) return null
    return { mode: 'step', from, to, step, set: [] }
  }

  // a/n (step from a)
  const stepMatch = part.match(/^(\d+)\/(\d+)$/)
  if (stepMatch) {
    const from = parseInt(stepMatch[1], 10)
    const step = parseInt(stepMatch[2], 10)
    if (isNaN(from) || isNaN(step) || step < 1) return null
    return { mode: 'step', from, to: range.max, step, set: [] }
  }

  // */n (step from min)
  const starStepMatch = part.match(/^\*\/(\d+)$/)
  if (starStepMatch) {
    const step = parseInt(starStepMatch[1], 10)
    if (isNaN(step) || step < 1) return null
    return { mode: 'step', from: range.min, to: range.max, step, set: [] }
  }

  // a-b (range)
  const rangeMatch = part.match(/^(\d+)-(\d+)$/)
  if (rangeMatch) {
    const from = parseInt(rangeMatch[1], 10)
    const to = parseInt(rangeMatch[2], 10)
    if (isNaN(from) || isNaN(to)) return null
    return { mode: 'range', from, to, step: 1, set: [] }
  }

  // comma-separated list (包括单个数字)
  const listMatch = part.match(/^(\d+)(,\d+)*$/)
  if (listMatch) {
    const values = part.split(',').map(v => parseInt(v, 10))
    if (values.some(v => isNaN(v))) return null
    return { mode: 'list', from: 0, to: 0, step: 1, set: values }
  }

  // 未能解析
  return null
}

/* ─── ? 互斥：Quartz 下日/周不能同时为具体值 ───
 * 规则：当一方从 every/? 变为具体值/列表时，另一方自动置为 '?'
 * 非 Quartz 方言无 '?' 行为
 */
export function applyMutualExclusion(model: CronModel, changedField: FieldName): CronModel {
  if (model.dialect !== 'quartz-7') return model
  if (changedField !== 'dayOfMonth' && changedField !== 'dayOfWeek') return model

  const otherField: FieldName = changedField === 'dayOfMonth' ? 'dayOfWeek' : 'dayOfMonth'
  const changed = model.fields[changedField]
  if (!changed) return model

  const isSpecific = changed.mode !== 'every' && !changed.raw?.startsWith('?')
  if (isSpecific) {
    // 把另一方的 mode 设为 every 并置 raw = '?'
    const clone = cloneModel(model)
    clone.fields[otherField] = { mode: 'every', from: 0, to: 0, step: 1, set: [], raw: '?' }
    return clone
  }

  return model
}

function cloneModel(model: CronModel): CronModel {
  const fields: Partial<Record<FieldName, CronField>> = {}
  for (const key of Object.keys(model.fields) as FieldName[]) {
    const f = model.fields[key]
    if (f) fields[key] = { ...f, set: [...f.set] }
  }
  return { dialect: model.dialect, fields }
}

/* ─── 创建默认 model ─── */
export function createDefaultModel(dialect: Dialect): CronModel {
  const fieldNames = DIALECT_FIELDS[dialect]
  const fields: Partial<Record<FieldName, CronField>> = {}
  for (const fn of fieldNames) {
    const range = FIELD_RANGES[fn]
    fields[fn] = { mode: 'every', from: range.min, to: range.max, step: 1, set: [] }
  }
  // Quartz 默认日/周互斥：日=*，周=?
  if (dialect === 'quartz-7') {
    if (fields.dayOfWeek) fields.dayOfWeek.raw = '?'
  }
  return { dialect, fields }
}

/* ─── 人话翻译（使用 cronstrue） ─── */
export function describeCron(expr: string, locale: 'zh_CN' | 'en' = 'zh_CN'): string {
  try {
    const opts = { locale, use24HourTimeFormat: true, dayOfWeekStartIndexZero: true }
    return cronstrue(expr, opts)
  } catch {
    return '表达式无效'
  }
}

/* ─── 未来 N 次运行时间 ─── */
export interface RunTime {
  date: Date
  utcOffset: number
  relativeText: string
}

export function nextRuns(
  expr: string,
  count: number,
  startDate: Date = new Date(),
  timezone?: string,
): RunTime[] {
  try {
    const options: any = { startAt: startDate }
    if (timezone) options.timezone = timezone
    const job = new Cron(expr, options)
    if (!job) return []
    const results: RunTime[] = []
    for (let i = 0; i < count; i++) {
      const next = job.nextRun()
      if (!next) break
      results.push({
        date: new Date(next),
        utcOffset: next.getTimezoneOffset(),
        relativeText: getRelativeTime(next, startDate),
      })
    }
    return results
  } catch {
    return []
  }
}

function getRelativeTime(date: Date, now: Date): string {
  const diffMs = date.getTime() - now.getTime()
  if (diffMs <= 0) return '已过时'
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return `${diffSec} 秒后`
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} 分 ${diffSec % 60} 秒后`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时 ${diffMin % 60} 分后`
  const diffDay = Math.floor(diffHour / 24)
  return `${diffDay} 天后`
}

/* ─── 检测相邻两次运行是否跨 DST ─── */
export function hasDstTransition(runs: RunTime[]): boolean {
  if (runs.length < 2) return false
  for (let i = 1; i < runs.length; i++) {
    if (runs[i].utcOffset !== runs[i - 1].utcOffset) return true
  }
  return false
}

/* ─── 格式化时间为本地/UTC ─── */
export function formatDateTime(date: Date, tz?: string): { local: string; utc: string } {
  const localOpts: Intl.DateTimeFormatOptions = {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    ...(tz ? { timeZone: tz } : {}),
  }
  const local = new Intl.DateTimeFormat(undefined, localOpts).format(date)
  const utc = new Intl.DateTimeFormat(undefined, {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'UTC',
  }).format(date)
  return { local, utc }
}

/* ─── 获取当前时区名 ─── */
export function getCurrentTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}

/* ─── 示例表达式（按方言） ─── */
export const EXAMPLES: Record<Dialect, string[]> = {
  'standard-5': [
    '*/5 * * * *',
    '0 9 * * *',
    '30 8 * * 1-5',
    '0 0 1 1 *',
  ],
  'with-seconds-6': [
    '0 */5 * * * *',
    '0 0 9 * * *',
    '0 30 8 * * 1-5',
    '0 0 0 1 1 *',
  ],
  'quartz-7': [
    '0 */5 * * * ? *',
    '0 0 9 * * ? *',
    '0 30 8 ? * MON-FRI *',
    '0 0 0 1 1 ? 2026',
  ],
}
