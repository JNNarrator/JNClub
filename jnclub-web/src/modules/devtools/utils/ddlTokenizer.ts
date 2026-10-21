export interface SqlToken {
  type: 'keyword' | 'identifier' | 'string' | 'number' | 'operator' | 'comment' | 'punctuation'
  value: string
  raw: string
}

export const SQL_KEYWORDS = new Set([
  'ADD','ALL','ALTER','AND','ANY','AS','ASC','BEGIN','BETWEEN','BIGINT','BINARY','BIT','BLOB',
  'BOOLEAN','BY','CASCADE','CASE','CHANGE','CHAR','CHARACTER','CHECK','COALESCE','COLLATE',
  'COLUMN','COMMIT','CONSTRAINT','CONVERT','CREATE','CROSS','CURRENT_TIMESTAMP','CURRENT_DATE',
  'CURRENT_TIME','DATABASE','DATE','DATETIME','DECIMAL','DECLARE','DEFAULT','DELETE','DESC',
  'DESCRIBE','DISTINCT','DOUBLE','DROP','ELSE','END','ENUM','ESCAPE','EXISTS','EXPLAIN',
  'FLOAT','FOREIGN','FOR','FROM','FULL','FUNCTION','GROUP','HAVING','IDENTITY','IF','IN',
  'INDEX','INNER','INSERT','INT','INTEGER','INTERSECT','INTO','INOUT','IS','JOIN','KEY',
  'LEFT','LIKE','LIMIT','LONGBLOB','LONGTEXT','MATCHED','MEDIUMBLOB','MEDIUMINT','MEDIUMTEXT',
  'MODIFY','NOT','NULL','NUMERIC','ON','OPTION','OR','ORDER','OUT','OUTER','PARTITION',
  'PLAIN','PRECISION','PRIMARY','PROCEDURE','RANGE','REAL','REFERENCES','REGEXP','RENAME',
  'REPLACE','RIGHT','ROLLBACK','ROW','ROWS','SCHEMA','SELECT','SESSION','SET','SHOW',
  'SMALLINT','SOME','START','TABLE','TEXT','THEN','TIME','TIMESTAMP','TINYBLOB','TINYINT',
  'TINYTEXT','TO','TRANSACTION','TRIGGER','TRUNCATE','UNION','UNIQUE','UNSIGNED','UPDATE',
  'USAGE','USE','USING','VALUES','VARCHAR','VARYING','VIEW','WHEN','WHERE','WHILE','WITH',
  'WRITE','YEAR','ZEROFILL',
])

export function tokenizeSql(input: string): SqlToken[] {
  const tokens: SqlToken[] = []
  let i = 0
  const len = input.length

  function peek(n = 0): string | undefined { return input[i + n] }
  function advance(): string { return input[i++] }

  while (i < len) {
    const ch = peek()

    if (ch === "'" || ch === '"') {
      tokens.push(readString(ch))
      continue
    }

    if (ch === '`') {
      tokens.push(readBacktick())
      continue
    }

    if (ch === '-' && peek(1) === '-') {
      tokens.push(readLineComment())
      continue
    }

    if (ch === '/' && peek(1) === '*') {
      tokens.push(readBlockComment())
      continue
    }

    if (/[0-9]/.test(ch!)) {
      tokens.push(readNumber())
      continue
    }

    if (/[a-zA-Z_]/.test(ch!)) {
      tokens.push(readWord())
      continue
    }

    if (/\s/.test(ch!)) {
      advance()
      continue
    }

    if ('();,=<>!+-*/%'.includes(ch!)) {
      let op = advance()
      if ((op === '!' || op === '<' || op === '>') && peek() === '=') {
        op += advance()
      }
      tokens.push({ type: 'operator', value: op, raw: op })
      continue
    }

    advance()
  }

  return tokens

  function readString(quote: string): SqlToken {
    let raw = advance()
    while (i < len) {
      const c = advance()
      raw += c
      if (c === '\\') raw += advance()
      else if (c === quote) break
    }
    return { type: 'string', value: raw.slice(1, -1), raw }
  }

  function readBacktick(): SqlToken {
    let raw = advance()
    while (i < len) {
      const c = advance()
      raw += c
      if (c === '`') break
    }
    return { type: 'identifier', value: raw.slice(1, -1), raw }
  }

  function readLineComment(): SqlToken {
    let raw = ''
    while (i < len && peek() !== '\n') raw += advance()
    return { type: 'comment', value: raw, raw }
  }

  function readBlockComment(): SqlToken {
    let raw = advance() + advance()
    while (i + 1 < len && !(peek() === '*' && peek(1) === '/')) raw += advance()
    if (i < len) raw += advance() + advance()
    return { type: 'comment', value: raw, raw }
  }

  function readNumber(): SqlToken {
    let raw = ''
    while (i < len && /[0-9.eE+\-]/.test(peek()!)) raw += advance()
    return { type: 'number', value: raw, raw }
  }

  function readWord(): SqlToken {
    let raw = ''
    while (i < len && /[a-zA-Z0-9_$]/.test(peek()!)) raw += advance()
    const upper = raw.toUpperCase()
    return {
      type: SQL_KEYWORDS.has(upper) ? 'keyword' : 'identifier',
      value: raw,
      raw,
    }
  }
}

export function tokenizedSqlText(input: string): string {
  return tokenizeSql(input).map(t => t.value).join('')
}

export function normalizedSql(input: string): string {
  return tokenizeSql(input)
    .filter(t => t.type !== 'comment')
    .filter(t => t.type !== 'string' || true)
    .map(t => t.type === 'string' ? `'${t.value}'` : t.value)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function splitStatements(input: string): string[] {
  const tokens = tokenizeSql(input)
  const stmts: string[] = []
  let current = ''
  for (const t of tokens) {
    if (t.type === 'operator' && t.value === ';') {
      const s = current.trim()
      if (s) stmts.push(s)
      current = ''
    } else {
      current += t.raw
    }
  }
  const s = current.trim()
  if (s) stmts.push(s)
  return stmts
}
