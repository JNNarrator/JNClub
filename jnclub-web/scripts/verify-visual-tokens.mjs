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

/**
 * 极简 CSS 规则块扫描（QA 2026-10 新增）。
 * 只取「最内层规则块」：`@media { .x { … } }` 会被拆成内层的 `.x`，
 * 因为 `[^{}]*` 不允许跨过大括号 —— 对本项目的用例足够。
 */
function cssBlocks(src) {
  const out = []
  const re = /([^{}]+)\{([^{}]*)\}/g
  let m
  while ((m = re.exec(src))) out.push({ selector: m[1].trim(), body: m[2] })
  return out
}

/**
 * 只取「样式正文」：`.vue` 文件截取所有 <style> 块，`.css` 直接返回。
 * 不做这一步的话，cssBlocks 会把 <template> 里的标签文本当成选择器，
 * 报错信息会变成一段 HTML 片段（实测踩到）。
 */
function styleSource(f) {
  const code = readCode(f)
  if (!f.endsWith('.vue')) return code
  const blocks = [...code.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => m[1])
  return blocks.join('\n')
}

// ─────────────────────────────────────────── ① 表面层级与描边可见性
const results = []
const record = (group, name, actual, target, pass, note = '') => {
  results.push({ group, name, actual, target, pass, note })
}

const L = lightVars
const D = darkVars

/**
 * ⚠ 判据口径修正（QA 2026-10 复核）：
 *   原脚本把 PRD 8.1 的 [3.5, 6] 与 ≥16 直接换成了 DESIGN §12 的 O1/O2 备选判据
 *   （≥3.2 / ≥15）并标 ✓。但 O1/O2 在 DESIGN 里是**「开放项，需用户拍板」**，
 *   不是已生效的验收标准。用未获批的放宽判据给出绿灯，等于让 PRD 的硬指标静默失效。
 *   现改为：**按 PRD 原文判据判定**，偏差写在 note 里，失败即失败（--strict 会拦）。
 */
const dLPageSurfaceLight = deltaLstar(L['--bg-page'], L['--surface-1'])
record(
  '明度阶梯', '亮 page↔surface ΔL*', dLPageSurfaceLight.toFixed(2), '∈[3.5, 6]（PRD 8.1）',
  dLPageSurfaceLight >= 3.5 && dLPageSurfaceLight <= 6,
  dLPageSurfaceLight < 3.5
    ? `低于下限 3.5（Q1 决定不动 --bg-page 的直接后果）；DESIGN O1 建议改为 ≥3.2，但属待拍板开放项，不能自动视为达标`
    : '',
)

const dLPageSurfaceDark = deltaLstar(D['--bg-page'], D['--surface-1'])
record(
  '明度阶梯', '暗 page↔surface ΔL*', dLPageSurfaceDark.toFixed(2), '≥16（PRD 8.1）',
  dLPageSurfaceDark >= 16,
  dLPageSurfaceDark < 16
    ? `低于 16（PRD 正文「16.6」计算有误，CIE 复算为 15.30）；DESIGN O2 建议改为 ≥15，同为待拍板开放项`
    : '',
)

// 暗色 L1↔L2：PRD 无硬指标，但「L2 必须能从 L1 抬起来」是四层体系的前提，给一个真实下限
const dLSurfaceRaisedDark = deltaLstar(D['--surface-1'], D['--hover-bg'])
record('明度阶梯', '暗 surface↔raised ΔL*', dLSurfaceRaisedDark.toFixed(2), '≥3（L1→L2 可辨，参考值）', dLSurfaceRaisedDark >= 3)

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
    // 原脚本这里写成 `<= 6.6`，而目标字符串写的是 6.5 —— 密码库实测 6.57 因此「通过」。
    // 上限放宽 0.1 是为了让 6.57 过关，属于把判据往结果上凑。恢复 PRD/DESIGN 的 6.5。
    vsSurface >= 4.5 && vsSurface <= 6.5 && vsSoft >= 3,
    vsSurface > 6.5 ? '超出 6.5 上限；DESIGN §3.3 已给出备选值 #E59A38（6.47），应改 token 而不是放宽判据' : '',
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
const BLUR_TARGET = 50
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
record('玻璃准入', '玻璃规则数（≈总数/2，含 webkit 双写）', Math.round(blurTotal / 2), '≤25', Math.round(blurTotal / 2) <= 25)

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
record('玻璃准入', 'L1 选择器上的 backdrop-filter', l1Violations.length, '≤4（玻璃升级版）', l1Violations.length <= 4, l1Violations.join('; '))

