import { tokenizeSql, splitStatements } from './ddlTokenizer'
import type { IRModel, IRTable, IRField, IRIndex, DdlDialect } from './ddlTypes'
import { toCamelCase, toPascalCase } from './naming'

export function parseDdl(input: string, dialect: DdlDialect): IRModel {
  const stmts = splitStatements(input)
  const commentMap = new Map<string, string>()
  const tables: IRTable[] = []

  for (const stmt of stmts) {
    const upper = stmt.toUpperCase().trim()

    if (upper.startsWith('COMMENT ON')) {
      const c = parseCommentOn(stmt)
      if (c) commentMap.set(c.table, c.comment)
      continue
    }

    if (upper.startsWith('CREATE TABLE') || upper.startsWith('CREATE TEMPORARY TABLE')) {
      const table = parseCreateTable(stmt, dialect)
      if (table) {
        const tableKey = table.tableName.toLowerCase()
        if (commentMap.has(tableKey)) {
          table.comment = commentMap.get(tableKey)
        }
        tables.push(table)
      }
    }
  }

  return { tables }
}

function parseCommentOn(stmt: string): { table: string; comment: string } | null {
  const tokens = tokenizeSql(stmt)
  const vals = tokens.map(t => t.type === 'keyword' ? t.value.toUpperCase() : t.value)
  const idx = vals.indexOf('ON')
  if (idx === -1) return null
  const afterOn = vals.slice(idx + 1)
  let tableName = ''
  for (const w of afterOn) {
    if (w === 'IS' || w === "'" || w.startsWith("'")) break
    tableName += w
  }
  const m = stmt.match(/IS\s+'([^']*)'/i)
  const comment = m ? m[1] : ''
  return { table: tableName.replace(/\s+/g, ' ').trim().toLowerCase(), comment }
}

function parseCreateTable(stmt: string, _dialect: DdlDialect): IRTable | null {
  let tableName = ''
  const nameMatch = stmt.match(/CREATE\s+(TEMPORARY\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`|"|)([^`"(\s]+)(?:`|"|)\s*/i)
  if (nameMatch) {
    tableName = nameMatch[2].replace(/[`"']/g, '')
  } else {
    return null
  }

  const body = extractBody(stmt)
  if (!body) return null

  let comment = ''
  let engine = ''
  let charset = ''
  let collation = ''
  const fields: IRField[] = []
  const indexes: IRIndex[] = []
  const primaryKeys: string[] = []

  const parts = splitTopLevel(body)
  for (const part of parts) {
    const trimmed = part.trim()
    const upperPart = trimmed.toUpperCase()
    if (!trimmed) continue

    if (upperPart.startsWith('PRIMARY KEY')) {
      const cols = extractParenList(trimmed)
      for (const col of cols) {
        const cn = col.replace(/[`"']/g, '').trim().split(/\s+/)[0]
        if (cn && !primaryKeys.includes(cn)) primaryKeys.push(cn)
      }
      continue
    }

    if (upperPart.startsWith('UNIQUE KEY') || upperPart.startsWith('UNIQUE INDEX') || upperPart.startsWith('UNIQUE')) {
      const idx = parseIndexDef(trimmed)
      if (idx) { idx.unique = true; indexes.push(idx) }
      continue
    }

    if (upperPart.startsWith('KEY') || upperPart.startsWith('INDEX')) {
      const idx = parseIndexDef(trimmed)
      if (idx) indexes.push(idx)
      continue
    }

    if (upperPart.startsWith('FULLTEXT')) {
      const idx = parseIndexDef(trimmed)
      if (idx) { idx.type = 'fulltext'; indexes.push(idx) }
      continue
    }

    if (upperPart.startsWith('CONSTRAINT') || upperPart.startsWith('FOREIGN KEY')) continue
    if (upperPart.startsWith('CHECK') || upperPart.startsWith('EXCLUDE')) continue

    if (upperPart.startsWith('ENGINE')) {
      const m = trimmed.match(/[Ee][Nn][Gg][Ii][Nn][Ee]\s*=\s*(\w+)/)
      if (m) engine = m[1]
      continue
    }

    if (upperPart.startsWith('DEFAULT CHARSET') || upperPart.startsWith('CHARSET')) {
      const m = trimmed.match(/(?:DEFAULT\s+)?CHARSET\s*=\s*(\w[\w-]*)/i)
      if (m) charset = m[1]
      continue
    }

    if (upperPart.startsWith('DEFAULT COLLATE') || upperPart.startsWith('COLLATE')) {
      const m = trimmed.match(/(?:DEFAULT\s+)?COLLATE\s*=\s*(\w[\w-]*)/i)
      if (m) collation = m[1]
      continue
    }

    if (upperPart.startsWith('COMMENT')) {
      const m = trimmed.match(/COMMENT\s*['"]([^'"]*)['"]\s*/i)
      if (m) comment = m[1] || comment
      continue
    }

    if (upperPart.startsWith('TABLE')) continue
    if (upperPart.startsWith('WITH')) continue

    const field = parseColumnDef(trimmed)
    if (field) {
      fields.push(field)
    }
  }

  for (let i = fields.length - 1; i >= 0; i--) {
    if (fields[i].primaryKey && !primaryKeys.includes(fields[i].columnName)) {
      primaryKeys.unshift(fields[i].columnName)
    }
  }

  const table: IRTable = {
    tableName,
    className: toPascalCase(tableName),
    nameLinked: true,
    comment,
    charset,
    collation,
    engine,
    fields,
    primaryKey: primaryKeys,
    indexes,
  }
  return table
}

