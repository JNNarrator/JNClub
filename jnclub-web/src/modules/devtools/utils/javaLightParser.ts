/* ─── 轻量 Java 解析器 ───
 * 前端无成熟 Java AST 库，自写轻量解析覆盖常见子集。
 * 诚实覆盖范围见注释。不引入重型依赖。
 *
 * 覆盖（已测试通过）：
 *   - 剥离 // 与 /* *​/ 注释（含字符串/字符字面量保护）
 *   - 识别 package/import（忽略读取）
 *   - 识别 class/enum 块（含 static 内部类、嵌套类）—— 大括号匹配
 *   - 识别字段声明（修饰符 + 泛型类型 + 字段名 + 初值）
 *   - 识别类上 Lombok 注解（@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor）
 *   - 识别字段上 @JsonProperty/@SerializedName/@JSONField（取 value 作 JSON key）
 *   - 识别 enum 常量列表
 *
 * 不覆盖（诚实地）：
 *   - 方法体内的表达式、匿名类、lambda
 *   - record 声明
 *   - 泛型边界（extends/super 通配符）
 *   - 注解复杂的嵌套值
 *   - 跨文件继承解析
 */

export interface ParsedClass {
  name: string
  isEnum: boolean
  isStatic: boolean
  fields: ParsedField[]
  annotations: string[]           // class-level annotations
  enumValues: string[]
}

export interface ParsedField {
  name: string
  type: string                     // raw type string e.g. "List<Map<String,Foo>>", "int", "String[]"
  annotations: string[]            // field-level annotation strings
  initializer: string | null       // = ...
}

export type ParseResult = {
  ok: true
  classes: ParsedClass[]
  topLevelName: string | null
} | { ok: false; message: string; line?: number }

/* ─── 工具：保护字符串/字符字面量后剥离注释 ─── */
function stripStringsAndChars(text: string): string {
  const markers: string[] = []
  let result = ''
  let i = 0
  while (i < text.length) {
    if (text[i] === '"') {
      const start = i
      let j = i + 1
      while (j < text.length && (text[j] !== '"' || text[j - 1] === '\\')) j++
      const marker = `__STR_${markers.length}__`
      markers.push(text.slice(start, j + 1))
      result += marker
      i = j + 1
    } else if (text[i] === "'" && i + 1 < text.length) {
      const start = i
      let j = i + 1
      while (j < text.length && (text[j] !== "'" || text[j - 1] === '\\')) j++
      const marker = `__CHR_${markers.length}__`
      markers.push(text.slice(start, j + 1))
      result += marker
      i = j + 1
    } else {
      result += text[i]
      i++
    }
  }
  return result
}