/**
 * G5 的正题（QA 2026-10 补）：PRD 2.2 G5 与 8.1 都写的是
 *   「L1 surface 的 background-color alpha 必须是 1（不允许 rgba / 玻璃）」，
 * 原脚本只断言了「没有 backdrop-filter」—— 这是**近似物**：
 * `background: rgba(255,255,255,.72)` 不带 blur 也能通过，但它仍然不是实色。
 * 这里改成直接看声明值：L1 选择器的 background 必须引用不透明 token。
 */
const OPAQUE_TOKENS = ['--surface-1', '--bg-card', '--bg-page', '--hover-bg', '--glass-bg-solid', '--panel-bg', '--glass-surface-bg']
const l1AlphaViolations = []
let l1Scanned = 0
for (const f of styleFiles) {
  for (const { selector, body } of cssBlocks(styleSource(f))) {
    if (!L1_SELECTORS.some((sel) => selector.includes(sel))) continue
    const decl = [...body.matchAll(/(?:^|[;{])\s*background(?:-color)?\s*:\s*([^;]+)/g)].map((m) => m[1].trim())
    if (!decl.length) continue
    l1Scanned++
    const last = decl[decl.length - 1]
    const usesOpaqueToken = OPAQUE_TOKENS.some((t) => last.includes(`var(${t})`))
    const hasAlphaLiteral = /rgba\(|transparent|glass-bg-trans/.test(last)
    if (!usesOpaqueToken || hasAlphaLiteral) {
      l1AlphaViolations.push(`${rel(f)} :: ${selector} → background: ${last.slice(0, 60)}`)
    }
  }
}
/**
 * 自校验：把「扫到几条 L1 规则」一并报出来。
 * 否则正则一旦失效（例如注释夹在 `;` 与 `background` 之间导致锚点失配），
 * 断言会以「0 违规」的姿态静默通过 —— 这正是本次审计在别人脚本里抓到的问题模式。
 */
record(
  '玻璃准入', `L1 选择器的 background 为实色（扫描 ${l1Scanned} 条规则）`, l1AlphaViolations.length,
  '=0 违规且扫描数 ≥6', l1AlphaViolations.length === 0 && l1Scanned >= 6,
  l1AlphaViolations.slice(0, 4).join('; '),
)

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

// 新增 token 必须全部有消费
const NEW_TOKENS = [
  '--surface-1', '--surface-border', '--surface-border-strong', '--surface-glow',
  '--module-bookmark-icon', '--module-note-icon', '--module-file-icon', '--module-vault-icon',
  '--chart-series-1', '--chart-series-2', '--chart-series-3', '--chart-series-4',
  '--aurora-top', '--aurora-bottom', '--aurora-center',
  '--glass-surface-bg', '--glass-surface-border', '--glass-surface-shadow',
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
record('粉色配额', 'card-top-bar 类名消费', cardTopBar, '=0', cardTopBar === 0)

/**
 * PRD 8.1 这一行的原文是「卡片顶部 3px 品牌渐变条（`--gradient-top-bar`）**消费处** 1 → 0」，
 * 计量单位是 **token 消费**，不是类名。原脚本只数了 `card-top-bar` 类名 ——
 * 只要把顶条换个类名（或直接在别的类里写 background: var(--gradient-top-bar)）就能绕过。
 * 这里补上真正的口径。
 */
const topBarToken = styleFiles.reduce((n, f) => n + (readCode(f).match(/var\(--gradient-top-bar\)/g) || []).length, 0)
record('粉色配额', '--gradient-top-bar token 消费处', topBarToken, '=0', topBarToken === 0)

// 光晕来源唯一性：--surface-glow 只允许 1 处消费
let glowConsumers = 0
for (const f of styleFiles) glowConsumers += (readCode(f).match(/var\(--surface-glow\)/g) || []).length
record('粉色配额', '--surface-glow 消费处', glowConsumers, '=1', glowConsumers === 1)

// --surface-glow 的 alpha 必须 ≤6%（PRD 4.2「收敛为最多 1 层，且透明度 ≤6%」）
const glowAlpha = (() => {
  const m = String(L['--surface-glow']).match(/rgba?\(\s*[\d.]+\s*,\s*[\d.]+\s*,\s*[\d.]+\s*,\s*([\d.]+)\s*\)/)
  return m ? Number(m[1]) : null
})()
record('粉色配额', '--surface-glow 的 alpha', glowAlpha === null ? '无法解析' : glowAlpha, '≤0.06', glowAlpha !== null && glowAlpha <= 0.06)

/**
 * ⚠ 原脚本最大的盲区（QA 2026-10 补）：
 *   「--surface-glow 消费处 = 1」只能证明**新 token** 只用了一次，
 *   证明不了「页面底不再有粉色光晕」。而 PRD 4.2 明令禁止的正是后者。
 *   实测 `--glass-glow-top`（亮色 rgba(236,91,142,0.16)）仍被当作**页面底色**用在
 *   Welcome / ExtensionPage / NoteEditorPage 三处 —— 每个页面还是 2 层 16%/14% 粉光。
 *   这里按「选择器是否属于 L3 浮层」区分：浮层内的品牌光晕合法，页面级的不合法。
 */
const L3_SELECTOR_HINTS = [
  '.n-modal', '.n-dialog', '.n-drawer', '.n-popover', '.n-message', '.n-notification',
  '.glass-card--modal', '.glass-header', '.glass-chip', '.mobile-tabbar', '.j-batch-bar',
  '.trend-tip', '.outline-float', '.backdrop-blur',
]
const pageGlowViolations = []
for (const f of styleFiles) {
  for (const { selector, body } of cssBlocks(styleSource(f))) {
    if (!/var\(--glass-glow-(top|bottom)\)/.test(body)) continue
    if (L3_SELECTOR_HINTS.some((h) => selector.includes(h))) continue
    pageGlowViolations.push(`${rel(f)} :: ${selector.slice(0, 60)}`)
  }
}
record(
  '粉色配额', '页面级（非 L3 浮层）品牌光晕', pageGlowViolations.length, '=0（PRD 4.2）',
  pageGlowViolations.length === 0, pageGlowViolations.slice(0, 6).join('; '),
)

// ─────────────────────────────────────────── 已声明偏差（需主理人拍板）
/**
 * PRD 8.1 的两条明度阶梯判据，在 Q1/Q6「不动 --bg-page」的前提下数学上不可达：
 *   亮：#F5F5F7 → #FFFFFF 的 ΔL* 上限就是 3.41，要到 3.5 必须压深 --bg-page；
 *   暗：#000 → #26262A 是 15.30（PRD 正文「16.6」算错），要到 16 必须提亮 --bg-card，
 *       会连锁 20+ 处面板底色。
 * DESIGN §12 的 O1/O2 提案把判据放宽为 亮 ≥3.2 / 暗 ≥15，并追加硬条件「描边 ≥1.2:1」。
 *
 * 处理原则（QA 立场）：
 *   - **默认不放行**：不加开关时按 PRD 原文判据判，仍报 ✗，避免「放宽判据 → 静默转绿」。
 *   - 加 `--accept-approved` 才按放宽判据放行，且输出里保留 `⚠ 已批准偏差` 字样，
 *     并计入单独的「已批准偏差」计数 —— 它永远不算「达标项」。
 *   - 若哪天 --bg-page / --bg-card 调整到位，这几项会自然回绿，届时应删除本表。
 */
const ACCEPT_APPROVED = process.argv.includes('--accept-approved')
const APPROVED_DEVIATIONS = {
  '亮 page↔surface ΔL*': { floor: 3.2, ref: 'DESIGN §12 O1 + tokens.ts surface1 注释' },
  '暗 page↔surface ΔL*': { floor: 15, ref: 'DESIGN §12 O2 + tokens.ts surface1 注释' },
}

// ─────────────────────────────────────────── 输出
const groups = [...new Set(results.map((r) => r.group))]
let failures = 0
let approvedCount = 0
console.log('\n═══ JNClub 视觉体系静态复核（2026-10）═══\n')
for (const g of groups) {
  console.log(`▌ ${g}`)
  for (const r of results.filter((x) => x.group === g)) {
    let mark = r.pass ? '✓' : '✗'
    let note = r.note ? `  ⚠ ${r.note}` : ''
    if (!r.pass && ACCEPT_APPROVED) {
      const dev = APPROVED_DEVIATIONS[r.name]
      if (dev && Number(r.actual) >= dev.floor) {
        mark = '◐'
        approvedCount++
        note = `  ◐ 已批准偏差：PRD 原文 ${r.target}，实测 ${r.actual} 未达；按放宽判据 ≥${dev.floor} 放行（依据 ${dev.ref}，需主理人书面确认）`
      }
    }
    if (mark === '✗') failures++
    console.log(`  ${mark} ${r.name}  =  ${r.actual}   [目标 ${r.target}]${note}`)
  }
  console.log('')
}

console.log('▌ 玻璃层分布（按文件）')
for (const [f, n] of blurByFile.sort((a, b) => b[1] - a[1])) {
  console.log(`  · ${f}: ${n} 行（${Math.round(n / 2)} 条规则）`)
}
console.log('')

const hardPass = results.length - failures - approvedCount
console.log(
  failures === 0
    ? `✓ ${hardPass} 项达标${approvedCount ? ` + ${approvedCount} 项已批准偏差（不计入达标）` : ''}，共 ${results.length} 项`
    : `✗ ${failures}/${results.length} 项未达标${approvedCount ? `（另有 ${approvedCount} 项已批准偏差）` : ''}`,
)
if (isStrict && failures > 0) process.exit(1)
