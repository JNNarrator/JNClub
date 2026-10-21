/* ─── 命名规约 / 关键字集 / 类型推断 / 序列化框架注解映射 ───
 * jsonToJava 与 javaToJson 共享的命名工具。
 * 四框架注解映射 + Java 关键字集 + 字段/类名安全化。
 */

export const JAVA_KEYWORDS = new Set([
  'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char',
  'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum',
  'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements',
  'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package',
  'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp',
  'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient',
  'try', 'void', 'volatile', 'while', 'true', 'false', 'null',
])

export function isValidJavaIdentifier(s: string): boolean {
  if (!s || s.length === 0) return false
  if (!/^[a-zA-Z_$][a-zA-Z0-9_$]*$/.test(s)) return false
  return !JAVA_KEYWORDS.has(s)
}

export function safeJavaIdentifier(s: string): string {
  let r = s.replace(/[^a-zA-Z0-9_$]/g, '_')
  if (!/^[a-zA-Z_$]/.test(r)) r = '_' + r
  if (JAVA_KEYWORDS.has(r)) r += '_'
  return r
}

export function toPascalCase(s: string): string {
  // 若已是 camelCase（小写+大写相邻），保留内部 case，只大写首字母
  if (/[a-z][A-Z]/.test(s)) {
    return s.charAt(0).toUpperCase() + s.slice(1)
  }
  return s.split(/[^a-zA-Z0-9]/)
    .filter(Boolean)
    .map(p => p.charAt(0).toUpperCase() + p.slice(1).toLowerCase())
    .join('')
}

export function toCamelCase(s: string): string {
  const p = toPascalCase(s)
  if (!p) return '_empty'
  return p.charAt(0).toLowerCase() + p.slice(1)
}

export function toSnakeCase(s: string): string {
  const r = s.replace(/([A-Z])/g, '_$1').toLowerCase()
  return r.startsWith('_') ? r.slice(1) : r
}

/* ─── 策略枚举 ─── */
export type NumberStrategy = 'auto' | 'bigdecimal'
export type NullStrategy = 'object' | 'string'
export type FieldNaming = 'camelCase' | 'keep'
export type SerializationFramework = 'jackson' | 'gson' | 'fastjson' | 'none'
export type NestedClassMode = 'inner' | 'separate'
export type ExampleStyle = 'empty' | 'placeholder' | 'typename'
export type DateFormat = 'iso' | 'epoch'
export type OutputKeyStyle = 'keep' | 'camelCase' | 'snake_case'

/* ─── 选项清单 ─── */
export interface JsonJavabeanOptions {
  framework: SerializationFramework
  indentSize: number
  numberStrategy: NumberStrategy
  nullStrategy: NullStrategy
  fieldNaming: FieldNaming
  nestedMode: NestedClassMode
  rootClassName: string
  packageName: string
  lombok: boolean
  lombokBuilder: boolean
  exampleStyle: ExampleStyle
  dateFormat: DateFormat
  outputKeyStyle: OutputKeyStyle
  autoConvert: boolean
}

export const DEFAULT_OPTIONS: JsonJavabeanOptions = {
  framework: 'jackson',
  indentSize: 4,
  numberStrategy: 'auto',
  nullStrategy: 'object',
  fieldNaming: 'camelCase',
  nestedMode: 'inner',
  rootClassName: 'Root',
  packageName: '',
  lombok: true,
  lombokBuilder: false,
  exampleStyle: 'placeholder',
  dateFormat: 'iso',
  outputKeyStyle: 'keep',
  autoConvert: false,
}

/* ─── 类型推断 ─── */
export interface FieldDef {
  fieldName: string
  originalKey: string
  javaType: string
  isObject: boolean
  nestedClassName?: string
  isArray: boolean
}

