/* ─── 格式互转中枢 ───
 * 星型架构：源文本 --parse--> JS 对象 --stringify--> 目标文本
 * 共 5 个 parser + 5 个 serializer + 1 个 convert 编排函数（11 个纯函数）。
 * 新增第 6 种格式只需补 1 parser + 1 serializer 并注册到表，不改动其它分支。
 *
 * XML 映射约定（fast-xml-parser）：
 *   属性 → 键名前缀 '@_'（attributeNamePrefix:'@_'）
 *   文本节点 → '#text'
 *   重复同名标签自动成数组。
 * CSV 异类策略：中间表示约定为「对象数组」（首行表头）；嵌套对象默认拒绝，
 *   开启 autoFlatten 才扁平一层（嵌套键 a.b），明确提示有损。
 * 危险防护：YAML 禁用 !!js/function 等危险 tag（CORE_SCHEMA），禁止解析原型污染向量。
 */
import * as yaml from 'js-yaml'
import * as toml from 'smol-toml'
import { XMLParser, XMLBuilder } from 'fast-xml-parser'
import Papa from 'papaparse'

export type DataFormat = 'json' | 'yaml' | 'toml' | 'xml' | 'csv' | 'properties'

export type ConvertResult =
  | { ok: true; text: string }
  | { ok: false; stage: 'parse' | 'stringify'; fmt: DataFormat; message: string; line?: number; col?: number }

export interface FormatMeta {
  displayName: string
  ext: string
  sample: string
}

const SAMPLE_JSON = `{
  "name": "jnclub",
  "tools": 5,
  "tags": ["json", "yaml", "toml", "xml", "csv"],
  "meta": { "author": "jiangnan", "open": true }
}`
const SAMPLE_YAML = `name: jnclub
tools: 5
tags:
  - json
  - yaml
  - toml
  - xml
  - csv
meta:
  author: jiangnan
  open: true`
const SAMPLE_TOML = `name = "jnclub"
tools = 5
tags = ["json", "yaml", "toml", "xml", "csv"]
[meta]
author = "jiangnan"
open = true`
const SAMPLE_XML = `<?xml version="1.0"?>
<app authors="2">
  <name>jnclub</name>
  <tools>5</tools>
  <tags>
    <tag>json</tag>
    <tag>yaml</tag>
    <tag>toml</tag>
    <tag>xml</tag>
    <tag>csv</tag>
  </tags>
</app>`
const SAMPLE_CSV = `name,tools,open
jnclub,5,true
papa,3,false
flux,1,true`
const SAMPLE_PROPERTIES = `# jnclub app config
app.name = jnclub
app.tools = 5
app.tags[0] = json
app.tags[1] = yaml
app.tags[2] = toml
author = jiangnan
open = true`

export const FORMAT_META: Record<DataFormat, FormatMeta> = {
  json: { displayName: 'JSON', ext: 'json', sample: SAMPLE_JSON },
  yaml: { displayName: 'YAML', ext: 'yml', sample: SAMPLE_YAML },
  toml: { displayName: 'TOML', ext: 'toml', sample: SAMPLE_TOML },
  xml: { displayName: 'XML', ext: 'xml', sample: SAMPLE_XML },
  csv: { displayName: 'CSV', ext: 'csv', sample: SAMPLE_CSV },
  properties: { displayName: 'Properties', ext: 'properties', sample: SAMPLE_PROPERTIES },
}

function asErr(stage: 'parse' | 'stringify', fmt: DataFormat, e: unknown): ConvertResult {
  const m = e instanceof Error ? e.message : String(e)
  let line: number | undefined, col: number | undefined
  const lm = m.match(/line\s+(\d+)/i); if (lm) line = +lm[1]
  const cm = m.match(/col(?:umn)?\s+(\d+)/i); if (cm) col = +cm[1]
  const pm = m.match(/position\s+(\d+)/i)
  if (pm && line == null) line = +pm[1]
  return { ok: false, stage, fmt, message: m, line, col }
}

// ── JSON ──
function parseJSON(text: string): unknown { return JSON.parse(text) }
function stringifyJSON(obj: unknown): string { return JSON.stringify(obj, null, 2) }

// ── YAML ── CORE_SCHEMA 禁用 !!js/function 等，防原型污染 / 代码执行
function parseYAML(text: string): unknown {
  return yaml.load(text, { schema: yaml.CORE_SCHEMA })
}
function stringifyYAML(obj: unknown): string {
  return yaml.dump(obj, { indent: 2, lineWidth: -1, noRefs: true, schema: yaml.CORE_SCHEMA })
}

// ── TOML ── smol-toml；顶层须为表、数组元素类型需一致、不支持 null
function parseTOML(text: string): unknown { return toml.parse(text) }
function stringifyTOML(obj: unknown): string { return toml.stringify(obj as Record<string, unknown>) }

