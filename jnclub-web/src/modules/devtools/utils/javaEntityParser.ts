import type { IRModel, IRTable, IRField, AnnotationStyle, FieldRole } from './ddlTypes'
import { toSnakeCase } from './naming'

interface ParsedJavaField {
  fieldName: string
  javaType: string
  annotations: string[]
  comment?: string
}

interface ParsedJavaClass {
  className: string
  fields: ParsedJavaField[]
  classAnnotations: string[]
  comment?: string
}

export function parseJavaSource(source: string): ParsedJavaClass[] {
  const classes: ParsedJavaClass[] = []
  const classRegex = /(?:public\s+)?(?:abstract\s+)?class\s+(\w+)(?:\s+extends\s+\w+)?(?:\s+implements\s+[\w,\s]+)?\s*\{/g
  let match: RegExpExecArray | null

  while ((match = classRegex.exec(source)) !== null) {
    const className = match[1]
    const start = match.index
    const body = extractBraceBlock(source, start + match[0].length - 1)
    const classBlock = body || source.slice(match.index)

    const classAnnotations = extractAnnotations(source.slice(0, start))

    const fields = parseFields(classBlock)
    classes.push({ className, fields, classAnnotations })
  }

  return classes
}

function extractBraceBlock(s: string, openIdx: number): string | null {
  let depth = 0
  let start = -1
  for (let i = openIdx; i < s.length; i++) {
    if (s[i] === '{') { if (depth === 0) start = i; depth++ }
    else if (s[i] === '}') { depth--; if (depth === 0) return s.slice(start! + 1, i) }
  }
  return null
}

function extractAnnotations(block: string): string[] {
  const anns: string[] = []
  const regex = /@(\w+(?:\.\w+)*(?:\s*\([^)]*\))?)/g
  let m: RegExpExecArray | null
  while ((m = regex.exec(block)) !== null) {
    anns.push(m[1])
  }
  return anns
}

function parseFields(block: string): ParsedJavaField[] {
  const fields: ParsedJavaField[] = []
  const fieldRegex = /(?:(\/\*\*[\s\S]*?\*\/)\s*)?((?:@\w+(?:\([^)]*\))?\s*\n?\s*)*)(private|protected|public)\s+(static\s+)?(\w+(?:<[^>]*>)?(?:\[\])?)\s+(\w+)\s*[=;]/g
  let m: RegExpExecArray | null

  while ((m = fieldRegex.exec(block)) !== null) {
    const javadoc = m[1]?.trim()
    const annBlock = m[2]?.trim()
    const type = m[5]
    const name = m[6]

    const annotations = annBlock ? extractAnnotations(annBlock) : []
    let comment: string | undefined
    if (javadoc) {
      const cm = javadoc.match(/\*\s*(\S.*?)\s*\*\/|\*\s*([^*].*?)$/)
      if (cm) comment = (cm[1] || cm[2] || '').trim()
    }

    fields.push({ fieldName: name, javaType: type, annotations, comment })
  }

  return fields
}

function detectAnnotationStyle(annotations: string[]): AnnotationStyle {
  const annSet = new Set(annotations.map(a => a.replace(/\(.*/, '')))
  const hasLombok = annSet.has('Data') || annSet.has('Getter') || annSet.has('Setter') || annSet.has('ToString') || annSet.has('EqualsAndHashCode')
  const hasJpa = annSet.has('Table') || annSet.has('Column') || annSet.has('Id') || annSet.has('GeneratedValue') || annSet.has('Entity')
  const hasMp = annSet.has('TableName') || annSet.has('TableField') || annSet.has('TableId')

  if (hasLombok && hasMp) return 'lombok+mp'
  if (hasLombok && hasJpa) return 'lombok+jpa'
  if (hasLombok) return 'lombok'
  if (hasMp) return 'mybatis-plus'
  if (hasJpa) return 'none'
  return 'lombok+mp'
}

const ROLE_ANNOTATION_MAP: Record<string, string> = {
  TableId: 'id',
  TableLogic: 'deleteFlag',
  TableField: '',
  CreatedDate: 'createTime',
  CreatedBy: 'creator',
  LastModifiedDate: 'updateTime',
  LastModifiedBy: 'updater',
  Version: 'version',
}

const ROLE_COLUMN_MAP: Record<string, FieldRole> = {
  create_time: 'createTime',
  create_date: 'createTime',
  gmt_create: 'createTime',
  update_time: 'updateTime',
  gmt_modified: 'updateTime',
  update_date: 'updateTime',
  is_deleted: 'deleteFlag',
  deleted: 'deleteFlag',
  is_del: 'deleteFlag',
  del_flag: 'deleteFlag',
  version: 'version',
  tenant_id: 'tenantId',
  create_by: 'creator',
  create_user: 'creator',
  update_by: 'updater',
  update_user: 'updater',
  id: 'id',
}

export function parseJavaToModel(source: string): IRModel {
  const classes = parseJavaSource(source)
  const tables: IRTable[] = []

  for (const cls of classes) {
    const style = detectAnnotationStyle(cls.classAnnotations)

    const tableName = extractTableName(cls, style)
    const fields: IRField[] = []

    for (const f of cls.fields) {
      const columnName = extractColumnName(f, style)
      const role = detectRole(f, columnName)
      const nullable = !f.javaType.endsWith('Boolean') || true
      const isPk = f.annotations.some(a => /@?(TableId|Id|GeneratedValue)/.test(a))

      fields.push({
        columnName,
        fieldName: f.fieldName,
        nameLinked: true,
        javaType: f.javaType,
        nullable,
        primaryKey: isPk,
        autoIncrement: isPk && f.annotations.some(a => a.includes('GenerationType.IDENTITY')),
        unique: false,
        comment: f.comment,
        role,
      })
    }

    tables.push({
      tableName,
      className: cls.className,
      nameLinked: true,
      fields,
      primaryKey: fields.filter(f => f.primaryKey).map(f => f.columnName),
      indexes: [],
    })
  }

  return { tables }
}

function extractTableName(cls: ParsedJavaClass, _style: AnnotationStyle): string {
  for (const ann of cls.classAnnotations) {
    const m = ann.match(/TableName\s*\(\s*value\s*=\s*"([^"]+)"/i)
    if (m) return m[1]
    const m2 = ann.match(/TableName\s*\(\s*"([^"]+)"/i)
    if (m2) return m2[1]
    const m3 = ann.match(/Table\s*\(\s*name\s*=\s*"([^"]+)"/i)
    if (m3) return m3[1]
  }
  return toSnakeCase(cls.className)
}

function extractColumnName(field: ParsedJavaField, _style: AnnotationStyle): string {
  for (const ann of field.annotations) {
    const m = ann.match(/TableField\s*\(\s*value\s*=\s*"([^"]+)"/i)
    if (m) return m[1]
    const m2 = ann.match(/TableField\s*\(\s*"([^"]+)"/i)
    if (m2) return m2[1]
    const m3 = ann.match(/Column\s*\(\s*name\s*=\s*"([^"]+)"/i)
    if (m3) return m3[1]
  }
  return toSnakeCase(field.fieldName)
}

function detectRole(field: ParsedJavaField, columnName: string): FieldRole {
  for (const ann of field.annotations) {
    const annName = ann.replace(/\(.*/, '')
    const mapped = ROLE_ANNOTATION_MAP[annName]
    if (mapped) return mapped as any
  }
  const lower = columnName.toLowerCase()
  return (ROLE_COLUMN_MAP[lower] as any) || 'none'
}
