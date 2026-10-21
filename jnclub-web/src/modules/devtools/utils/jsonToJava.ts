/* ─── JSON → JavaBean 代码生成 ───
 * 类型推断 + 四框架注解 + Lombok/手写 getter + 内部类/独立类。
 * 依赖 naming.ts 的命名规约与类型推断。
 */

import {
  type SerializationFramework, type NumberStrategy, type NullStrategy,
  type FieldNaming, type NestedClassMode,
  safeJavaIdentifier,
  inferJavaType, mergeTypes, frameworkImports, fieldAnnotation,
  toJavaFieldName, toClassName,
} from './naming'

export interface GenOptions {
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
}

export type GenResult = {
  ok: true; code: string; classes: string[]
} | { ok: false; message: string }

interface MemberField {
  name: string
  originalKey: string
  type: string
  rawValue: unknown
}

interface ClassDef {
  className: string
  fields: MemberField[]
  nested: ClassDef[]
  isEnum?: boolean
  enumValues?: string[]
}

function indent(n: number, s: string): string {
  const i = ' '.repeat(n)
  return s.split('\n').map(l => l ? i + l : '').join('\n')
}


function collectLombokImports(builder: boolean): string[] {
  const imps = ['import lombok.Data;', 'import lombok.NoArgsConstructor;', 'import lombok.AllArgsConstructor;']
  if (builder) imps.push('import lombok.Builder;')
  return imps
}

function generateEqualsHashCode(fields: MemberField[], indentSize: number): string {
  const i = ' '.repeat(indentSize)
  const i2 = ' '.repeat(indentSize * 2)
  const fieldRefs = fields.map(f => f.name).join(', ')
  return (
    `\n${i}@Override\n` +
    `${i}public boolean equals(Object o) {\n` +
    `${i2}if (this == o) return true;\n` +
    `${i2}if (o == null || getClass() != o.getClass()) return false;\n` +
    `${i2}${safeJavaIdentifier('')}that = (${'${className}'}) o; // placeholder\n` +
    `${i2}return ${fields.map(f => `Objects.equals(${f.name}, that.${f.name})`).join(' &&\n${i2}       ')};\n` +
    `${i}}\n` +
    `\n${i}@Override\n` +
    `${i}public int hashCode() {\n` +
    `${i2}return Objects.hash(${fieldRefs});\n` +
    `${i}}\n`
  )
}

function generateToString(fields: MemberField[], indentSize: number): string {
  const i = ' '.repeat(indentSize)
  const i2 = ' '.repeat(indentSize * 2)
  const pairs = fields.map(f => `"${f.name}=" + ${f.name}`).join(' + ",\n${i2}')
  return (
    `\n${i}@Override\n` +
    `${i}public String toString() {\n` +
    `${i2}return "${'${className}'}{" +\n${i2}       ${pairs} +\n${i2}       "'}";\n` +
    `${i}}\n`
  )
}

