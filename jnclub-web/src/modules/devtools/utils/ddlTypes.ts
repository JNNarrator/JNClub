export type DdlDialect = 'mysql' | 'postgresql' | 'oceanbase'

export type AnnotationStyle = 'none' | 'lombok' | 'mybatis-plus' | 'lombok+jpa' | 'lombok+mp'

export type DdlDirection = 'ddl2java' | 'java2ddl'

export type FieldRole = 'none' | 'id' | 'createTime' | 'updateTime' | 'deleteFlag' | 'version' | 'tenantId' | 'creator' | 'updater'

export type PrimaryKeyStrategy = 'auto_increment' | 'sequence' | 'snowflake' | 'uuid' | 'none'

export type PgAutoIncrementStyle = 'identity' | 'serial'

export type MultiClassLayout = 'oneFile' | 'perTableBlock'

export type FieldNaming = 'camelCase' | 'snakeCase' | 'PascalCase'

export interface IRModel {
  tables: IRTable[]
}

export interface IRTable {
  tableName: string
  className: string
  nameLinked: boolean
  comment?: string
  charset?: string
  collation?: string
  engine?: string
  fields: IRField[]
  primaryKey: string[]
  indexes: IRIndex[]
  rawExtras?: string
}

export interface IRField {
  columnName: string
  fieldName: string
  nameLinked: boolean
  javaType: string
  dbTypeOverride?: string
  length?: number
  precision?: number
  scale?: number
  unsigned?: boolean
  nullable: boolean
  primaryKey: boolean
  primaryKeyStrategy?: PrimaryKeyStrategy
  autoIncrement: boolean
  unique: boolean
  defaultValue?: string
  onUpdateCurrentTimestamp?: boolean
  comment?: string
  role: FieldRole
  charset?: string
  collation?: string
  enumValues?: string[]
  jsonSemantic?: boolean
  rawDbType?: string
}

export interface IRIndex {
  name?: string
  columns: { columnName: string; order?: 'asc' | 'desc' }[]
  unique: boolean
  type?: 'btree' | 'hash' | 'fulltext'
}

export const FIELD_ROLE_DEFAULTS: Record<FieldRole, {
  recommendedColumn: string
  recommendedJavaType: string
  defaults: Partial<IRField>
}> = {
  none: { recommendedColumn: '', recommendedJavaType: '', defaults: {} },
  id: {
    recommendedColumn: 'id',
    recommendedJavaType: 'Long',
    defaults: { nullable: false, primaryKey: true },
  },
  createTime: {
    recommendedColumn: 'create_time',
    recommendedJavaType: 'LocalDateTime',
    defaults: { nullable: false, defaultValue: 'CURRENT_TIMESTAMP' },
  },
  updateTime: {
    recommendedColumn: 'update_time',
    recommendedJavaType: 'LocalDateTime',
    defaults: { nullable: false, defaultValue: 'CURRENT_TIMESTAMP', onUpdateCurrentTimestamp: true },
  },
  deleteFlag: {
    recommendedColumn: 'is_deleted',
    recommendedJavaType: 'Boolean',
    defaults: { nullable: false, defaultValue: '0' },
  },
  version: {
    recommendedColumn: 'version',
    recommendedJavaType: 'Integer',
    defaults: { nullable: false, defaultValue: '0' },
  },
  tenantId: {
    recommendedColumn: 'tenant_id',
    recommendedJavaType: 'Long',
    defaults: { nullable: false },
  },
  creator: {
    recommendedColumn: 'create_by',
    recommendedJavaType: 'String',
    defaults: {},
  },
  updater: {
    recommendedColumn: 'update_by',
    recommendedJavaType: 'String',
    defaults: {},
  },
}
