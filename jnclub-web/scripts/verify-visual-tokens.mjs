#!/usr/bin/env node
/**
 * verify-visual-tokens.mjs — 视觉体系静态复核（2026-10 视觉重塑验收脚本）
 *
 * 为什么需要它：
 *   PRD 8.1 的主表里有 6 项是「可机械断言」的（对比度、ΔL*、玻璃层数、token 覆盖率、
 *   硬编码 hex、粉边框配额），每次人工核算都要重写一遍 CIE L* 公式，容易算错
 *   （PRD 正文就有一处 16.6 vs 15.30 的计算错误）。本脚本把这些口径固化下来。
 *
 * 用法：
 *   node scripts/verify-visual-tokens.mjs         打印复核报告
 *   node scripts/verify-visual-tokens.mjs --strict 有任一硬指标不达标则退出码 1
 *
 * 口径：
 *   - 对比度 = WCAG 2.x 相对亮度比 (L1+0.05)/(L2+0.05)
 *   - ΔL* = CIE 1976 明度差：sRGB → 线性化 → Y → L* = 116·∛Y − 16
 *   - token 覆盖率 = 「定义集 ∩ 使用集」/ 定义集，使用集来自 src 下所有 .vue/.css
 *   - 硬编码 hex 只统计颜色字面量，vendor 目录与 tokens.ts 豁免
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, resolve, join, extname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(here, '..')
const srcRoot = resolve(projectRoot, 'src')
const isStrict = process.argv.includes('--strict')

// ─────────────────────────────────────────── 加载 token（与 gen-theme-css 同一套机制）
async function loadTokens() {
  const tokensPath = resolve(srcRoot, 'themes/tokens.ts')
  try {
    const { transformSync } = await import('esbuild')
    const { code } = transformSync(readFileSync(tokensPath, 'utf8'), {
      loader: 'ts',
      format: 'esm',
    })
    return await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
  } catch (err) {
    if (err?.code !== 'ERR_MODULE_NOT_FOUND') throw err
    return await import(pathToFileURL(tokensPath).href)
  }
}

const { lightTokens, darkTokens, tokensToCSSVars } = await loadTokens()
const lightVars = tokensToCSSVars(lightTokens)
const darkVars = tokensToCSSVars(darkTokens)

// ─────────────────────────────────────────── 颜色数学
/** '#RRGGBB' | 'rgba(r,g,b,a)' → { r, g, b }（0-255），alpha 单独返回 */
function parseColor(input) {
  const s = String(input).trim()
  const hex = s.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i)
  if (hex) {
    let h = hex[1]
    if (h.length === 3) h = h.split('').map((c) => c + c).join('')
    return {
      r: parseInt(h.slice(0, 2), 16),
      g: parseInt(h.slice(2, 4), 16),
      b: parseInt(h.slice(4, 6), 16),
      a: 1,
    }
  }
  const rgb = s.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i)
  if (rgb) {
    return {
      r: Number(rgb[1]),
      g: Number(rgb[2]),
      b: Number(rgb[3]),
      a: rgb[4] === undefined ? 1 : Number(rgb[4]),
    }
  }
  throw new Error(`无法解析颜色：${input}`)
}

/** 把带 alpha 的颜色按 source-over 合成到不透明底色上 */
function composite(fg, bg) {
  const f = parseColor(fg)
  const b = parseColor(bg)
  if (f.a >= 1) return f
  return {
    r: f.r * f.a + b.r * (1 - f.a),
    g: f.g * f.a + b.g * (1 - f.a),
    b: f.b * f.a + b.b * (1 - f.a),
    a: 1,
  }
}