function generateClassCode(
  cd: ClassDef,
  opts: GenOptions,
  extraImports: Set<string>,
  collectedClasses: string[],
  depth: number,
  parentChain: string[],
): string {
  const i = ' '.repeat(opts.indentSize)
  const lines: string[] = []
  const cn = cd.className

  // Check circular in parentChain
  if (parentChain.includes(cn)) {
    collectedClasses.push(`// 循环引用检测：${cn} 已出现在祖类链 ${parentChain.join('→')}，跳过重复生成`)
    return ''
  }

  if (cd.isEnum) {
    // Generate enum
    if (depth === 0) {
      if (opts.packageName) lines.push(`package ${opts.packageName};\n`)
      if (opts.framework !== 'none') extraImports.add(frameworkImports(opts.framework)[0])
    }
    const enumBody = cd.enumValues?.map(v => `  ${v}`).join(',\n') ?? '  // empty'
    lines.push(`public enum ${cn} {\n${enumBody}\n}`)
    collectedClasses.push(lines.join('\n'))
    return lines.join('\n')
  }

  // Collect field types for import detection
  let hasBigDec = false
  let hasDate = false
  for (const f of cd.fields) {
    if (f.type.includes('BigDecimal')) hasBigDec = true
    if (f.type.includes('LocalDateTime')) hasDate = true
  }

  // Header
  if (depth === 0) {
    if (opts.packageName) lines.push(`package ${opts.packageName};\n`)
  }

  // Imports block (only at top level)
  if (depth === 0) {
    const imps: string[] = []
    if (opts.lombok) {
      imps.push(...collectLombokImports(opts.lombokBuilder))
    } else {
      if (hasBigDec) imps.push('import java.math.BigDecimal;')
      if (hasDate) imps.push('import java.time.LocalDateTime;')
      imps.push('import java.util.Objects;')
    }
    imps.push('import java.util.List;')
    if (opts.framework !== 'none') {
      imps.push(...frameworkImports(opts.framework))
    }
    for (const imp of [...new Set(imps)]) {
      lines.push(imp)
    }
    lines.push('')
  }

  // Class declaration
  if (opts.lombok) {
    lines.push('@Data')
    lines.push('@NoArgsConstructor')
    lines.push('@AllArgsConstructor')
    if (opts.lombokBuilder) lines.push('@Builder')
  }
  lines.push(`public class ${cn} {`)
  if (depth > 0 && opts.nestedMode === 'inner') {
    // For inner classes, first line of class body is paren reference
  }

  // Fields
  for (const f of cd.fields) {
    // Annotation
    const ann = fieldAnnotation(f.originalKey, f.name, opts.framework)
    if (ann.trim()) lines.push(indent(opts.indentSize, ann.trimRight()))
    // Field declaration
    let finalType = f.type
    if (f.type === 'Object') {
      const inferred = inferJavaType(f.rawValue, opts.numberStrategy, opts.nullStrategy)
      if (inferred !== 'Object') finalType = inferred
    }
    lines.push(`${i}private ${finalType} ${f.name};`)
  }

  // If not lombok, generate getters/setters/equals/hashCode/toString/constructors
  if (!opts.lombok) {
    // No-arg constructor
    lines.push('')
    lines.push(`${i}public ${cn}() {}`)
    // All-args constructor
    lines.push('')
    const params = cd.fields.map(f => `${finalizeType(f.type, f.rawValue, opts)} ${f.name}`).join(', ')
    lines.push(`${i}public ${cn}(${params}) {`)
    for (const f of cd.fields) {
      lines.push(`${i}${i}this.${f.name} = ${f.name};`)
    }
    lines.push(`${i}}`)

    // Getters and Setters
    for (const f of cd.fields) {
      const capName = f.name.charAt(0).toUpperCase() + f.name.slice(1)
      const ft = finalizeType(f.type, f.rawValue, opts)
      // Getter
      lines.push('')
      lines.push(`${i}public ${ft} get${capName}() {`)
      lines.push(`${i}${i}return ${f.name};`)
      lines.push(`${i}}`)
      // Setter
      lines.push('')
      lines.push(`${i}public void set${capName}(${ft} ${f.name}) {`)
      lines.push(`${i}${i}this.${f.name} = ${f.name};`)
      lines.push(`${i}}`)
    }

    // equals and hashCode
    const eqHash = generateEqualsHashCode(cd.fields, opts.indentSize)
    const toString = generateToString(cd.fields, opts.indentSize)
    lines.push('')
    lines.push(eqHash)
    lines.push('')
    lines.push(toString)
  }

  lines.push('}')
  collectedClasses.push(lines.join('\n'))

  // Nested classes
  let nestedCode = ''
  for (const nested of cd.nested) {
    const nc = generateClassCode(nested, opts, extraImports, collectedClasses, depth + 1, [...parentChain, cn])
    if (opts.nestedMode === 'separate') {
      collectedClasses.push(`\n// file: ${nested.className}.java\n${nc}`)
    } else {
      nestedCode += '\n' + indent(opts.indentSize, nc || '')
    }
  }

  if (opts.nestedMode === 'inner' && nestedCode) {
    // Append inner classes before closing brace
    const lastBrace = lines.lastIndexOf('}')
    if (lastBrace >= 0) {
      lines.splice(lastBrace, 0, nestedCode)
    }
  }

  return lines.join('\n')
}

function finalizeType(type: string, rawValue: unknown, opts: GenOptions): string {
  if (type === 'Object') {
    return inferJavaType(rawValue, opts.numberStrategy, opts.nullStrategy)
  }
  return type
}

function inferFields(obj: Record<string, unknown>, opts: GenOptions, parentChain: string[]): ClassDef {
  const className = parentChain.length === 0
    ? safeJavaIdentifier(opts.rootClassName)
    : toClassName(parentChain[parentChain.length - 1])

  const fields: MemberField[] = []
  const nested: ClassDef[] = []

  // Circular reference: check ANCESTORS only (parent chain minus self)
  const ancestors = parentChain.slice(0, -1)
  if (ancestors.includes(className)) {
    return { className, fields, nested }
  }

  const childChain = [...parentChain, className]

  for (const [key, value] of Object.entries(obj)) {
    try {
      const fieldName = toJavaFieldName(key, opts.fieldNaming)
      let javaType: string
      let nestedClass: ClassDef | null = null

      if (value !== null && value !== undefined && typeof value === 'object') {
        if (Array.isArray(value)) {
          if (value.length > 0) {
            const nonNullObj = value.find(v => v !== null && v !== undefined && typeof v === 'object' && !Array.isArray(v))
            if (nonNullObj) {
              const elemName = toClassName(key) + 'Item'
              nestedClass = inferFields(nonNullObj as Record<string, unknown>, opts, [...childChain, elemName])
              nestedClass.className = elemName
              nested.push(nestedClass)
              javaType = `List<${elemName}>`
            } else {
              const nonNullPrim = value.find(v => v !== null && v !== undefined)
              const elemType = nonNullPrim !== undefined ? inferJavaType(nonNullPrim, opts.numberStrategy, opts.nullStrategy) : 'Object'
              javaType = `List<${elemType}>`
            }
          } else {
            javaType = 'List<Object>'
          }
        } else {
          const nestedName = toClassName(key)
          nestedClass = inferFields(value as Record<string, unknown>, opts, [...childChain, nestedName])
          nestedClass.className = nestedName
          nested.push(nestedClass)
          javaType = nestedName
        }
      } else {
        javaType = inferJavaType(value, opts.numberStrategy, opts.nullStrategy)
      }

      fields.push({ name: fieldName, originalKey: key, type: javaType, rawValue: value })
    } catch (_) {
      // 细粒度容错：单个字段推断失败不炸掉整个对象的字段列表
      fields.push({ name: toJavaFieldName(key, opts.fieldNaming), originalKey: key, type: 'Object', rawValue: value })
    }
  }

  return { className, fields, nested }
}

