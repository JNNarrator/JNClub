/**
 * password.ts — 密码生成器 + 强度检测
 * 强度算法与后端 VaultCrypto.strengthScore 保持一致（长度×4 上限50 + 字符类别×8 上限30 + 常见弱密码 -30）
 */

export interface PasswordOptions {
  length: number
  upper: boolean
  lower: boolean
  digits: boolean
  symbols: boolean
  excludeAmbiguous: boolean
}

export const DEFAULT_PASSWORD_OPTIONS: PasswordOptions = {
  length: 16,
  upper: true,
  lower: true,
  digits: true,
  symbols: true,
  excludeAmbiguous: true,
}

const UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
const LOWER = 'abcdefghijklmnopqrstuvwxyz'
const DIGITS = '0123456789'
const SYMBOLS = '!@#$%^&*()_+-=[]{}|;:,.<>?'
// 易混淆字符（去重后排除）
const AMBIGUOUS = new Set('0O1lI|`\'"`'.split(''))

function pick(set: string, excludeAmbiguous: boolean): string {
  let s = set
  if (excludeAmbiguous) {
    s = [...set].filter((c) => !AMBIGUOUS.has(c)).join('')
    if (s.length === 0) s = set
  }
  return s
}

/** 安全随机整数 [0, n) */
function randInt(n: number): number {
  const buf = new Uint32Array(1)
  crypto.getRandomValues(buf)
  return buf[0] % n
}

/** 洗牌（Fisher–Yates） */
function shuffle(arr: string[]): string[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = randInt(i + 1)
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
  }
  return arr
}

/** 按选项生成随机密码：保证每个启用字符集至少出现一次 */
export function generatePassword(opts: Partial<PasswordOptions> = {}): string {
  const o: PasswordOptions = { ...DEFAULT_PASSWORD_OPTIONS, ...opts }
  const length = Math.max(4, Math.min(128, o.length))

  const pools: string[] = []
  if (o.upper) pools.push(UPPER)
  if (o.lower) pools.push(LOWER)
  if (o.digits) pools.push(DIGITS)
  if (o.symbols) pools.push(SYMBOLS)
  if (pools.length === 0) pools.push(LOWER) // 全关时兜底

  const cleaned = pools.map((p) => pick(p, o.excludeAmbiguous))
  const chars: string[] = []

  // 每个启用的字符集先放一个
  for (const pool of cleaned) {
    chars.push(pool[randInt(pool.length)])
  }
  // 剩余位从合并池随机取
  const merged = cleaned.join('')
  while (chars.length < length) {
    chars.push(merged[randInt(merged.length)])
  }
  return shuffle(chars).join('')
}

/** 常见弱密码（与后端一致） */
const COMMON_WEAK = [
  '123456', '12345678', '123456789', '1234567890', 'password', 'password1',
  '1234567', '12345', 'qwerty', 'abc123', '111111', '000000', 'iloveyou',
  'admin', 'admin123', 'test', 'test123', 'letmein', 'welcome', 'monkey',
]

export interface PasswordStrength {
  score: number // 0-100
  level: 'weak' | 'medium' | 'strong'
  label: string
}

const LEVEL_LABEL: Record<PasswordStrength['level'], string> = {
  weak: '弱',
  medium: '中',
  strong: '强',
}

export function passwordStrength(plain: string): PasswordStrength {
  if (!plain) return { score: 0, level: 'weak', label: '弱' }
  const len = plain.length
  let score = 0

  // 长度权重（满分 50）
  score += Math.min(50, len * 4)

  // 字符类别多样性（满分 30）
  let variety = 0
  if (/[A-Z]/.test(plain)) variety++
  if (/[a-z]/.test(plain)) variety++
  if (/[0-9]/.test(plain)) variety++
  if (/[^A-Za-z0-9]/.test(plain)) variety++
  score += variety * 8

  // 常见弱密码惩罚（-30）
  if (len <= 12) {
    const lower = plain.toLowerCase()
    if (COMMON_WEAK.includes(lower)) score -= 30
  }

  score = Math.max(0, Math.min(100, score))
  const level: PasswordStrength['level'] = score < 60 ? 'weak' : score < 80 ? 'medium' : 'strong'
  return { score, level, label: LEVEL_LABEL[level] }
}