function extractBody(sql: string): string | null {
  const start = sql.indexOf('(')
  if (start === -1) return null
  let depth = 0
  let inString = false
  let strChar = ''
  for (let i = start; i < sql.length; i++) {
    const c = sql[i]
    if (inString) {
      if (c === '\\') { i++; continue }
      if (c === strChar) inString = false
      continue
    }
    if (c === "'" || c === '"' || c === '`') { inString = true; strChar = c; continue }
    if (c === '(') depth++
    if (c === ')') {
      depth--
      if (depth === 0) return sql.slice(start + 1, i)
    }
  }
  return null
}

function splitTopLevel(body: string): string[] {
  const parts: string[] = []
  let depth = 0
  let inString = false
  let strChar = ''
  let current = ''

  for (const c of body) {
    if (inString) {
      current += c
      if (c === '\\') { continue }
      if (c === strChar) inString = false
      continue
    }
    if (c === "'" || c === '"' || c === '`') { inString = true; strChar = c; current += c; continue }
    if (c === '(') { depth++; current += c; continue }
    if (c === ')') { depth--; current += c; continue }
    if (c === ',' && depth === 0) {
      parts.push(current)
      current = ''
      continue
    }
    current += c
  }
  const s = current.trim()
  if (s) parts.push(s)
  return parts
}

