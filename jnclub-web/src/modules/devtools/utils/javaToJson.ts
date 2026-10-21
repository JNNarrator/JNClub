/* ─── Java → 示例 JSON 生成 ───
 * 依赖 javaLightParser.ts 的 ParseResult 与 naming.ts 的工具函数。
 * 含循环引用检测、类型→示例值映射、注解 key 读取。
 */

import { parseJavaSource, type ParsedClass, type ParsedField } from './javaLightParser'
import {
  type ExampleStyle, type DateFormat, type OutputKeyStyle,
} from './naming'

export interface JavaToJsonOptions {
  exampleStyle: ExampleStyle
  dateFormat: DateFormat
  outputKeyStyle: OutputKeyStyle
  indentSize: number
  indentChar?: string
}

const MAX_DEPTH = 6

/* ─── 类型 → 示例值 ─── */
function exampleValue(
  typeName: string,
  style: ExampleStyle,
  dateFmt: DateFormat,
  depth: number,
  ancestorChain: string[],
  classIndex: Map<string, ParsedClass>,
  root: ParsedClass | null,
): unknown {
  if (depth > MAX_DEPTH) return null

  const t = typeName.replace(/\[\s*\]/g, '').trim()
  const baseType = t
  const isArray = typeName.includes('[]') || typeName.includes('...')
  const isList = typeName.startsWith('List') || typeName.startsWith('Set') || typeName.startsWith('Collection')
  const isMap = typeName.startsWith('Map')

  // Map
  if (isMap) {
    const innerMatch = typeName.match(/Map\s*<\s*(\S+)\s*,\s*(\S+)\s*>/)
    if (innerMatch) {
      const valType = innerMatch[2]
      const val = exampleValue(valType, style, dateFmt, depth + 1, ancestorChain, classIndex, root)
      return style === 'empty' ? {} : { key: val }
    }
    return style === 'empty' ? {} : { key: null }
  }

  // List / Set / Array
  if (isArray || isList) {
    const innerMatch = isList ? typeName.match(/<(.*?)>/) : null
    let elemType = 'Object'
    if (innerMatch) {
      // Extract outer element type (split by comma respecting nesting depth)
      const inner = innerMatch[1]
      let depth2 = 0
      let splitAt = inner.length
      for (let i = 0; i < inner.length; i++) {
        if (inner[i] === '<') depth2++
        else if (inner[i] === '>') depth2--
        else if (inner[i] === ',' && depth2 === 0) { splitAt = i; break }
      }
      elemType = inner.slice(0, splitAt).trim()
    }
    if (style === 'empty') return []
    const elem = exampleValue(elemType, style, dateFmt, depth + 1, ancestorChain, classIndex, root)
    return [elem]
  }

  // boolean/Boolean
  if (baseType === 'boolean' || baseType === 'Boolean') {
    if (style === 'empty') return false
    return true
  }

  // numeric types
  const numericTypes = ['int', 'Integer', 'long', 'Long', 'short', 'Short', 'byte', 'Byte',
    'float', 'Float', 'double', 'Double', 'BigDecimal', 'BigInteger', 'Number']
  if (numericTypes.some(nt => baseType === nt)) {
    if (style === 'empty') return 0
    if (style === 'typename') return baseType
    // placeholder
    const isDecimal = (baseType === 'float' || baseType === 'Float' || baseType === 'double' || baseType === 'Double' || baseType === 'BigDecimal' || baseType === 'Number')
    if (isDecimal) return 0.0
    return 0
  }

  // char
  if (baseType === 'char' || baseType === 'Character') {
    if (style === 'empty') return ''
    return 'a'
  }

  // String
  if (baseType === 'String') {
    if (style === 'empty') return ''
    if (style === 'typename') return 'string'
    return 'string'
  }

  // Date/time
  const dateTypes = ['Date', 'LocalDate', 'LocalDateTime', 'LocalTime', 'Instant', 'ZonedDateTime', 'OffsetDateTime']
  if (dateTypes.some(dt => baseType === dt || baseType.endsWith(dt))) {
    if (style === 'empty') return null
    if (dateFmt === 'epoch') return 1700000000
    return '2024-01-15T10:30:00'
  }

  // Enum or Object (custom class)
  if (classIndex && classIndex.has(baseType)) {
    const cls = classIndex.get(baseType)!
    if (cls.isEnum) {
      if (style === 'empty') return ''
      return cls.enumValues[0] || baseType
    }
    // Check circular reference
    if (ancestorChain.includes(baseType)) {
      // Circular detected
      return { '$ref': baseType }
    }
    return generateClassJson(cls, style, dateFmt, depth + 1, ancestorChain, classIndex, root)
  }

  // Object / unknown
  if (baseType === 'Object' || baseType === 'Serializable' || baseType === 'Comparable') {
    return null
  }

  // Default: try as object if we have a class for it
  if (/^[A-Z]/.test(baseType) && classIndex?.has(baseType)) {
    const cls = classIndex.get(baseType)!
    if (depth > MAX_DEPTH || ancestorChain.includes(baseType)) return { '$ref': baseType }
    return generateClassJson(cls, style, dateFmt, depth + 1, ancestorChain, classIndex, root)
  }

  return null
}

