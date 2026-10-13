#!/usr/bin/env node
/**
 * gen-theme-css.mjs — 由 src/themes/tokens.ts 生成 src/themes/tokens.generated.css
 *
 * 为什么需要它：
 *   此前 117 个 CSS 变量由 App.vue 在 onMounted 里逐个 setProperty 注入，
 *   导致首帧所有 var(--x) 全部无值回退（FOUC），且硬编码颜色永不随主题变化。
 *   改为构建期生成静态 CSS 后，变量随阻塞式 <link> 一起生效，首帧即正确。
 *
 * 生成的四个块（按 specificity 递进，互斥不冲突）：
 *   1. :root                                  → 亮色（无 data-theme 时的默认）
 *   2. @media (prefers-color-scheme: dark)
 *        :root:not([data-theme])              → 系统暗色兜底（脚本未执行也不闪）
 *   3. :root[data-theme='light']              → 用户显式选亮色（压过系统暗色）
 *   4. :root[data-theme='dark']               → 用户显式选暗色
 *
 * 用法：
 *   node scripts/gen-theme-css.mjs           生成（dev/build 由 predev/prebuild 自动调用）
 *   node scripts/gen-theme-css.mjs --check   仅校验生成物是否为最新（CI 用），不写入
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(here, '..')
const tokensPath = resolve(projectRoot, 'src/themes/tokens.ts')
const outPath = resolve(projectRoot, 'src/themes/tokens.generated.css')

const isCheck = process.argv.includes('--check')

/**
 * 加载 tokens.ts。
 *
 * 本地 Node ≥ 22.6 可直接 import .ts（type stripping 默认开启），
 * 但**服务器是 v18.19.1**，会抛 ERR_UNKNOWN_FILE_EXTENSION，导致 prebuild 中断、
 * 整个部署失败。故统一走 esbuild（vite 的传递依赖，engines 为 node>=18）做纯类型
 * 擦除，转成 JS 后经 data URL 导入：不落临时文件，也不依赖 tsconfig。
 *
 * 只有 esbuild 不可用时才回退到原生 .ts 导入。
 */
async function loadTokens() {
  try {
    const { transformSync } = await import('esbuild')
    const { code } = transformSync(readFileSync(tokensPath, 'utf8'), {
      loader: 'ts',
      format: 'esm',
    })
    return await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
  } catch (err) {
    if (err?.code !== 'ERR_MODULE_NOT_FOUND') throw err
    // 没有 esbuild（例如未装 node_modules）：交给 Node 原生 TS 支持
    return await import(pathToFileURL(tokensPath).href)
  }
}

const { lightTokens, darkTokens, tokensToCSSVars } = await loadTokens()

/** 把一个变量表渲染成 CSS 规则块 */
function rule(selector, vars, indent = '  ') {
  const body = Object.entries(vars)
    .map(([key, val]) => `${indent}${key}: ${val};`)
    .join('\n')
  return `${selector} {\n${body}\n}`
}

/** media 内层需要多缩进一级 */
function ruleNested(selector, vars, indent = '    ') {
  const body = Object.entries(vars)
    .map(([key, val]) => `${indent}${key}: ${val};`)
    .join('\n')
  return `${indent}${selector} {\n${body}\n${indent}}`
}

const lightVars = tokensToCSSVars(lightTokens)
const darkVars = tokensToCSSVars(darkTokens)

const header = `/*
 * 自动生成 — 请勿手改。
 * 源：src/themes/tokens.ts
 * 重新生成：npm run gen:theme
 *
 * 主题切换通过 <html data-theme="light|dark"> 驱动，由 index.html 内联脚本在
 * 首帧前写入，App.vue 只负责后续同步，不再逐个注入 CSS 变量。
 */`

const css = [
  header,
  '',
  '/* 1. 默认亮色（无 data-theme 时） */',
  rule(':root', lightVars),
  '',
  '/* 2. 系统暗色兜底：内联脚本未执行（JS 被禁用/报错）时也不闪白 */',
  '@media (prefers-color-scheme: dark) {',
  ruleNested(':root:not([data-theme])', darkVars),
  '}',
  '',
  '/* 3. 用户显式选亮色：压过系统暗色 */',
  rule(":root[data-theme='light']", lightVars),
  '',
  '/* 4. 用户显式选暗色 */',
  rule(":root[data-theme='dark']", darkVars),
  '',
].join('\n')

if (isCheck) {
  let current = ''
  try {
    current = readFileSync(outPath, 'utf8')
  } catch {
    console.error('✗ tokens.generated.css 不存在，请运行：npm run gen:theme')
    process.exit(1)
  }
  if (current !== css) {
    console.error('✗ tokens.generated.css 与 tokens.ts 不同步，请运行：npm run gen:theme')
    process.exit(1)
  }
  console.log(`✓ tokens.generated.css 已是最新（亮/暗各 ${Object.keys(lightVars).length} 个变量）`)
  process.exit(0)
}

writeFileSync(outPath, css, 'utf8')
console.log(
  `✓ 已生成 src/themes/tokens.generated.css` +
    `（亮色 ${Object.keys(lightVars).length} 个变量，暗色 ${Object.keys(darkVars).length} 个）`,
)