function extractObjectFields(obj: Record<string, unknown>, opts: GenOptions, parentChain: string[]): ClassDef {
  return inferFields(obj, opts, parentChain)
}

function mergeArrayElements(arr: Record<string, unknown>[], opts: GenOptions, parentChain: string[]): ClassDef {
  const allKeys = new Set<string>()
  const keyValues: Record<string, unknown[]> = {}
  for (const item of arr) {
    for (const [k, v] of Object.entries(item)) {
      allKeys.add(k)
      if (!keyValues[k]) keyValues[k] = []
      keyValues[k].push(v)
    }
  }

  const className = parentChain.length === 0
    ? safeJavaIdentifier(opts.rootClassName) + 'Item'
    : toClassName(parentChain[parentChain.length - 1]) + 'Item'

  const fields: MemberField[] = []
  const nested: ClassDef[] = []

  for (const key of allKeys) {
    const values = keyValues[key] || []
    const fieldName = toJavaFieldName(key, opts.fieldNaming)
    const types = values.map(v => inferJavaType(v, opts.numberStrategy, opts.nullStrategy))
    const mergedType = mergeTypes(types)

    // Check if any value is an object
    const objVal = values.find(v => v !== null && v !== undefined && typeof v === 'object' && !Array.isArray(v))
    if (objVal && mergedType === 'Object') {
      const nestedName = toClassName(key)
      const nestedClass = extractObjectFields(objVal as Record<string, unknown>, opts, [...parentChain, nestedName])
      nestedClass.className = nestedName
      nested.push(nestedClass)
      fields.push({ name: fieldName, originalKey: key, type: nestedName, rawValue: objVal })
    } else {
      fields.push({ name: fieldName, originalKey: key, type: mergedType, rawValue: values[0] ?? null })
    }
  }

  return { className, fields, nested }
}

export function jsonToJava(jsonText: string, opts: GenOptions): GenResult {
  try {
    const parsed = JSON.parse(jsonText)

    if (parsed === null || parsed === undefined) {
      return { ok: false, message: '顶层值为 null 或 undefined，无法生成 Bean' }
    }

    const topType = typeof parsed
    if (topType === 'string' || topType === 'number' || topType === 'boolean') {
      return { ok: false, message: `顶层为 ${topType} 类型值，非对象或数组，无法生成 Bean。\nJSON 格式的 Bean 生成需顶层为对象 {} 或数组 []` }
    }

    const collectedClasses: string[] = []
    const extraImports = new Set<string>()

    if (Array.isArray(parsed)) {
      if (parsed.length === 0) {
        return { ok: false, message: '顶层为空数组 []，无法推断元素结构。\n请提供一个非空数组或对象作为输入' }
      }
      // Top-level array
      const allObjects = parsed.every(v => v !== null && v !== undefined && typeof v === 'object' && !Array.isArray(v))
      if (allObjects) {
        const itemClass = mergeArrayElements(parsed as Record<string, unknown>[], opts, [])
        generateClassCode(itemClass, opts, extraImports, collectedClasses, 0, [])
        return {
          ok: true,
          code: `// 顶层为数组，对应 List<${itemClass.className}>\n${collectedClasses.join('\n\n')}`,
          classes: collectedClasses,
        }
      }
      return { ok: false, message: '顶层数组元素不是对象，无法生成 Bean' }
    }

    // Top-level object
    const rootClass = extractObjectFields(parsed as Record<string, unknown>, opts, [])
    generateClassCode(rootClass, opts, extraImports, collectedClasses, 0, [])
    return {
      ok: true,
      code: collectedClasses.join('\n\n'),
      classes: collectedClasses,
    }
  } catch (e) {
    return { ok: false, message: `JSON 解析错误：${e instanceof Error ? e.message : String(e)}` }
  }
}