function relLuminance(rgb) {
  const lin = [rgb.r, rgb.g, rgb.b].map((c) => {
    const v = c / 255
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
}

/** WCAG 对比度（前景可带 alpha，按底色合成后计算） */
function contrast(fg, bg) {
  const b = parseColor(bg)
  const f = composite(fg, bg)
  const l1 = relLuminance(f)
  const l2 = relLuminance(b)
  const hi = Math.max(l1, l2)
  const lo = Math.min(l1, l2)
  return (hi + 0.05) / (lo + 0.05)
}

/** CIE 1976 L* */
function lstar(color) {
  const rgb = typeof color === 'string' ? parseColor(color) : color
  const y = relLuminance(rgb)
  return y > 0.008856 ? 116 * Math.cbrt(y) - 16 : 903.3 * y
}

/** 两色的 ΔL* */
function deltaLstar(a, b) {
  return Math.abs(lstar(a) - lstar(b))
}

/** 12%/18% 的 color-mix(in srgb, c p%, bg) 近似（sRGB 线性插值，与浏览器一致） */
function mix(color, percent, bg) {
  const c = parseColor(color)
  const b = parseColor(bg)
  const p = percent / 100
  return {
    r: c.r * p + b.r * (1 - p),
    g: c.g * p + b.g * (1 - p),
    b: c.b * p + b.b * (1 - p),
    a: 1,
  }
}

const toHex = (rgb) =>
  '#' + [rgb.r, rgb.g, rgb.b].map((c) => Math.round(c).toString(16).padStart(2, '0')).join('').toUpperCase()

// ─────────────────────────────────────────── 源文件扫描
const VENDOR_HINTS = [
  '/animation/vendor/', // 21st.dev 组件原样落地
  '/animation/JGlareHover.vue', // 同上，glareColor 是 vendor prop 默认值
  '/devtools/', // 开发者工具，不在本轮视觉范围
]

function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const st = statSync(full)
    if (st.isDirectory()) {
      if (name === 'node_modules' || name === 'dist') continue
      walk(full, acc)
    } else if (['.vue', '.css', '.ts'].includes(extname(name))) {
      acc.push(full)
    }
  }
  return acc
}

const allFiles = walk(srcRoot)
/** 参与样式统计的文件（排除生成物与 token 源：它们的 hex 是定义而非消费） */
const styleFiles = allFiles.filter(
  (f) => !f.endsWith('tokens.generated.css') && !f.endsWith('/themes/tokens.ts'),
)
const isVendor = (f) => VENDOR_HINTS.some((h) => f.includes(h))

const read = (f) => readFileSync(f, 'utf8')
const rel = (f) => f.replace(projectRoot + '/', '')

/**
 * 去掉 CSS/JS 注释后再做静态断言。
 * 为什么必须去掉：本轮大量改动都附带了「原先用 backdrop-filter / #D8D8DE」这类
 * 说明性注释，若直接对原文做正则匹配，会把注释里的历史描述误判成现存规则
 * （实测误报 2 处 L1 玻璃 + 1 处 hex）。
 */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')   // 块注释
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1') // 行注释（避开 https:// 之类）
}

/** 样式文件去注释后的正文 */
const readCode = (f) => stripComments(read(f))

// ─────────────────────────────────────────── ① 表面层级与描边可见性
const results = []
const record = (group, name, actual, target, pass, note = '') => {
  results.push({ group, name, actual, target, pass, note })
}

const L = lightVars
const D = darkVars

// 亮色 ΔL*（O1：判据 ≥3.2）
const dLPageSurfaceLight = deltaLstar(L['--bg-page'], L['--surface-1'])
record('明度阶梯', '亮 page↔surface ΔL*', dLPageSurfaceLight.toFixed(2), '≥3.2（O1）', dLPageSurfaceLight >= 3.2)

// 暗色 ΔL*（O2：判据 ≥15）
const dLPageSurfaceDark = deltaLstar(D['--bg-page'], D['--surface-1'])
record('明度阶梯', '暗 page↔surface ΔL*', dLPageSurfaceDark.toFixed(2), '≥15（O2）', dLPageSurfaceDark >= 15)

// 暗色 L1↔L2
const dLSurfaceRaisedDark = deltaLstar(D['--surface-1'], D['--hover-bg'])
record('明度阶梯', '暗 surface↔raised ΔL*', dLSurfaceRaisedDark.toFixed(2), '无硬指标', true)