function readAnnotationKey(field: ParsedField): string | null {
  for (const ann of field.annotations) {
    // @JsonProperty("value")
    const jp = ann.match(/@JsonProperty\s*\(\s*"([^"]+)"/)
    if (jp) return jp[1]
    // @SerializedName("value")
    const gs = ann.match(/@SerializedName\s*\(\s*"([^"]+)"/)
    if (gs) return gs[1]
    // @JSONField(name = "value")
    const jf = ann.match(/@JSONField\s*\(\s*(?:\w+\s*=\s*)?"([^"]+)"/)
    if (jf) return jf[1]
    // @JSONField("value") - shorthand
    const jf2 = ann.match(/@JSONField\s*\(\s*"([^"]+)"/)
    if (jf2) return jf2[1]
  }
  return null
}

function generateClassJson(
  cls: ParsedClass,
  style: ExampleStyle,
  dateFmt: DateFormat,
  depth: number,
  ancestorChain: string[],
  classIndex: Map<string, ParsedClass>,
  root: ParsedClass | null,
): Record<string, unknown> {
  const result: Record<string, unknown> = {}

  for (const field of cls.fields) {
    // Skip static/final fields conventionally
    if (field.type === 'static') continue

    // Determine JSON key
    const annKey = readAnnotationKey(field)
    let jsonKey = annKey || field.name

    // Get example value
    const val = exampleValue(field.type, style, dateFmt, depth + 1, [...ancestorChain, cls.name], classIndex, root)
    result[jsonKey] = val
  }

  return result
}

export type JavaToJsonResult = {
  ok: true
  json: string
  circularRefs: string[]
} | { ok: false; message: string; line?: number }

export function javaToJson(source: string, opts: JavaToJsonOptions): JavaToJsonResult {
  const parseResult = parseJavaSource(source)

  if (!parseResult.ok) {
    return { ok: false, message: parseResult.message, line: parseResult.line }
  }

  if (parseResult.classes.length === 0) {
    return { ok: false, message: '未找到任何类或枚举定义。轻量解析器要求类以 class/enum 关键字声明，且格式基本规范。' }
  }

  // Build class index
  const classIndex = new Map<string, ParsedClass>()
  for (const cls of parseResult.classes) {
    classIndex.set(cls.name, cls)
  }

  // Find top-level non-enum class
  const topClass = parseResult.classes.find(c => !c.isEnum)
  if (!topClass) {
    return { ok: false, message: '输入只包含枚举、接口或空文件，未找到常规类定义。轻量解析器不支持接口与 record 生成示例 JSON。' }
  }

  const circularRefs: string[] = []

  // Watch for circular references
  const watchAncestors: string[] = [topClass.name]

  const genClass = generateClassJson(topClass, opts.exampleStyle, opts.dateFormat, 0, watchAncestors, classIndex, topClass)

  // Check for circular refs in output
  function findRefs(obj: unknown, path: string): void {
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      const r = obj as Record<string, unknown>
      if (r['$ref']) {
        circularRefs.push(`${path}: ${r['$ref']}`)
      }
      for (const [k, v] of Object.entries(r)) {
        if (k !== '$ref') findRefs(v, `${path}.${k}`)
      }
    } else if (Array.isArray(obj)) {
      obj.forEach((item, i) => findRefs(item, `${path}[${i}]`))
    }
  }
  findRefs(genClass, topClass.name)

  const jsonText = JSON.stringify(genClass, null, opts.indentSize)

  let prefix = ''
  if (circularRefs.length > 0) {
    prefix = `// 检测到循环引用，已截断：${circularRefs.join('；')}\n`
  }
  if (parseResult.classes.length > 1 && topClass.name) {
    const otherClasses = parseResult.classes.filter(c => c.name !== topClass.name)
    prefix += `// 提示：输入包含 ${otherClasses.length} 个其他类（${otherClasses.map(c => c.name).join(', ')}），仅以 ${topClass.name} 为主类生成示例\n`
  }

  return {
    ok: true,
    json: prefix + jsonText,
    circularRefs,
  }
}
