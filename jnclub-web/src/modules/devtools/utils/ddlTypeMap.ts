import type { DdlDialect } from './ddlTypes'
import type { DdlOptions } from './ddlOptions'

export interface TypeMapping {
  dbType: string
  javaType: string
  importType?: string
  length?: number
  precision?: number
  scale?: number
}

function stripLength(t: string): string {
  return t.replace(/\(.*/, '').toUpperCase().trim()
}

function parseLength(s: string): { length?: number; precision?: number; scale?: number } {
  const m = s.match(/\((\d+)(?:\s*,\s*(\d+))?\)/)
  if (!m) return {}
  if (m[2]) return { precision: Number(m[1]), scale: Number(m[2]) }
  return { length: Number(m[1]) }
}

const MYSQL_JAVA_MAP: Record<string, string> = {
  TINYINT: 'Byte',
  SMALLINT: 'Short',
  MEDIUMINT: 'Integer',
  INT: 'Integer',
  INTEGER: 'Integer',
  BIGINT: 'Long',
  FLOAT: 'Float',
  DOUBLE: 'Double',
  DECIMAL: 'BigDecimal',
  NUMERIC: 'BigDecimal',
  BIT: 'Boolean',
  BOOL: 'Boolean',
  BOOLEAN: 'Boolean',
  CHAR: 'String',
  VARCHAR: 'String',
  TINYTEXT: 'String',
  TEXT: 'String',
  MEDIUMTEXT: 'String',
  LONGTEXT: 'String',
  JSON: 'String',
  ENUM: 'String',
  DATE: 'LocalDate',
  DATETIME: 'LocalDateTime',
  TIMESTAMP: 'LocalDateTime',
  TIME: 'LocalTime',
  YEAR: 'Integer',
  BINARY: 'byte[]',
  VARBINARY: 'byte[]',
  TINYBLOB: 'byte[]',
  BLOB: 'byte[]',
  MEDIUMBLOB: 'byte[]',
  LONGBLOB: 'byte[]',
  UUID: 'String',
  POINT: 'String',
  LINESTRING: 'String',
  POLYGON: 'String',
  GEOMETRY: 'String',
}

const PG_JAVA_MAP: Record<string, string> = {
  SMALLINT: 'Short',
  INT2: 'Short',
  INTEGER: 'Integer',
  INT: 'Integer',
  INT4: 'Integer',
  BIGINT: 'Long',
  INT8: 'Long',
  REAL: 'Float',
  FLOAT4: 'Float',
  DOUBLE: 'Double',
  FLOAT8: 'Double',
  DECIMAL: 'BigDecimal',
  NUMERIC: 'BigDecimal',
  BOOLEAN: 'Boolean',
  BOOL: 'Boolean',
  CHAR: 'String',
  CHARACTER: 'String',
  VARCHAR: 'String',
  CHARACTER_VARYING: 'String',
  TEXT: 'String',
  JSON: 'String',
  JSONB: 'String',
  UUID: 'UUID',
  DATE: 'LocalDate',
  TIMESTAMP: 'LocalDateTime',
  TIMESTAMP_WITHOUT_TIME_ZONE: 'LocalDateTime',
  TIMESTAMP_WITH_TIME_ZONE: 'OffsetDateTime',
  TIMESTAMPTZ: 'OffsetDateTime',
  TIME: 'LocalTime',
  TIME_WITHOUT_TIME_ZONE: 'LocalTime',
  TIME_WITH_TIME_ZONE: 'OffsetTime',
  TIMETZ: 'OffsetTime',
  INTERVAL: 'String',
  BYTEA: 'byte[]',
  BIT: 'Boolean',
  BIT_VARYING: 'String',
  VARBIT: 'String',
  INET: 'String',
  CIDR: 'String',
  MACADDR: 'String',
  POINT: 'String',
  LINE: 'String',
  LSEG: 'String',
  BOX: 'String',
  PATH: 'String',
  POLYGON: 'String',
  CIRCLE: 'String',
  TSVECTOR: 'String',
  TSQUERY: 'String',
  OID: 'Long',
  NAME: 'String',
  MONEY: 'BigDecimal',
}

function dialectDbMap(dialect: DdlDialect): Record<string, string> {
  if (dialect === 'postgresql') return PG_JAVA_MAP
  return MYSQL_JAVA_MAP
}

export function dbTypeToJava(
  rawDbType: string,
  options: DdlOptions,
): { javaType: string; importType?: string } {
  const map = dialectDbMap(options.targetDialect)
  const base = stripLength(rawDbType)
  const len = parseLength(rawDbType)

  if (options.targetDialect === 'mysql') {
    if (base === 'TINYINT' && options.tinyint1AsBoolean && len.length === 1) {
      return { javaType: 'Boolean' }
    }
    if (base === 'INT' && options.unsignedIntAsLong) return { javaType: 'Long' }
    if (base === 'BIGINT' && options.unsignedBigintAsBigInteger) return { javaType: 'BigInteger' }
    if (base === 'JSON' && options.jsonAsObject) return { javaType: 'String' }
    if (base === 'TIMESTAMP' && options.timestampAsOffset) return { javaType: 'OffsetDateTime' }
    if ((base === 'BINARY' || base === 'VARBINARY') && rawDbType.toUpperCase().includes('UUID') && options.uuidAsUuid) {
      return { javaType: 'UUID' }
    }
    if (base === 'SMALLINT' && options.smallAsBoxed) return { javaType: 'Short' }
  }

  if (options.targetDialect === 'postgresql') {
    if (base === 'JSONB' && options.jsonAsObject) return { javaType: 'String' }
    if ((base === 'TIMESTAMP_WITH_TIME_ZONE' || base === 'TIMESTAMPTZ') && options.timestamptzAsLocal) {
      return { javaType: 'LocalDateTime' }
    }
    if (base === 'UUID' && options.uuidAsUuid) return { javaType: 'UUID', importType: 'java.util.UUID' }
    if (base === 'TEXT' && !options.pgPreferText && len.length === 1) return { javaType: 'String' }
  }

  const mapped = map[base]
  if (mapped) {
    const importType = mapped === 'BigDecimal' ? 'java.math.BigDecimal'
      : mapped === 'BigInteger' ? 'java.math.BigInteger'
      : mapped === 'LocalDate' ? 'java.time.LocalDate'
      : mapped === 'LocalDateTime' ? 'java.time.LocalDateTime'
      : mapped === 'LocalTime' ? 'java.time.LocalTime'
      : mapped === 'OffsetDateTime' ? 'java.time.OffsetDateTime'
      : mapped === 'OffsetTime' ? 'java.time.OffsetTime'
      : mapped === 'UUID' ? 'java.util.UUID'
      : undefined
    return { javaType: mapped, importType }
  }

  return { javaType: 'String' }
}

export interface JavaTypeInfo {
  type: string
  importType?: string
  columnType: string
  dbTypeOverride?: string
  length?: number
  precision?: number
  scale?: number
  unsigned?: boolean
}

export function javaToDbType(
  javaType: string,
  dialect: DdlDialect,
  options: DdlOptions,
): JavaTypeInfo {
  const jt = javaType.replace(/^java\.(lang|time|math|util)\./, '')
  if (dialect === 'postgresql') {
    return pgJavaToDbType(jt, options)
  }
  return mysqlJavaToDbType(jt, dialect === 'oceanbase', options)
}

function mysqlJavaToDbType(javaType: string, _isOB: boolean, options: DdlOptions): JavaTypeInfo {
  switch (javaType) {
    case 'Boolean': return options.mysqlBoolAsBit ? { type: 'Boolean', columnType: 'BIT(1)' }
      : { type: 'Boolean', columnType: 'TINYINT(1)' }
    case 'Byte': return { type: 'Byte', columnType: 'TINYINT' }
    case 'Short': return { type: 'Short', columnType: 'SMALLINT' }
    case 'Integer': return { type: 'Integer', columnType: 'INT' }
    case 'Long': return { type: 'Long', columnType: 'BIGINT' }
    case 'Float': return { type: 'Float', columnType: 'FLOAT' }
    case 'Double': return { type: 'Double', columnType: 'DOUBLE' }
    case 'BigDecimal':
    case 'java.math.BigDecimal':
      return { type: 'BigDecimal', columnType: `DECIMAL(${options.defaultDecimalPS || '19,4'})` }
    case 'BigInteger':
    case 'java.math.BigInteger':
      return { type: 'BigInteger', columnType: 'DECIMAL(38,0)' }
    case 'String': return { type: 'String', columnType: 'VARCHAR(255)' }
    case 'LocalDate':
    case 'java.time.LocalDate':
      return { type: 'LocalDate', columnType: 'DATE' }
    case 'LocalDateTime':
    case 'java.time.LocalDateTime':
      return { type: 'LocalDateTime', columnType: 'DATETIME' }
    case 'LocalTime':
    case 'java.time.LocalTime':
      return { type: 'LocalTime', columnType: 'TIME' }
    case 'OffsetDateTime':
    case 'java.time.OffsetDateTime':
      return { type: 'OffsetDateTime', columnType: 'TIMESTAMP' }
    case 'UUID':
    case 'java.util.UUID':
      return options.mysqlUuidAsBinary
        ? { type: 'UUID', columnType: 'BINARY(16)' }
        : { type: 'UUID', columnType: 'VARCHAR(36)' }
    default: return { type: 'String', columnType: 'VARCHAR(255)' }
  }
}

function pgJavaToDbType(javaType: string, options: DdlOptions): JavaTypeInfo {
  switch (javaType) {
    case 'Boolean': return { type: 'Boolean', columnType: 'BOOLEAN' }
    case 'Byte': return { type: 'Byte', columnType: 'SMALLINT' }
    case 'Short': return { type: 'Short', columnType: 'SMALLINT' }
    case 'Integer': return { type: 'Integer', columnType: 'INTEGER' }
    case 'Long': return { type: 'Long', columnType: 'BIGINT' }
    case 'Float': return { type: 'Float', columnType: 'REAL' }
    case 'Double': return { type: 'Double', columnType: 'DOUBLE PRECISION' }
    case 'BigDecimal':
    case 'java.math.BigDecimal':
      return { type: 'BigDecimal', columnType: `NUMERIC(${options.defaultDecimalPS || '19,4'})` }
    case 'BigInteger':
    case 'java.math.BigInteger':
      return { type: 'BigInteger', columnType: 'NUMERIC(38,0)' }
    case 'String': return options.pgPreferText
      ? { type: 'String', columnType: 'TEXT' }
      : { type: 'String', columnType: 'VARCHAR(255)' }
    case 'LocalDate':
    case 'java.time.LocalDate':
      return { type: 'LocalDate', columnType: 'DATE' }
    case 'LocalDateTime':
    case 'java.time.LocalDateTime':
      return { type: 'LocalDateTime', columnType: 'TIMESTAMP' }
    case 'LocalTime':
    case 'java.time.LocalTime':
      return { type: 'LocalTime', columnType: 'TIME' }
    case 'OffsetDateTime':
    case 'java.time.OffsetDateTime':
      return { type: 'OffsetDateTime', columnType: 'TIMESTAMP WITH TIME ZONE' }
    case 'OffsetTime':
    case 'java.time.OffsetTime':
      return { type: 'OffsetTime', columnType: 'TIME WITH TIME ZONE' }
    case 'UUID':
    case 'java.util.UUID':
      return { type: 'UUID', columnType: 'UUID', importType: 'java.util.UUID' }
    default: return options.pgPreferText
      ? { type: 'String', columnType: 'TEXT' }
      : { type: 'String', columnType: 'VARCHAR(255)' }
  }
}