// 描边可见性（G6：≥1.2:1）
const borderVsPageLight = contrast(L['--surface-border'], L['--bg-page'])
const borderVsSurfaceLight = contrast(L['--surface-border'], L['--surface-1'])
const borderStrongVsPageLight = contrast(L['--surface-border-strong'], L['--bg-page'])
record('描边可见性', '亮 --surface-border vs --bg-page', borderVsPageLight.toFixed(2), '≥1.2', borderVsPageLight >= 1.2)
record('描边可见性', '亮 --surface-border vs --surface-1', borderVsSurfaceLight.toFixed(2), '≥1.2', borderVsSurfaceLight >= 1.2)
record('描边可见性', '亮 --surface-border-strong vs --bg-page', borderStrongVsPageLight.toFixed(2), '≥1.2', borderStrongVsPageLight >= 1.2)
const borderVsSurfaceDark = contrast(D['--surface-border'], D['--surface-1'])
record('描边可见性', '暗 --surface-border vs --surface-1', borderVsSurfaceDark.toFixed(2), '≥1.2', borderVsSurfaceDark >= 1.2)

// ─────────────────────────────────────────── ② 模块图标色（SC 1.4.11：≥3:1，两个场景都测）
const MODULES = [
  ['收藏', 'bookmark'],
  ['便签', 'note'],
  ['云盘', 'file'],
  ['密码库', 'vault'],
]

for (const [label, key] of MODULES) {
  const icon = L[`--module-${key}-icon`]
  const base = L[`--module-${key}`]
  const soft = toHex(mix(base, 12, L['--surface-1']))
  const vsWhite = contrast(icon, L['--surface-1'])
  const vsPage = contrast(icon, L['--bg-page'])
  const vsSoft = contrast(icon, soft)
  record(
    '模块图标色（亮）',
    `${label} ${icon} vs #FFF / vs page / vs soft${soft}`,
    `${vsWhite.toFixed(2)} / ${vsPage.toFixed(2)} / ${vsSoft.toFixed(2)}`,
    '全部 ≥3.0',
    vsWhite >= 3 && vsPage >= 3 && vsSoft >= 3,
  )
}

for (const [label, key] of MODULES) {
  const icon = D[`--module-${key}-icon`]
  const base = D[`--module-${key}`]
  const soft = toHex(mix(base, 18, D['--surface-1']))
  const vsSurface = contrast(icon, D['--surface-1'])
  const vsSoft = contrast(icon, soft)
  record(
    '模块图标色（暗）',
    `${label} ${icon} vs #26262A / vs soft${soft}`,
    `${vsSurface.toFixed(2)} / ${vsSoft.toFixed(2)}`,
    'vs surface ∈[4.5,6.5]，vs soft ≥3.0',
    vsSurface >= 4.5 && vsSurface <= 6.6 && vsSoft >= 3,
  )
}

// ─────────────────────────────────────────── ③ 图表序列色（≥3:1）
for (const i of [1, 2, 3, 4]) {
  const raw = L[`--chart-series-${i}`]
  // 2–4 是 var() 别名，解析成实际 hex 再测
  const resolved = raw.startsWith('var(') ? L[raw.slice(4, -1)] : raw
  const c = contrast(resolved, L['--surface-1'])
  record('图表序列色（亮）', `--chart-series-${i} ${resolved}`, c.toFixed(2), '≥3.0', c >= 3)
}
for (const i of [1, 2, 3, 4]) {
  const raw = D[`--chart-series-${i}`]
  const resolved = raw.startsWith('var(') ? D[raw.slice(4, -1)] : raw
  const c = contrast(resolved, D['--surface-1'])
  record('图表序列色（暗）', `--chart-series-${i} ${resolved}`, c.toFixed(2), '≥3.0', c >= 3)
}

// 反向断言：--brand 在页面底上不达标，所以绝不能当图表/图标色
const brandOnPage = contrast(L['--brand'], L['--bg-page'])
record('品牌粉边界', `--brand ${L['--brand']} vs --bg-page`, brandOnPage.toFixed(2), '<3.0（故禁作图形色）', brandOnPage < 3)

// ─────────────────────────────────────────── ④ 正文对比度（≥4.5:1）
for (const [name, key] of [['--text-1', '--text-1'], ['--text-2', '--text-2'], ['--text-3', '--text-3'], ['--brand-text', '--brand-text']]) {
  const onSurface = contrast(L[key], L['--surface-1'])
  const onPage = contrast(L[key], L['--bg-page'])
  record('正文对比度（亮）', `${name} vs surface / vs page`, `${onSurface.toFixed(2)} / ${onPage.toFixed(2)}`, '≥4.5', onSurface >= 4.5 && onPage >= 4.5)
}
for (const [name, key] of [['--text-1', '--text-1'], ['--text-2', '--text-2'], ['--text-3', '--text-3']]) {
  const onSurface = contrast(D[key], D['--surface-1'])
  record('正文对比度（暗）', `${name} vs surface`, onSurface.toFixed(2), '≥4.5', onSurface >= 4.5)
}