export function inferJavaType(value: unknown, ns: NumberStrategy, nullStrategy: NullStrategy): string {
  if (value === null || value === undefined) return nullStrategy === 'object' ? 'Object' : 'String'
  const t = typeof value
  if (t === 'string') return 'String'
  if (t === 'boolean') return 'Boolean'
  if (t === 'number') {
    if (ns === 'bigdecimal') return Number.isInteger(value) ? 'Long' : 'BigDecimal'
    if (Number.isInteger(value)) {
      const n = value as number
      if (n >= -2147483648 && n <= 2147483647) return 'Integer'
      return 'Long'
    }
    return 'Double'
  }
  if (t === 'object') {
    if (Array.isArray(value)) {
      if (value.length === 0) return 'List<Object>'
      const nonNull = value.find(v => v !== null && v !== undefined)
      if (!nonNull) return 'List<Object>'
      return `List<${inferJavaType(nonNull, ns, nullStrategy)}>`
    }
    return 'Object'
  }
  return 'Object'
}

export function mergeTypes(types: string[]): string {
  const u = [...new Set(types)]
  if (u.length === 1) return u[0]
  const s = new Set(u)
  if (s.has('Object')) return 'Object'
  const hasNum = s.has('Integer') || s.has('Long') || s.has('Double') || s.has('BigDecimal')
  const hasStr = s.has('String')
  const hasBool = s.has('Boolean')
  if (hasNum && (hasStr || hasBool)) return 'Object'
  if (hasNum) {
    if (s.has('Double') || s.has('BigDecimal')) return 'Double'
    if (s.has('Long')) return 'Long'
    return 'Integer'
  }
  if (hasStr && hasBool) return 'Object'
  if (hasStr) return 'String'
  if (hasBool) return 'Boolean'
  return 'Object'
}

/* ─── 注解映射 ─── */
export function frameworkPackageParts(f: SerializationFramework): { annotation: string; import_: string; param: string } {
  switch (f) {
    case 'jackson': return { annotation: '@JsonProperty', import_: 'com.fasterxml.jackson.annotation.JsonProperty', param: 'value' }
    case 'gson': return { annotation: '@SerializedName', import_: 'com.google.gson.annotations.SerializedName', param: 'value' }
    case 'fastjson': return { annotation: '@JSONField', import_: 'com.alibaba.fastjson.annotation.JSONField', param: 'name' }
    case 'none': return { annotation: '', import_: '', param: '' }
  }
}

export function frameworkImports(f: SerializationFramework): string[] {
  if (f === 'none') return []
  const p = frameworkPackageParts(f)
  return [`import ${p.import_};;`]
}

export function fieldAnnotation(originalKey: string, fieldName: string, f: SerializationFramework): string {
  if (originalKey === fieldName && !JAVA_KEYWORDS.has(originalKey) && isValidJavaIdentifier(originalKey)) return ''
  if (f === 'none') return `    // 原 key: "${originalKey}"\n`
  const p = frameworkPackageParts(f)
  return `    @${p.annotation}("${escapeJavaStr(originalKey)}")\n`
}

export function escapeJavaStr(s: string): string {
  return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')
}

export function toJavaFieldName(key: string, naming: FieldNaming): string {
  if (naming === 'keep') return isValidJavaIdentifier(key) ? key : safeJavaIdentifier(key)
  return toCamelCase(key)
}

export function toClassName(key: string): string {
  return safeJavaIdentifier(toPascalCase(key))
}

/* ─── 输出 key 风格 ─── */
export function applyOutputKeyStyle(key: string, style: OutputKeyStyle): string {
  if (style === 'keep') return key
  if (style === 'camelCase') return toCamelCase(key)
  return toSnakeCase(key)
}

/* ─── 轻量 Java 格式化 ─── */
export function lightFormatJava(code: string, indentSize: number): string {
  const indent = ' '.repeat(indentSize)
  let depth = 0
  const lines = code.split('\n')
  const result: string[] = []
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i]
    const trimmed = l.trim()
    const isClose = trimmed.startsWith('}')
    if (isClose) depth = Math.max(0, depth - 1)
    result.push(indent.repeat(depth) + trimmed)
    if (trimmed.endsWith('{')) depth++
  }
  return result.join('\n')
}