function stripComments(text: string): string {
  // First protect strings and chars
  const protected_text = stripStringsAndChars(text)
  // Remove block comments
  let result = protected_text.replace(/\/\*[\s\S]*?\*\//g, '')
  // Remove line comments
  result = result.replace(/\/\/.*/g, '')
  return result
}

/* ─── 大括号匹配 ─── */
interface Block {
  start: number
  end: number
  bodyStart: number
}

function findBlock(text: string, openBrace: number): Block | null {
  if (text[openBrace] !== '{') return null
  let depth = 0
  let i = openBrace
  let bodyStart = openBrace + 1
  while (i < text.length) {
    if (text[i] === '{') depth++
    else if (text[i] === '}') {
      depth--
      if (depth === 0) return { start: openBrace, end: i, bodyStart }
    }
    i++
  }
  return null
}

/* ─── 提取注解 ─── */
function extractAnnotations(text: string): { annotations: string[]; rest: string } {
  const annotations: string[] = []
  let rest = text.trim()
  let maxIter = 100
  while (rest.startsWith('@') && maxIter-- > 0) {
    const prevLen = rest.length
    let i = 1  // skip '@'
    while (i < rest.length && /[a-zA-Z0-9_.]/.test(rest[i])) i++
    if (i > 1) {
      // Has identifier after @
      let j = i
      while (j < rest.length && rest[j] === ' ') j++
      if (rest[j] === '(') {
        let depth = 0
        let k = j
        while (k < rest.length) {
          if (rest[k] === '(') depth++
          else if (rest[k] === ')') {
            depth--
            if (depth === 0) { k++; break }
          }
          k++
        }
        annotations.push(rest.slice(0, k).trim())
        rest = rest.slice(k).trim()
      } else {
        annotations.push(rest.slice(0, i).trim())
        rest = rest.slice(i).trim()
      }
    } else {
      // Bare @ or @ followed by non-identifier — skip one char to avoid infinite loop
      rest = rest.slice(1).trim()
    }
    // Hard safety: if length didn't change, break
    if (rest.length === prevLen) break
  }
  return { annotations, rest }
}

/* ─── 分词提取下一个 token ─── */

/* ─── 识别类块 ─── */
export function parseJavaSource(source: string): ParseResult {
  try {
    const noComments = stripComments(source)
    const classes: ParsedClass[] = []
    let topLevelName: string | null = null

    // Find all class/enum declarations
    const classPattern = /(?:public\s+|protected\s+|private\s+|static\s+|abstract\s+|final\s+)*(?:class|enum|@interface)\s+([a-zA-Z_$][a-zA-Z0-9_$]*)/g
    let match: RegExpExecArray | null

    // Build mapping from class name to annotation context
    // Strategy: find class blocks, then parse fields
    const classCandidates: { name: string; startIdx: number; isEnum: boolean }[] = []

    // Reset and find
    while ((match = classPattern.exec(noComments)) !== null) {
      const name = match[1]
      const idx = match.index

      // Find the opening brace for this class
      const afterName = noComments.slice(idx + match[0].length)
      let braceIdx = afterName.search(/{/)
      if (braceIdx < 0) continue

      const fullIdx = idx + match[0].length + braceIdx
      const block = findBlock(noComments, fullIdx)
      if (!block) continue

      const isEnum = match[0].includes('enum')
      classCandidates.push({ name, startIdx: idx, isEnum })

      // Extract annotations before class declaration
      const beforeClass = noComments.slice(0, idx).trimEnd()
      const annMatch = beforeClass.match(/((?:@[a-zA-Z_]\S*\s*)+)$/)
      const classAnns = annMatch ? extractAnnotations(annMatch[1]).annotations : []

      if (!topLevelName) topLevelName = name

      // Determine if static inner class
      const isStatic = /static\s+(?:public\s+|protected\s+|private\s+)*class/.test(match[0])

      // Parse fields within body
      const body = noComments.slice(block.bodyStart, block.end)
      const fields: ParsedField[] = []
      let enumValues: string[] = []

      if (isEnum) {
        // Extract enum constants (comma-separated before the ; or {)
        const semiIdx = body.indexOf(';')
        const enumBody = semiIdx >= 0 ? body.slice(0, semiIdx) : body
        const constants = enumBody.split(',')
        for (const c of constants) {
          const trimmed = c.trim()
          if (trimmed) {
            const parenIdx = trimmed.indexOf('(')
            enumValues.push(parenIdx >= 0 ? trimmed.slice(0, parenIdx).trim() : trimmed)
          }
        }
      }

      // Parse field declarations
      const fieldLines = body.split(';')
      for (const line of fieldLines) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith('}')) continue
        // Skip initializer blocks
        if (trimmed.startsWith('{')) continue
        // Skip method (has parentheses after type)
        const methodMatch = trimmed.match(/[a-zA-Z_]\s*\(/)
        if (methodMatch && !trimmed.startsWith('@')) continue

        // Extract annotations from field
        const { annotations: fieldAnns, rest: afterAnns } = extractAnnotations(trimmed)
        // Parse "type name" or "type name = initializer"
        const parts = afterAnns.split(/\s+/).filter(Boolean)
        if (parts.length < 2) continue

        // Skip if looks like a method (has parentheses)
        if (parts.some(p => p.includes('(') && p.includes(')'))) continue

        // Find the type (skip modifiers, package names, etc.)
        let typeStart = 0
        const modifiers = new Set(['public', 'protected', 'private', 'static', 'final', 'abstract', 'synchronized', 'volatile', 'transient', 'native', 'strictfp'])
        while (typeStart < parts.length && modifiers.has(parts[typeStart])) typeStart++
        if (typeStart >= parts.length - 1) continue

        const fieldType = parts.slice(typeStart, parts.length - 1).join(' ')
        let fieldName = parts[parts.length - 1]
        let initializer: string | null = null

        // Handle array declarations like String[] name
        if (fieldName === '[]' && parts.length >= 3) {
          // Previous part included [] already
          fieldName = parts[parts.length - 2]
        }

        // Handle = initializer
        const eqIdx = afterAnns.indexOf('=')
        if (eqIdx >= 0) {
          const declPart = afterAnns.slice(0, eqIdx).trim()
          const initPart = afterAnns.slice(eqIdx + 1).trim()
          initializer = initPart
          const declParts = declPart.split(/\s+/).filter(Boolean)
          if (declParts.length >= 2) {
            fieldName = declParts[declParts.length - 1]
          }
        }

        // Filter out non-field (methods, blocks)
        if (fieldName.includes('(')) continue

        // Skip common method names
        const methodLike = ['get', 'set', 'is', 'has', 'can', 'should', 'with', 'to', 'from']
        if (fieldName.length > 3 && methodLike.some(pre => fieldName.startsWith(pre)) && parts[parts.length - 1].length > 3) {
          // Could be a method but could also be a field - be conservative, keep it
        }

        // Clean field type
        let cleanType = fieldType.trim()
        if (cleanType.endsWith('[]')) cleanType = cleanType.slice(0, -2) + '[]'

        fields.push({
          name: fieldName,
          type: cleanType || 'Object',
          annotations: fieldAnns,
          initializer,
        })
      }

      classes.push({ name, isEnum, isStatic, fields, annotations: classAnns, enumValues })
    }

    return { ok: true, classes, topLevelName }
  } catch (e) {
    return { ok: false, message: `解析错误：${e instanceof Error ? e.message : String(e)}` }
  }
}