function extractParenList(s: string): string[] {
  const m = s.match(/\(([^)]*)\)/)
  if (!m) return []
  return m[1].split(',').map(x => x.replace(/[`"']/g, '').trim()).filter(Boolean)
}

function parseColumnDef(s: string): IRField | null {
  const tokens = tokenizeSql(s)
  if (tokens.length < 2) return null

  const colName = tokens[0].raw.replace(/[`"']/g, '')
  let dbType = ''
  let dbTypeOverride: string | undefined
  let length: number | undefined
  let precision: number | undefined
  let scale: number | undefined
  let unsigned = false
  let nullable = true
  let primaryKey = false
  let autoIncrement = false
  let unique = false
  let defaultValue: string | undefined
  let onUpdateCurrentTimestamp = false
  let comment: string | undefined
  let enumValues: string[] | undefined
  let jsonSemantic: boolean | undefined

  let idx = 1
  if (idx < tokens.length) {
    dbType = tokens[idx].raw
    idx++
    if (dbType.toUpperCase() === 'SET') {
      dbType = 'SET'
    }
  }

  const isEnum = dbType.toUpperCase() === 'ENUM'
  if (isEnum) {
    const m = s.match(/ENUM\s*\(([^)]*)\)/i)
    if (m) {
      enumValues = m[1].split(',').map(v => v.replace(/['"]/g, '').trim())
    }
  }

  const lenMatch = tokens[idx - 1]?.raw.match(/\((\d+)(?:\s*,\s*(\d+))?\)/)
  if (lenMatch) {
    if (lenMatch[2]) {
      precision = Number(lenMatch[1])
      scale = Number(lenMatch[2])
    } else {
      length = Number(lenMatch[1])
    }
  }

  for (let i = idx; i < tokens.length; i++) {
    const t = tokens[i]
    const up = t.raw.toUpperCase()

    if (up === 'UNSIGNED') { unsigned = true; continue }
    if (up === 'ZEROFILL') { continue }
    if (up === 'NOT' && i + 1 < tokens.length && tokens[i + 1].raw.toUpperCase() === 'NULL') {
      nullable = false; i++; continue
    }
    if (up === 'NULL') { nullable = true; continue }
    if (up === 'DEFAULT') {
      i++
      let defParts: string[] = []
      while (i < tokens.length && tokens[i].raw.toUpperCase() !== 'NOT' && tokens[i].raw.toUpperCase() !== 'NULL' && tokens[i].raw.toUpperCase() !== 'COMMENT' && tokens[i].raw.toUpperCase() !== 'ON' && tokens[i].raw.toUpperCase() !== 'AUTO_INCREMENT' && tokens[i].raw.toUpperCase() !== 'UNIQUE' && tokens[i].raw.toUpperCase() !== 'PRIMARY' && tokens[i].raw.toUpperCase() !== 'KEY' && tokens[i].raw.toUpperCase() !== ',' && tokens[i].raw !== ')' && tokens[i].type !== 'operator') {
        defParts.push(tokens[i].raw)
        i++
      }
      i--
      defaultValue = defParts.join(' ')
      if (defaultValue === 'NULL' && !nullable) {
        defaultValue = undefined
      }
      if (defaultValue && defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
        defaultValue = defaultValue.slice(1, -1)
      }
      continue
    }
    if (up === 'ON' && i + 2 < tokens.length && tokens[i + 1].raw.toUpperCase() === 'UPDATE' && tokens[i + 2].type === 'keyword' && tokens[i + 2].raw.toUpperCase() === 'CURRENT_TIMESTAMP') {
      onUpdateCurrentTimestamp = true; i += 2; continue
    }
    if (up === 'AUTO_INCREMENT') { autoIncrement = true; continue }
    if (up === 'UNIQUE') { unique = true; continue }
    if (up === 'PRIMARY' && i + 1 < tokens.length && tokens[i + 1].raw.toUpperCase() === 'KEY') {
      primaryKey = true; i++; continue
    }
    if (up === 'KEY') { continue }
    if (up === 'COMMENT') {
      i++
      if (i < tokens.length) {
        comment = tokens[i].raw.replace(/['"]/g, '')
      }
      continue
    }
    if (up === 'JSON') {
      jsonSemantic = true
      continue
    }
  }

  const javaType = typeToJava(dbType, length, unsigned)

  return {
    columnName: colName,
    fieldName: toCamelCase(colName),
    nameLinked: true,
    javaType,
    dbTypeOverride,
    length,
    precision,
    scale,
    unsigned,
    nullable,
    primaryKey,
    autoIncrement,
    unique,
    defaultValue,
    onUpdateCurrentTimestamp,
    comment,
    enumValues,
    jsonSemantic,
    rawDbType: dbType,
    role: 'none',
  }
}

function typeToJava(dbType: string, length?: number, unsigned?: boolean): string {
  const base = dbType.replace(/\(.*/, '').toUpperCase().trim()
  switch (base) {
    case 'TINYINT': return length === 1 ? 'Boolean' : 'Byte'
    case 'SMALLINT': return 'Short'
    case 'MEDIUMINT':
    case 'INT':
    case 'INTEGER': return 'Integer'
    case 'YEAR': return 'Integer'
    case 'BIGINT': return unsigned ? 'BigInteger' : 'Long'
    case 'FLOAT': return 'Float'
    case 'DOUBLE':
    case 'REAL': return 'Double'
    case 'DECIMAL':
    case 'NUMERIC': return 'BigDecimal'
    case 'BOOL':
    case 'BOOLEAN':
    case 'BIT': return 'Boolean'
    case 'CHAR':
    case 'VARCHAR':
    case 'TINYTEXT':
    case 'TEXT':
    case 'MEDIUMTEXT':
    case 'LONGTEXT':
    case 'ENUM':
    case 'SET':
    case 'JSON':
    case 'JSONB':
    case 'UUID':
    case 'NAME':
    case 'INET':
    case 'CIDR':
    case 'MACADDR':
    case 'INTERVAL':
    case 'TSVECTOR':
    case 'TSQUERY':
    case 'MONEY':
    case 'POINT':
    case 'LINE':
    case 'LSEG':
    case 'BOX':
    case 'PATH':
    case 'POLYGON':
    case 'CIRCLE':
    case 'GEOMETRY':
    case 'LINESTRING':
      return 'String'
    case 'DATE': return 'LocalDate'
    case 'TIME': return 'LocalTime'
    case 'DATETIME':
    case 'TIMESTAMP':
    case 'TIMESTAMP_WITHOUT_TIME_ZONE':
      return 'LocalDateTime'
    case 'TIMESTAMP_WITH_TIME_ZONE':
    case 'TIMESTAMPTZ':
      return 'OffsetDateTime'
    case 'INT2': return 'Short'
    case 'INT4': return 'Integer'
    case 'INT8': return 'Long'
    case 'FLOAT4': return 'Float'
    case 'FLOAT8': return 'Double'
    case 'CHARACTER': return 'String'
    case 'CHARACTER_VARYING': return 'String'
    case 'BYTEA':
    case 'BINARY':
    case 'VARBINARY':
    case 'TINYBLOB':
    case 'BLOB':
    case 'MEDIUMBLOB':
    case 'LONGBLOB':
      return 'byte[]'
    case 'SERIAL':
    case 'BIGSERIAL':
      return 'Long'
    default: return 'String'
  }
}

function parseIndexDef(s: string): IRIndex | null {
  const m = s.match(/(?:UNIQUE\s+)?(?:KEY|INDEX)\s+(?:`|"|)(\w+)(?:`|"|)\s*\(([^)]+)\)/i)
  const m2 = s.match(/(?:UNIQUE\s+)?(?:KEY|INDEX)\s*(?:`|"|)(\w+)?(?:`|"|)\s*\(([^)]+)\)/i)
  const m3 = s.match(/(?:UNIQUE\s+)?\(([^)]+)\)/i)

  let name: string | undefined
  let colStr: string | undefined

  if (m) { name = m[1]; colStr = m[2] }
  else if (m2) { name = m2[1] || undefined; colStr = m2[2] }
  else if (m3) { colStr = m3[1] }

  if (!colStr) return null

  const columns = colStr.split(',').map(c => {
    const parts = c.replace(/[`"']/g, '').trim().split(/\s+/)
    const order = parts[1]?.toLowerCase() === 'desc' ? 'desc' as const : 'asc' as const
    return { columnName: parts[0], order }
  })

  return {
    name: name || undefined,
    columns,
    unique: s.toUpperCase().startsWith('UNIQUE'),
  }
}