// ── XML ── fast-xml-parser，约定 @_ 属性前缀 / #text 文本节点
function parseXML(text: string): unknown {
  const p = new XMLParser({
    attributeNamePrefix: '@_',
    textNodeName: '#text',
    parseAttributeValue: false,
    parseTagValue: false,
    trimValues: true,
    ignoreAttributes: false,
    ignorePiTags: true,
  })
  return p.parse(text)
}
function stringifyXML(obj: unknown): string {
  const b = new XMLBuilder({
    attributeNamePrefix: '@_',
    textNodeName: '#text',
    ignoreAttributes: false,
    format: true,
    indentBy: '  ',
  })
  return `<?xml version="1.0" encoding="UTF-8"?>\n${b.build(obj)}`
}

// ── CSV ── papaparse；中间表示为对象数组（首行表头）
function parseCSV(text: string): unknown {
  const r = Papa.parse(text, { header: true, skipEmptyLines: true, dynamicTyping: false })
  const rows = r.data as Record<string, unknown>[]
  if (!rows.length) throw new Error('CSV 为空或无有效数据行')
  if (rows.length === 1 && Object.keys(rows[0]).some(k => k === 'NaN' || k === '')) {
    const arr = Papa.parse(text, { skipEmptyLines: true, dynamicTyping: false }).data as unknown[][]
    return arr
  }
  return rows
}
function stringifyCSV(obj: unknown, autoFlatten = false): string {
  if (obj == null) throw new Error('无法序列化空值为 CSV')
  if (Array.isArray(obj)) {
    const needFlatten = obj.some(it => it && typeof it === 'object' && !Array.isArray(it) &&
      Object.values(it).some(v => v != null && typeof v === 'object'))
    if (needFlatten && !autoFlatten) {
      throw new Error('CSV 仅支持扁平对象数组（表格结构）；当前为嵌套对象。开启「自动扁平化」后再转，将有损')
    }
    const rows = autoFlatten ? obj.map(it => flattenOne(it as Record<string, unknown>)) : obj
    return Papa.unparse(rows, { quotes: true })
  }
  if (obj && typeof obj === 'object') {
    throw new Error('CSV 仅支持对象数组（表格）结构；当前为单个/嵌套对象，请先调整为数组')
  }
  throw new Error('CSV 仅支持对象数组；当前为标量或非对象数组')
}
/* 扁平一层：嵌套键用点拼，数组转分号字符串 */
function flattenOne(o: Record<string, unknown>, prefix = '', acc: Record<string, unknown> = {}): Record<string, unknown> {
  for (const [k, v] of Object.entries(o)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v != null && typeof v === 'object' && !Array.isArray(v)) {
      flattenOne(v as Record<string, unknown>, key, acc)
    } else if (Array.isArray(v)) {
      acc[key] = v.map(x => (x != null && typeof x === 'object') ? JSON.stringify(x) : String(x)).join('; ')
    } else {
      acc[key] = v
    }
  }
  return acc
}

