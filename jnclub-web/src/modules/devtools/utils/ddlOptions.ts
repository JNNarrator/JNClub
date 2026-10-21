import type { AnnotationStyle, DdlDialect, FieldNaming, PgAutoIncrementStyle, MultiClassLayout } from './ddlTypes'

export interface DdlOptions {
  annotationStyle: AnnotationStyle
  packageName: string
  indentSize: number
  fieldNaming: FieldNaming

  tinyint1AsBoolean: boolean
  unsignedIntAsLong: boolean
  unsignedBigintAsBigInteger: boolean
  timestampAsOffset: boolean
  timestamptzAsLocal: boolean
  jsonAsObject: boolean
  uuidAsUuid: boolean
  smallAsBoxed: boolean
  stripTablePrefix: string[]

  targetDialect: DdlDialect
  pgPreferText: boolean
  pgAutoIncrementStyle: PgAutoIncrementStyle
  mysqlBoolAsBit: boolean
  mysqlUuidAsBinary: boolean
  quoteIdentifiers: boolean
  emitIndexes: boolean
  defaultDecimalPS: string
  mysqlCharset: string
  mysqlCollation: string
  mysqlEmitEngine: boolean

  livePreview: boolean
  smartDefaults: boolean
  commentToJavaDoc: boolean
  multiClassLayout: MultiClassLayout
}

export const DEFAULT_DDL_OPTIONS: DdlOptions = {
  annotationStyle: 'lombok+mp',
  packageName: '',
  indentSize: 4,
  fieldNaming: 'camelCase',

  tinyint1AsBoolean: true,
  unsignedIntAsLong: true,
  unsignedBigintAsBigInteger: false,
  timestampAsOffset: false,
  timestamptzAsLocal: false,
  jsonAsObject: false,
  uuidAsUuid: false,
  smallAsBoxed: false,
  stripTablePrefix: ['t_', 'tbl_'],

  targetDialect: 'mysql',
  pgPreferText: false,
  pgAutoIncrementStyle: 'identity',
  mysqlBoolAsBit: false,
  mysqlUuidAsBinary: false,
  quoteIdentifiers: false,
  emitIndexes: true,
  defaultDecimalPS: '19,4',
  mysqlCharset: 'utf8mb4',
  mysqlCollation: 'utf8mb4_unicode_ci',
  mysqlEmitEngine: true,

  livePreview: false,
  smartDefaults: true,
  commentToJavaDoc: true,
  multiClassLayout: 'oneFile',
}
