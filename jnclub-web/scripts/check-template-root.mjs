#!/usr/bin/env node
/**
 * check-template-root.mjs — 防止「模板根层级写注释」把单根组件变成 Fragment
 *
 * 为什么需要它：
 *   2026-10-16 的第十批改动中，为解释 `active-module` 只是占位，在
 *   OverviewLayout.vue / RecycleLayout.vue 的 <template> 根层级加了两段 HTML 注释。
 *   Vue 3 会把模板里的 HTML 注释编译成真实节点（_createCommentVNode），
 *   组件因此从「单根」变成「多根 Fragment」（_createElementBlock(_Fragment, …)）；
 *   而 App.vue 用 <Transition name="page-fade" mode="out-in"> 包裹路由组件，
 *   <Transition> 只能作用于单根 —— 多根会让 out-in 状态机卡死：
 *   旧组件已卸载、新组件永不挂载，整页白屏（#app 塌成 688 字符空壳），
 *   且无任何 console 报错、无 PAGEERROR，纯静默失效，排查成本极高。
 *
 * 判据（为什么是「恰好 1 个元素 + 存在注释」）：
 *   只要求「根层级无注释」会误报一批既有组件 —— 它们本来就是刻意多根：
 *     BatchActionModals.vue（2 弹窗）、AppHeader.vue（头部 + 用户信息弹窗）、
 *     SideNav.vue（侧栏 + 3 个抽屉）、JMagnet.vue（两个条件分支）、
 *     RouteLoadingBar.vue（纯注释、无渲染）。
 *   这些组件元素数 ≥2 或为 0，多根是设计使然，不构成「意外 Fragment」。
 *   真正危险的形态是「本意单根，却被一段注释撑成多根」= 元素数恰好 1 且有注释。
 *
 * 用法：
 *   node scripts/check-template-root.mjs          校验（dev/build 由 predev/prebuild 自动调用）
 *   发现 error 时以退出码 1 中断构建，避免白屏上线。
 *
 * 修法：把说明写进 <script setup> 的 JSDoc，或移到根元素**内部**。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@vue/compiler-sfc'

const here = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(here, '..')
const srcRoot = resolve(projectRoot, 'src')

const ELEMENT = 1
const COMMENT = 3

/** 递归收集 src 下所有 .vue 文件 */
function collectVueFiles(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) collectVueFiles(full, out)
    else if (full.endsWith('.vue')) out.push(full)
  }
  return out
}

const errors = []
const notes = []

for (const file of collectVueFiles(srcRoot)) {
  const rel = relative(projectRoot, file)
  // 解析失败一律跳过：本脚本是「防白屏的护栏」，不该比它检查的代码更容易
  // 让构建失败。语法错误的 SFC 由 vue-tsc / vite 去报，这里只管根层级形态。
  let descriptor, parseErrors
  try {
    const source = readFileSync(file, 'utf8')
    ;({ descriptor, errors: parseErrors } = parse(source, { filename: file }))
  } catch {
    continue
  }
  if (parseErrors.length || !descriptor.template?.ast) continue

  const roots = descriptor.template.ast.children || []
  const elements = roots.filter((n) => n.type === ELEMENT)
  const comments = roots.filter((n) => n.type === COMMENT)
  if (comments.length === 0) continue

  const lines = comments.map((c) => c.loc.start.line).join(', ')

  if (elements.length === 1) {
    errors.push(
      `${rel}：模板根层级有 ${comments.length} 处注释（第 ${lines} 行）` +
        `，根元素只有 1 个 —— 这会让组件从单根变成多根 Fragment。\n` +
        `    若该组件挂在路由 <Transition mode="out-in"> 下，过渡状态机会卡死并整页白屏。\n` +
        `    修法：说明移到 <script setup> 的 JSDoc，或写进根元素内部。`,
    )
  } else {
    // 元素数 ≥2 或为 0：多根是刻意的，仅提示确认，不阻断构建
    notes.push(`${rel}：根层级 ${elements.length} 个元素 + ${comments.length} 处注释（第 ${lines} 行）`)
  }
}

if (notes.length) {
  console.log(`[check-template-root] 以下组件为刻意多根，未阻断（仅供参考）：`)
  for (const n of notes) console.log(`  · ${n}`)
}

if (errors.length) {
  console.error(`\n[check-template-root] 发现 ${errors.length} 处会破坏单根的模板注释：\n`)
  for (const e of errors) console.error(`  ✗ ${e}\n`)
  console.error(
    '背景：Vue 3 把模板 HTML 注释编译成真实节点，注释与唯一根元素并列会强制产生 Fragment；\n' +
      '      <Transition mode="out-in"> 只能作用于单根，多根会让它卡死（整页白屏、无报错）。\n',
  )
  process.exit(1)
}

console.log('[check-template-root] 通过：无「单根组件被注释撑成 Fragment」的情况。')