// ── Properties ── 手写解析器（零依赖）；中间表示为扁平对象（key 含点号路径，不拆嵌套）
//   支持 # / ! 注释、行尾 \ 续行、= / : / 空白分隔、\n \t \r \f \uXXXX 转义。
//   parse 丢弃注释；stringify 把嵌套/数组扁平为 a.b / a[0]，注释不还原（固有有损）。
function parseProperties(text: string): unknown {
  const result: Record<string, string> = {}
  const lines = text.split(/\r?\n/)
  let i = 0
  while (i < lines.length) {
    let raw = lines[i]
    // 续行：行尾单个反斜杠；双反斜杠是字面反斜杠不算续行
    let safety = 0
    while (raw.endsWith('\\') && !raw.endsWith('\\\\') && i + 1 < lines.length && safety++ < 1000) {
      raw = raw.slice(0, -1) + lines[++i].replace(/^[ \t]+/, '')
    }
    i++
    const trimmed = raw.trim()
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue
    // 分隔区：跳过空白，吞掉一个可选的 = 或 :，再跳空白
    let kj = 0
    while (kj < trimmed.length) {
      const c = trimmed[kj]
      if (c === '=' || c === ':' || c === ' ' || c === '\t' || c === '\f') break
      if (c === '\\' && kj + 1 < trimmed.length) { kj += 2; continue }
      kj++
    }
    const key = unescapeProp(trimmed.slice(0, kj).trim())
    let vj = kj
    while (vj < trimmed.length && (trimmed[vj] === ' ' || trimmed[vj] === '\t' || trimmed[vj] === '\f')) vj++
    if (trimmed[vj] === '=' || trimmed[vj] === ':') vj++
    while (vj < trimmed.length && (trimmed[vj] === ' ' || trimmed[vj] === '\t' || trimmed[vj] === '\f')) vj++
    const value = unescapeProp(trimmed.slice(vj).trim())
    if (!key) continue
    result[key] = value
  }
  return result
}
function stringifyProperties(obj: unknown): string {
  if (obj == null) throw new Error('无法序列化空值为 properties')
  if (Array.isArray(obj)) throw new Error('properties 仅支持键值对对象；数组请先在 JSON 视图转成以数字下标为键的对象')
  if (typeof obj !== 'object') throw new Error('properties 仅支持键值对对象')
  const flat: Record<string, string> = {}
  flattenProps(obj as Record<string, unknown>, '', flat)
  if (!Object.keys(flat).length) throw new Error('properties 序列化结果为空（无可写键）')
  return Object.entries(flat)
    .map(([k, v]) => `${escapePropKey(k)} = ${escapePropValue(v)}`)
    .join('\n')
}
function flattenProps(o: Record<string, unknown>, prefix: string, acc: Record<string, string>): void {
  for (const [k, v] of Object.entries(o)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v == null) acc[key] = ''
    else if (Array.isArray(v)) v.forEach((el, idx) => {
      acc[`${key}[${idx}]`] = el != null && typeof el === 'object' ? JSON.stringify(el) : String(el)
    })
    else if (typeof v === 'object') flattenProps(v as Record<string, unknown>, key, acc)
    else acc[key] = String(v)
  }
}
const PROP_ESC: Record<string, string> = { n: '\n', t: '\t', r: '\r', f: '\f', '\\': '\\', '=': '=', ':': ':', '#': '#', '!': '!', ' ': ' ' }
function unescapeProp(s: string): string {
  return s.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, g: string) => {
    if (g[0] === 'u') return String.fromCharCode(parseInt(g.slice(1), 16))
    return PROP_ESC[g] ?? g
  })
}
function escapePropKey(k: string): string {
  return k.replace(/[\\=:#! \t]/g, c => '\\' + c)
}
function escapePropValue(v: string): string {
  const m: Record<string, string> = { '\\': '\\\\', '\n': '\\n', '\r': '\\r', '\t': '\\t', '\f': '\\f', '#': '\\#', '!': '\\!' }
  return v.replace(/[\\\n\r\t\f#!]/g, c => m[c])
}

type Parser = (text: string) => unknown
type Serializer = (obj: unknown, opts: { autoFlatten: boolean }) => string
const PARSERS: Record<DataFormat, Parser> = { json: parseJSON, yaml: parseYAML, toml: parseTOML, xml: parseXML, csv: parseCSV, properties: parseProperties }
const SERIALIZERS: Record<DataFormat, Serializer> = {
  json: (o) => stringifyJSON(o), yaml: (o) => stringifyYAML(o), toml: (o) => stringifyTOML(o),
  xml: (o) => stringifyXML(o), csv: (o, opts) => stringifyCSV(o, opts.autoFlatten),
  properties: (o) => stringifyProperties(o),
}

/* 编排：from===to 直接格式化（parse→stringify 同格式重排）；否则 parse 后 stringify */
export function convert(text: string, from: DataFormat, to: DataFormat, opts: { autoFlatten?: boolean } = {}): ConvertResult {
  const autoFlatten = opts.autoFlatten ?? false
  if (from === to) {
    try { const obj = PARSERS[from](text); return { ok: true, text: SERIALIZERS[to](obj, { autoFlatten }) } }
    catch (e) { return asErr('parse', from, e) }
  }
  let obj: unknown
  try { obj = PARSERS[from](text) } catch (e) { return asErr('parse', from, e) }
  try { return { ok: true, text: SERIALIZERS[to](obj, { autoFlatten }) } }
  catch (e) { return asErr('stringify', to, e) }
}

/* 可能「有损」转换的提示，用于 UI 角标非阻断说明 */
export function lossyHint(from: DataFormat, to: DataFormat): string | undefined {
  // 转 string 规避 TS 在否定比较后对字面量联合的窄化误判
  const f = String(from), t = String(to)
  if (to === 'properties') return 'properties 注释与续行不还原；嵌套/数组会扁平为 a.b / a[0]'
  if (to === 'csv' && from !== 'csv') return 'CSV 仅保留扁平表格；嵌套结构需扁平化'
  if (from === 'xml' && to !== 'xml') return 'XML 属性以 @_ 前缀、文本以 #text 键保留'
  if (to === 'toml' && from !== 'toml') return 'TOML 不支持 null；异构/嵌套数组可能报错'
  if (f === 'properties' && t !== 'properties') return 'properties 仅扁平 KV；嵌套经点路径还原为字符串'
  return undefined
}