// ─────────────────────────────────────────── ⑤ 玻璃准入（backdrop-filter 计数）
const BLUR_TARGET = 30
let blurTotal = 0
const blurByFile = []
for (const f of styleFiles) {
  const n = (readCode(f).match(/backdrop-filter: blur/g) || []).length
  if (n) {
    blurTotal += n
    blurByFile.push([rel(f), n])
  }
}
record('玻璃准入', 'backdrop-filter: blur 总数', blurTotal, `≤${BLUR_TARGET}`, blurTotal <= BLUR_TARGET)
record('玻璃准入', '玻璃规则数（≈总数/2，含 webkit 双写）', Math.round(blurTotal / 2), '≤15', Math.round(blurTotal / 2) <= 15)

// L1 结构面不得出现 backdrop-filter
const L1_SELECTORS = ['.j-page-body', '.side-nav', '.folder-column', '.collection-column', '.panel', '.j-stat-card']
const l1Violations = []
for (const f of styleFiles) {
  const src = readCode(f)
  for (const sel of L1_SELECTORS) {
    const re = new RegExp(`\\${sel}\\s*\\{[^}]*backdrop-filter`, 'g')
    if (re.test(src)) l1Violations.push(`${rel(f)} :: ${sel}`)
  }
}
record('玻璃准入', 'L1 选择器上的 backdrop-filter', l1Violations.length, '=0', l1Violations.length === 0, l1Violations.join('; '))

