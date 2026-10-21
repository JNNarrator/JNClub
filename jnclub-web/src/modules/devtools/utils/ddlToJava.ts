import type { IRTable, IRField } from './ddlTypes'
import type { DdlOptions } from './ddlOptions'
import { toPascalCase, safeJavaIdentifier } from './naming'

const ROLE_ANNOTATIONS: Record<string, string[]> = {
  id: ['@TableId'],
  createTime: ['@TableField(fill = FieldFill.INSERT)'],
  updateTime: ['@TableField(fill = FieldFill.INSERT_UPDATE)'],
  deleteFlag: ['@TableLogic'],
  version: ['@Version'],
}

const LOMBOK_ON_CLASS: string[] = ['@Data', '@EqualsAndHashCode(callSuper = false)', '@Accessors(chain = true)']

export function generateJavaImports(model: IRTable, options: DdlOptions): string[] {
  const imports: Set<string> = new Set()

  const style = options.annotationStyle

  if (style === 'lombok+mp' || style === 'lombok+jpa' || style === 'lombok') {
    imports.add('import lombok.Data;')
    imports.add('import lombok.EqualsAndHashCode;')
    imports.add('import lombok.experimental.Accessors;')
  }

  if (style === 'mybatis-plus' || style === 'lombok+mp') {
    imports.add('import com.baomidou.mybatisplus.annotation.*;')
    imports.add('import com.baomidou.mybatisplus.core.toolkit.support.SortedLambda;')
  }

  if (style === 'none' || style === 'lombok+jpa') {
    imports.add('import javax.persistence.*;')
  }

  for (const field of model.fields) {
    const jt = field.javaType
    if (jt === 'BigDecimal' || jt === 'BigInteger') imports.add('import java.math.' + jt + ';')
    else if (jt === 'LocalDate' || jt === 'LocalDateTime' || jt === 'LocalTime') imports.add('import java.time.' + jt + ';')
    else if (jt === 'OffsetDateTime' || jt === 'OffsetTime') imports.add('import java.time.' + jt + ';')
    else if (jt === 'UUID') imports.add('import java.util.UUID;')
  }

  return [...imports]
}

export function fieldAnnotations(field: IRField, options: DdlOptions): string[] {
  const anns: string[] = []

  if (field.role !== 'none') {
    const roleAnns = ROLE_ANNOTATIONS[field.role]
    if (roleAnns) anns.push(...roleAnns)
  }

  if (field.primaryKey && field.role !== 'id') {
    if (options.annotationStyle === 'mybatis-plus' || options.annotationStyle === 'lombok+mp') {
      anns.push('@TableId')
    } else if (options.annotationStyle === 'none' || options.annotationStyle === 'lombok+jpa') {
      anns.push('@Id')
      if (field.autoIncrement) anns.push('@GeneratedValue(strategy = GenerationType.IDENTITY)')
    }
  }

  if (field.autoIncrement && field.primaryKey && (options.annotationStyle === 'mybatis-plus' || options.annotationStyle === 'lombok+mp')) {
    anns.push('@TableId(type = IdType.AUTO)')
  }

  if (options.annotationStyle === 'mybatis-plus' || options.annotationStyle === 'lombok+mp') {
    const colName = field.columnName !== toSnake(field.fieldName) ? field.columnName : undefined
    if (colName) anns.push(`@TableField("${colName}")`)

    if (field.role === 'createTime') {
      anns.push('@TableField(fill = FieldFill.INSERT)')
    } else if (field.role === 'updateTime') {
      anns.push('@TableField(fill = FieldFill.INSERT_UPDATE)')
    } else if (field.role === 'deleteFlag') {
      anns.push('@TableLogic')
    } else if (field.role === 'version') {
      anns.push('@Version')
    }
  }

  if (options.annotationStyle === 'none' || options.annotationStyle === 'lombok+jpa') {
    if (field.columnName !== toSnake(field.fieldName)) {
      anns.push(`@Column(name = "${field.columnName}")`)
    }
  }

  if (options.commentToJavaDoc && field.comment) {
    anns.push(`/** ${field.comment} */`)
  }

  return anns
}

function toSnake(s: string): string {
  return s.replace(/([A-Z])/g, '_$1').toLowerCase().replace(/^_/, '')
}

export function generateClassCode(
  model: IRTable,
  options: DdlOptions,
): string {
  const indent = ' '.repeat(options.indentSize || 4)
  const lines: string[] = []

  const imports = generateJavaImports(model, options)

  const pkg = options.packageName ? `package ${options.packageName};\n` : ''

  if (pkg) lines.push(pkg)
  if (imports.length > 0) {
    lines.push(...imports)
    lines.push('')
  }

  const className = toPascalCase(safeJavaIdentifier(model.className))

  if ((options.annotationStyle === 'mybatis-plus' || options.annotationStyle === 'lombok+mp') && model.tableName !== toSnake(className)) {
    lines.push(`@TableName("${model.tableName}")`)
  } else if (options.annotationStyle === 'none' || options.annotationStyle === 'lombok+jpa') {
    lines.push(`@Entity`)
    if (model.tableName !== toSnake(className)) {
      lines.push(`@Table(name = "${model.tableName}")`)
    }
  }

  if (options.annotationStyle === 'lombok' || options.annotationStyle === 'lombok+mp' || options.annotationStyle === 'lombok+jpa') {
    lines.push(...LOMBOK_ON_CLASS)
  }

  if (options.commentToJavaDoc && model.comment) {
    lines.push(`/** ${model.comment} */`)
  }

  lines.push(`public class ${className} implements Serializable {`)
  lines.push(`${indent}private static final long serialVersionUID = 1L;`)
  lines.push('')

  for (const field of model.fields) {
    const anns = fieldAnnotations(field, options)
    for (const ann of anns) {
      if (ann.startsWith('/**')) {
        lines.push(`${indent}${ann}`)
      } else {
        lines.push(`${indent}${ann}`)
      }
    }
    const jt = field.javaType || 'String'
    const fn = safeJavaIdentifier(field.fieldName)
    lines.push(`${indent}private ${jt} ${fn};`)
    lines.push('')
  }

  for (const field of model.fields) {
    const jt = field.javaType || 'String'
    const fn = safeJavaIdentifier(field.fieldName)
    const cap = fn.charAt(0).toUpperCase() + fn.slice(1)
    lines.push(`${indent}public ${jt} get${cap}() {`)
    lines.push(`${indent}${indent}return ${fn};`)
    lines.push(`${indent}}`)
    lines.push('')
    lines.push(`${indent}public void set${cap}(${jt} ${fn}) {`)
    lines.push(`${indent}${indent}this.${fn} = ${fn};`)
    lines.push(`${indent}}`)
    lines.push('')
  }

  lines.push('}')

  return lines.join('\n')
}

export function generateJavaClasses(models: IRTable[], options: DdlOptions): string {
  if (options.multiClassLayout === 'oneFile') {
    const parts: string[] = []
    for (const model of models) {
      parts.push(generateClassCode(model, options))
      parts.push('')
    }
    return parts.join('\n').trim()
  }

  return generateClassCode(models[0], options)
}

export function generateJavaWithImports(models: IRTable[], options: DdlOptions): string {
  return generateJavaClasses(models, options)
}
