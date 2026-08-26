/**
 * todoNlp.ts — 待办自然语言快速添加（规则版，不引入 NLP 依赖）
 * 支持：今天/明天/后天/周X/星期X、X月X日、HH:mm、上午/下午/晚上/中午、
 *       提醒/提前 N 分钟、每天/每周/每月/每年、高优先级
 * 解析失败时调用方退化为普通标题。
 */

export interface ParsedTodo {
  title: string
  dueDate: string | null
  dueTime: string | null
  remindAt: string | null
  recurrence: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY' | null
  priority: number
}

const pad = (n: number) => String(n).padStart(2, '0')
const fmtDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

function addDays(base: Date, days: number): Date {
  const d = new Date(base.getFullYear(), base.getMonth(), base.getDate() + days)
  return d
}

function nextWeekday(base: Date, target: number): Date {
  // JS getDay(): 0=Sun, target: 1=Mon ... 7=Sun
  let diff = target - (base.getDay() === 0 ? 7 : base.getDay())
  if (diff <= 0) diff += 7
  return addDays(base, diff)
}

function extractDate(text: string, base: Date): { date: string | null; rest: string } {
  let rest = text
  let date: string | null = null

  // 今天 / 明天 / 后天 / 大后天
  const relative = rest.match(/(今天|明日|明天|后天|大后天)/)
  if (relative) {
    const kw = relative[1]
    date = kw === '今天' || kw === '明日'
      ? fmtDate(base)
      : kw === '明天'
        ? fmtDate(addDays(base, 1))
        : kw === '后天'
          ? fmtDate(addDays(base, 2))
          : fmtDate(addDays(base, 3))
    rest = rest.replace(relative[0], ' ')
  }

  // 周X / 星期X
  const weekday = rest.match(/(?:周|星期|礼拜)([一二三四五六日天])/)
  if (weekday) {
    const map: Record<string, number> = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 }
    const target = map[weekday[1]]
    if (target) date = fmtDate(nextWeekday(base, target))
    rest = rest.replace(weekday[0], ' ')
  }

  // YYYY-MM-DD / YYYY/MM/DD
  const iso = rest.match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})/)
  if (iso) {
    date = `${iso[1]}-${pad(Number(iso[2]))}-${pad(Number(iso[3]))}`
    rest = rest.replace(iso[0], ' ')
  }

  // X月X日 / X月X号
  const md = rest.match(/(\d{1,2})月(\d{1,2})(?:日|号)/)
  if (md) {
    date = `${base.getFullYear()}-${pad(Number(md[1]))}-${pad(Number(md[2]))}`
    rest = rest.replace(md[0], ' ')
  }

  return { date, rest: rest.replace(/\s+/g, ' ').trim() }
}

function extractTime(text: string): { time: string | null; rest: string } {
  let rest = text
  let time: string | null = null

  // 显式 HH:mm / HH点mm分 / H点
  const explicit = rest.match(/(\d{1,2}):(\d{2})|(\d{1,2})[点时](\d{1,2})?分?/)
  if (explicit) {
    let h = 0
    let m = 0
    if (explicit[1] != null) {
      h = Number(explicit[1])
      m = Number(explicit[2])
    } else {
      h = Number(explicit[3])
      m = explicit[4] ? Number(explicit[4]) : 0
    }
    time = `${pad(h % 24)}:${pad(m)}`
    rest = rest.replace(explicit[0], ' ')
  }

  // 上午/下午/晚上/中午 + 时间
  const period = rest.match(/(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|今晚)\s*(\d{1,2})?[点时]?(\d{1,2})?分?/)
  if (period) {
    let h = 9
    const kw = period[1]
    if (kw === '中午') h = 12
    else if (kw === '下午' || kw === '傍晚') h = 14
    else if (kw === '晚上' || kw === '今晚') h = 20
    else if (kw === '凌晨') h = 6
    if (period[2]) {
      let parsed = Number(period[2])
      if ((kw === '下午' || kw === '傍晚' || kw === '晚上' || kw === '今晚') && parsed < 12) parsed += 12
      h = parsed
    }
    const m = period[3] ? Number(period[3]) : 0
    time = `${pad(h % 24)}:${pad(m)}`
    rest = rest.replace(period[0], ' ')
  }

  // 只有“今晚/晚上”等无数字
  if (!time) {
    const bare = rest.match(/(今晚|晚上|下午|上午|中午|凌晨)/)
    if (bare) {
      const map: Record<string, string> = { '凌晨': '06:00', '上午': '09:00', '中午': '12:00', '下午': '14:00', '晚上': '20:00', '今晚': '20:00' }
      time = map[bare[1]] || null
      rest = rest.replace(bare[0], ' ')
    }
  }

  return { time, rest: rest.replace(/\s+/g, ' ').trim() }
}

function extractRecurrence(text: string): { recurrence: ParsedTodo['recurrence']; rest: string } {
  let rest = text
  let recurrence: ParsedTodo['recurrence'] = null
  const patterns: Array<[RegExp, ParsedTodo['recurrence']]> = [
    [/(每天|每日|天天)/, 'DAILY'],
    [/(每周|每星期|每礼拜)/, 'WEEKLY'],
    [/(每月|每个月)/, 'MONTHLY'],
    [/(每年|每一年)/, 'YEARLY'],
  ]
  for (const [re, r] of patterns) {
    if (re.test(rest)) {
      recurrence = r
      rest = rest.replace(re, ' ')
      break
    }
  }
  return { recurrence, rest: rest.replace(/\s+/g, ' ').trim() }
}

function extractRemind(text: string, dueDate: string | null, dueTime: string | null): { remindAt: string | null; rest: string } {
  let rest = text
  let remindAt: string | null = null
  const m = rest.match(/(?:提醒|提前)\s*(\d{1,3})\s*分钟/)
  if (m && dueDate && dueTime) {
    const minutes = Number(m[1])
    const dt = new Date(`${dueDate}T${dueTime}:00`)
    dt.setMinutes(dt.getMinutes() - minutes)
    remindAt = `${fmtDate(dt)} ${pad(dt.getHours())}:${pad(dt.getMinutes())}:00`
    rest = rest.replace(m[0], ' ')
  }
  return { remindAt, rest: rest.replace(/\s+/g, ' ').trim() }
}

export function parseTodoNlp(input: string): ParsedTodo {
  const text = input.trim()
  const base = new Date()
  const dateStep = extractDate(text, base)
  const timeStep = extractTime(dateStep.rest)
  const recurStep = extractRecurrence(timeStep.rest)
  const remindStep = extractRemind(recurStep.rest, dateStep.date, timeStep.time)

  let title = remindStep.rest
    .replace(/\b高优先级\b|\b紧急\b/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const priority = /高优先级|紧急/.test(text) ? 2 : 0

  // 没有解析出日期时，重复任务默认从今天开始
  let dueDate = dateStep.date
  if (!dueDate && recurStep.recurrence) dueDate = fmtDate(base)

  return {
    title,
    dueDate,
    dueTime: timeStep.time,
    remindAt: remindStep.remindAt,
    recurrence: recurStep.recurrence,
    priority,
  }
}