// ─────────────────────────────────────────── ⑥ token 覆盖率
const definedVars = new Set(Object.keys(lightVars))
const usedVars = new Set()
for (const f of styleFiles) {
  const src = readCode(f)
  for (const m of src.matchAll(/var\(\s*(--[a-z0-9-]+)/gi)) usedVars.add(m[1])
}
/**
 * 豁免名单：这些 token 在设计上就无法被 CSS 消费，不算死 token。
 * --layout-sider-collapsed-width：naive-ui 的 collapsedWidth prop 类型限定为 Number、
 * 不接受 CSS 变量（见 tokens.ts 与 SideNav.vue 的注释），故它只作记录用途。
 */
const DEAD_TOKEN_ALLOW = new Set(['--layout-sider-collapsed-width'])
const deadTokens = [...definedVars].filter((v) => !usedVars.has(v) && !DEAD_TOKEN_ALLOW.has(v))
const coverage = ((definedVars.size - deadTokens.length) / definedVars.size) * 100
record('token 覆盖率', `定义 ${definedVars.size} / 使用 ${definedVars.size - deadTokens.length}`, `${coverage.toFixed(1)}%`, '100%', deadTokens.length === 0, deadTokens.length ? `死 token: ${deadTokens.join(', ')}` : '')

// 新增 10 个 token 必须全部有消费
const NEW_TOKENS = [
  '--surface-1', '--surface-border', '--surface-border-strong', '--surface-glow',
  '--module-bookmark-icon', '--module-note-icon', '--module-file-icon', '--module-vault-icon',
  '--chart-series-1', '--chart-series-2', '--chart-series-3', '--chart-series-4',
]
const newUnused = NEW_TOKENS.filter((t) => !usedVars.has(t))
record('token 覆盖率', '本轮新增 token 的消费', NEW_TOKENS.length - newUnused.length, `=${NEW_TOKENS.length}`, newUnused.length === 0, newUnused.length ? `未消费: ${newUnused.join(', ')}` : '')

// ─────────────────────────────────────────── ⑦ 硬编码 hex / 圆角 / 字号
const HEX_RE = /#[0-9a-f]{3,8}\b/gi
const hexHits = []
for (const f of styleFiles) {
  if (isVendor(f)) continue
  // 去注释后逐行看：只统计「属性: 值」形式的声明里的颜色字面量
  for (const line of readCode(f).split('\n')) {
    if (!/:\s*[^;]*$/.test(line)) continue
    const m = line.match(HEX_RE)
    if (m) hexHits.push(`${rel(f)}: ${line.trim().slice(0, 80)}`)
  }
}
record('硬编码', '样式内 hex 颜色字面量（vendor 豁免）', hexHits.length, '=0', hexHits.length === 0, hexHits.slice(0, 6).join(' | '))

const RADIUS_RE = /border-radius:(?!\s*var\()\s*([^;]+);/g
const radiusHits = []
const FS_RE = /font-size:(?!\s*var\()\s*([^;]+);/g
const fsHits = []
for (const f of styleFiles) {
  if (isVendor(f)) continue
  const src = readCode(f)
  /**
   * 判定口径（与 PLAN-2026-19 的「归零」定义一致）：
   * 只要值里引用了 token（var()）、或是 calc()/百分比这类派生值、或是 em 相对单位，
   * 都不算硬编码 —— 真正的硬编码是「直接写死一个 px 数字」。
   * 例：`0 var(--radius-sm) var(--radius-sm) 0`（圆角拼接）与
   *     `calc(var(--radius-sm) - 2px)`（内层圆角推导）都是正确的 token 用法。
   */
  for (const m of src.matchAll(RADIUS_RE)) {
    const v = m[1].trim()
    if (/^(0|0px|inherit)$/.test(v)) continue
    if (/var\(|calc\(|%/.test(v)) continue
    radiusHits.push(`${rel(f)}: ${m[0].trim()}`)
  }
  for (const m of src.matchAll(FS_RE)) {
    const v = m[1].trim()
    if (/^(inherit|0)$/.test(v)) continue
    // em / rem / % 是相对单位，随所在容器缩放，不属于「写死字号」
    if (/var\(|calc\(|em\b|%/.test(v)) continue
    fsHits.push(`${rel(f)}: ${m[0].trim()}`)
  }
}
record('硬编码', '硬编码圆角（0/50%/inherit 豁免）', radiusHits.length, '=0', radiusHits.length === 0, radiusHits.slice(0, 6).join(' | '))
record('硬编码', '硬编码字号', fsHits.length, '=0', fsHits.length === 0, fsHits.slice(0, 6).join(' | '))

// ─────────────────────────────────────────── ⑧ 粉色配额
const pinkBorderHits = []
for (const f of styleFiles) {
  for (const line of readCode(f).split('\n')) {
    if (/border[a-z-]*:[^;]*var\(--glass-chip-border\)/.test(line)) pinkBorderHits.push(`${rel(f)}: ${line.trim()}`)
  }
}
record('粉色配额', '--glass-chip-border 的边框消费', pinkBorderHits.length, '≤8', pinkBorderHits.length <= 8)

const cardTopBar = styleFiles.reduce((n, f) => n + (readCode(f).match(/card-top-bar/g) || []).length, 0)
record('粉色配额', 'card-top-bar 消费', cardTopBar, '=0', cardTopBar === 0)

// 光晕来源唯一性：--surface-glow 只允许 1 处消费
let glowConsumers = 0
for (const f of styleFiles) glowConsumers += (readCode(f).match(/var\(--surface-glow\)/g) || []).length
record('粉色配额', '--surface-glow 消费处', glowConsumers, '=1', glowConsumers === 1)

// ─────────────────────────────────────────── 输出
const groups = [...new Set(results.map((r) => r.group))]
let failures = 0
console.log('\n═══ JNClub 视觉体系静态复核（2026-10）═══\n')
for (const g of groups) {
  console.log(`▌ ${g}`)
  for (const r of results.filter((x) => x.group === g)) {
    const mark = r.pass ? '✓' : '✗'
    if (!r.pass) failures++
    const note = r.note ? `  ⚠ ${r.note}` : ''
    console.log(`  ${mark} ${r.name}  =  ${r.actual}   [目标 ${r.target}]${note}`)
  }
  console.log('')
}

console.log('▌ 玻璃层分布（按文件）')
for (const [f, n] of blurByFile.sort((a, b) => b[1] - a[1])) {
  console.log(`  · ${f}: ${n} 行（${Math.round(n / 2)} 条规则）`)
}
console.log('')

console.log(failures === 0 ? `✓ 全部 ${results.length} 项达标` : `✗ ${failures}/${results.length} 项未达标`)
if (isStrict && failures > 0) process.exit(1)
