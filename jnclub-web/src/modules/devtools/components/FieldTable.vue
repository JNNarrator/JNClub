<script setup lang="ts">
import { computed, ref, h } from 'vue'
import { NDataTable, NInput, NSelect, NSwitch, NButton, NInputNumber, NCollapse, NCollapseItem } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import type { IRModel, IRTable, IRField, IRIndex, FieldRole } from '../utils/ddlTypes'
import type { DdlOptions } from '../utils/ddlOptions'
import { FIELD_ROLE_DEFAULTS } from '../utils/ddlTypes'
import { toCamelCase, toPascalCase, toSnakeCase } from '../utils/naming'

const props = defineProps<{
  model: IRModel
  options: DdlOptions
}>()

const emit = defineEmits<{
  'update:model': [v: IRModel]
}>()

const model = computed({
  get: () => props.model,
  set: (v: IRModel) => emit('update:model', v),
})

function updateTable(i: number, patch: Partial<IRTable>) {
  const t = { ...model.value.tables[i], ...patch }
  const ts = [...model.value.tables]
  ts[i] = t
  model.value = { ...model.value, tables: ts }
}

function updateField(ti: number, fi: number, patch: Partial<IRField>) {
  const f = { ...model.value.tables[ti].fields[fi], ...patch }
  const fields = [...model.value.tables[ti].fields]
  fields[fi] = f
  updateTable(ti, { fields })
}

function removeField(ti: number, fi: number) {
  const fields = model.value.tables[ti].fields.filter((_, i) => i !== fi)
  updateTable(ti, { fields })
}

function addField(ti: number) {
  const fields = [...model.value.tables[ti].fields, {
    columnName: '',
    fieldName: '',
    nameLinked: true,
    javaType: 'String',
    nullable: true,
    primaryKey: false,
    autoIncrement: false,
    unique: false,
    role: 'none',
  } as IRField]
  updateTable(ti, { fields })
}

function updateIndex(ti: number, ii: number, patch: Partial<IRIndex>) {
  const idx = { ...model.value.tables[ti].indexes[ii], ...patch }
  const indexes = [...model.value.tables[ti].indexes]
  indexes[ii] = idx
  updateTable(ti, { indexes })
}

function removeIndex(ti: number, ii: number) {
  const indexes = model.value.tables[ti].indexes.filter((_, i) => i !== ii)
  updateTable(ti, { indexes })
}

function addIndex(ti: number) {
  const indexes = [...model.value.tables[ti].indexes, {
    columns: [{ columnName: '' }],
    unique: false,
  } as IRIndex]
  updateTable(ti, { indexes })
}

function addIndexColumn(ti: number, ii: number) {
  const idx = model.value.tables[ti].indexes[ii]
  const columns = [...idx.columns, { columnName: '' }]
  updateIndex(ti, ii, { columns })
}

function updateIndexColumn(ti: number, ii: number, ci: number, col: string) {
  const columns = [...model.value.tables[ti].indexes[ii].columns]
  columns[ci] = { ...columns[ci], columnName: col }
  updateIndex(ti, ii, { columns })
}

function removeIndexColumn(ti: number, ii: number, ci: number) {
  const columns = model.value.tables[ti].indexes[ii].columns.filter((_, i) => i !== ci)
  updateIndex(ti, ii, { columns })
}

const roleOptions = [
  { label: '无', value: 'none' },
  { label: '主键 ID', value: 'id' },
  { label: '创建时间', value: 'createTime' },
  { label: '更新时间', value: 'updateTime' },
  { label: '逻辑删除', value: 'deleteFlag' },
  { label: '乐观锁', value: 'version' },
  { label: '租户 ID', value: 'tenantId' },
  { label: '创建人', value: 'creator' },
  { label: '更新人', value: 'updater' },
]

const ROLE_JAVA_TYPES: Record<string, string> = {
  id: 'Long',
  createTime: 'LocalDateTime',
  updateTime: 'LocalDateTime',
  deleteFlag: 'Boolean',
  version: 'Integer',
  tenantId: 'Long',
  creator: 'String',
  updater: 'String',
}

const ROLE_COLUMN_NAMES: Record<string, string> = {
  id: 'id',
  createTime: 'create_time',
  updateTime: 'update_time',
  deleteFlag: 'is_deleted',
  version: 'version',
  tenantId: 'tenant_id',
  creator: 'create_by',
  updater: 'update_by',
}

function onRoleChange(ti: number, fi: number, role: FieldRole) {
  const defaults = FIELD_ROLE_DEFAULTS[role]
  const patch: Partial<IRField> = { role }
  if (role !== 'none') {
    patch.javaType = ROLE_JAVA_TYPES[role] || 'String'
    if (defaults.defaults.nullable !== undefined) patch.nullable = defaults.defaults.nullable
    if (defaults.defaults.primaryKey) patch.primaryKey = defaults.defaults.primaryKey
    if (defaults.defaults.defaultValue) patch.defaultValue = defaults.defaults.defaultValue
    if (defaults.defaults.onUpdateCurrentTimestamp) patch.onUpdateCurrentTimestamp = true
    const colName = ROLE_COLUMN_NAMES[role]
    if (colName) {
      patch.columnName = colName
      patch.fieldName = toCamelCase(colName)
    }
  }
  updateField(ti, fi, patch)
}

function onColumnNameChange(ti: number, fi: number, col: string) {
  const f = model.value.tables[ti].fields[fi]
  updateField(ti, fi, {
    columnName: col,
    fieldName: f.nameLinked ? toCamelCase(col) : f.fieldName,
  })
}

function onFieldNameChange(ti: number, fi: number, fn: string) {
  const f = model.value.tables[ti].fields[fi]
  updateField(ti, fi, {
    fieldName: fn,
    columnName: f.nameLinked ? toSnakeCase(fn) : f.columnName,
  })
}

function onTableNameChange(ti: number, name: string) {
  const t = model.value.tables[ti]
  updateTable(ti, {
    tableName: name,
    className: t.nameLinked ? toPascalCase(name) : t.className,
  })
}

function onClassNameChange(ti: number, name: string) {
  const t = model.value.tables[ti]
  updateTable(ti, {
    className: name,
    tableName: t.nameLinked ? toSnakeCase(name) : t.tableName,
  })
}

const selectedTable = ref(0)

const LinkIcon = (linked: boolean) => h('span', {
  style: { cursor: 'pointer', fontSize: '12px', color: `var(${linked ? '--brand' : '--text-3'})` },
  title: linked ? '已关联' : '已解除',
}, linked ? '🔗' : '⛓️‍💥')

function createFieldColumns(ti: number): DataTableColumn<IRField>[] {
  return [
    {
      title: '列名',
      key: 'columnName',
      width: 140,
      render(row, ri) {
        return h(NInput, {
          value: row.columnName,
          size: 'tiny',
          placeholder: 'column_name',
          'onUpdate:value': (v: string) => onColumnNameChange(ti, ri, v),
        })
      },
    },
    {
      title: '字段名',
      key: 'fieldName',
      width: 160,
      render(row, ri) {
        return h('div', { style: 'display:flex;align-items:center;gap:4px' }, [
          h(NInput, {
            value: row.fieldName,
            size: 'tiny',
            placeholder: 'fieldName',
            'onUpdate:value': (v: string) => onFieldNameChange(ti, ri, v),
          }),
          LinkIcon(row.nameLinked),
        ])
      },
    },
    {
      title: 'Java 类型',
      key: 'javaType',
      width: 120,
      render(row, ri) {
        return h(NInput, {
          value: row.javaType,
          size: 'tiny',
          placeholder: 'String',
          'onUpdate:value': (v: string) => updateField(ti, ri, { javaType: v }),
        })
      },
    },
    {
      title: 'DB 类型',
      key: 'rawDbType',
      width: 120,
      render(row, ri) {
        return h(NInput, {
          value: row.rawDbType || '',
          size: 'tiny',
          placeholder: 'VARCHAR(255)',
          'onUpdate:value': (v: string) => updateField(ti, ri, { rawDbType: v || undefined }),
        })
      },
    },
    {
      title: '长度',
      key: 'length',
      width: 70,
      render(row, ri) {
        return h(NInputNumber, {
          value: row.length ?? null,
          size: 'tiny',
          min: 0,
          style: 'width:60px',
          'onUpdate:value': (v: number | null) => updateField(ti, ri, { length: v ?? undefined }),
        })
      },
    },
    {
      title: '非空',
      key: 'nullable',
      width: 55,
      render(row, ri) {
        return h(NSwitch, {
          value: !row.nullable,
          size: 'small',
          'onUpdate:value': (v: boolean) => updateField(ti, ri, { nullable: !v }),
        })
      },
    },
    {
      title: '主键',
      key: 'primaryKey',
      width: 55,
      render(row, ri) {
        return h(NSwitch, {
          value: row.primaryKey,
          size: 'small',
          'onUpdate:value': (v: boolean) => updateField(ti, ri, { primaryKey: v }),
        })
      },
    },
    {
      title: '自增',
      key: 'autoIncrement',
      width: 55,
      render(row, ri) {
        return h(NSwitch, {
          value: row.autoIncrement,
          size: 'small',
          'onUpdate:value': (v: boolean) => updateField(ti, ri, { autoIncrement: v }),
        })
      },
    },
    {
      title: '唯一',
      key: 'unique',
      width: 55,
      render(row, ri) {
        return h(NSwitch, {
          value: row.unique,
          size: 'small',
          'onUpdate:value': (v: boolean) => updateField(ti, ri, { unique: v }),
        })
      },
    },
    {
      title: '默认值',
      key: 'defaultValue',
      width: 110,
      render(row, ri) {
        return h(NInput, {
          value: row.defaultValue ?? '',
          size: 'tiny',
          placeholder: '',
          'onUpdate:value': (v: string) => updateField(ti, ri, { defaultValue: v || undefined }),
        })
      },
    },
    {
      title: '角色',
      key: 'role',
      width: 110,
      render(row, ri) {
        return h(NSelect, {
          value: row.role,
          options: roleOptions,
          size: 'tiny',
          'onUpdate:value': (v: FieldRole) => onRoleChange(ti, ri, v),
        })
      },
    },
    {
      title: '注释',
      key: 'comment',
      width: 140,
      render(row, ri) {
        return h(NInput, {
          value: row.comment ?? '',
          size: 'tiny',
          placeholder: '注释',
          'onUpdate:value': (v: string) => updateField(ti, ri, { comment: v || undefined }),
        })
      },
    },
    {
      title: '',
      key: 'actions',
      width: 40,
      render(_, ri) {
        return h(NButton, { size: 'tiny', text: true, type: 'error', onClick: () => removeField(ti, ri) }, '✕')
      },
    },
  ]
}
</script>

<template>
  <div class="ft-panel" v-if="model.tables.length > 0">
    <div class="ft-table-bar">
      <NSelect
        :value="selectedTable"
        :options="model.tables.map((t, i) => ({ label: t.tableName || `表 ${i + 1}`, value: i }))"
        size="small" style="width:200px"
        @update:value="(v: number) => selectedTable = v"
      />
    </div>

    <template v-for="(table, ti) in model.tables" :key="ti">
      <div v-if="ti === selectedTable" class="ft-table-wrap">
        <NCollapse :default-expanded-names="['fields']">
          <NCollapseItem title="表属性" name="props">
            <div class="ft-props-grid">
              <div class="ft-prop">
                <span class="ft-prop-l">表名</span>
                <NInput :value="table.tableName" size="tiny" placeholder="table_name"
                  @update:value="(v: string) => onTableNameChange(ti, v)" style="width:200px" />
              </div>
              <div class="ft-prop">
                <span class="ft-prop-l">类名</span>
                <div style="display:flex;align-items:center;gap:4px">
                  <NInput :value="table.className" size="tiny" placeholder="ClassName"
                    @update:value="(v: string) => onClassNameChange(ti, v)" style="width:200px" />
                  <span :style="{cursor:'pointer',fontSize:'12px',color: table.nameLinked ? 'var(--brand)' : 'var(--text-3)'}"
                    :title="table.nameLinked ? '已关联' : '已解除'"
                    @click="updateTable(ti, { nameLinked: !table.nameLinked })">
                    {{ table.nameLinked ? '🔗' : '⛓️‍💥' }}
                  </span>
                </div>
              </div>
              <div class="ft-prop">
                <span class="ft-prop-l">注释</span>
                <NInput :value="table.comment ?? ''" size="tiny" placeholder="表注释"
                  @update:value="(v: string) => updateTable(ti, { comment: v || undefined })" style="width:300px" />
              </div>
              <div class="ft-prop">
                <span class="ft-prop-l">引擎</span>
                <NInput :value="table.engine ?? ''" size="tiny" placeholder="InnoDB"
                  @update:value="(v: string) => updateTable(ti, { engine: v || undefined })" style="width:120px" />
              </div>
              <div class="ft-prop">
                <span class="ft-prop-l">字符集</span>
                <NInput :value="table.charset ?? ''" size="tiny" placeholder="utf8mb4"
                  @update:value="(v: string) => updateTable(ti, { charset: v || undefined })" style="width:120px" />
              </div>
              <div class="ft-prop">
                <span class="ft-prop-l">排序规则</span>
                <NInput :value="table.collation ?? ''" size="tiny" placeholder="utf8mb4_unicode_ci"
                  @update:value="(v: string) => updateTable(ti, { collation: v || undefined })" style="width:180px" />
              </div>
            </div>
          </NCollapseItem>
        </NCollapse>

        <div class="ft-fields-bar">
          <span class="ft-fields-title">字段列表 ({{ table.fields.length }})</span>
          <NButton size="tiny" tertiary @click="addField(ti)">+ 添加字段</NButton>
        </div>

        <div class="ft-table-scroll">
          <NDataTable
            :columns="createFieldColumns(ti)"
            :data="table.fields"
            :row-key="undefined"
            size="small"
            striped
            virtual-scroll
            :max-height="320"
            :scroll-x="1300"
          />
        </div>

        <NCollapse class="ft-idx-collapse">
          <NCollapseItem title="索引 ({{ table.indexes.length }})" name="indexes">
            <div v-for="(idx, ii) in table.indexes" :key="ii" class="ft-idx-row">
              <div class="ft-idx-header">
                <NInput :value="idx.name ?? ''" size="tiny" placeholder="索引名"
                  @update:value="(v: string) => updateIndex(ti, ii, { name: v || undefined })" style="width:150px" />
                <label style="display:flex;align-items:center;gap:4px;font-size:12px">
                  <NSwitch :value="idx.unique" size="small" @update:value="(v: boolean) => updateIndex(ti, ii, { unique: v })" /> 唯一
                </label>
                <NSelect :value="idx.type ?? 'btree'" size="tiny" style="width:100px"
                  :options="[{label:'BTREE',value:'btree'},{label:'HASH',value:'hash'},{label:'FULLTEXT',value:'fulltext'}]"
                  @update:value="(v: 'btree'|'hash'|'fulltext') => updateIndex(ti, ii, { type: v })" />
                <NButton size="tiny" text type="error" @click="removeIndex(ti, ii)">✕</NButton>
              </div>
              <div class="ft-idx-cols">
                <div v-for="(col, ci) in idx.columns" :key="ci" class="ft-idx-col">
                  <NInput :value="col.columnName" size="tiny" placeholder="列名"
                    @update:value="(v: string) => updateIndexColumn(ti, ii, ci, v)" style="width:140px" />
                  <NSelect :value="col.order ?? 'asc'" size="tiny" style="width:80px"
                    :options="[{label:'ASC',value:'asc'},{label:'DESC',value:'desc'}]"
                    @update:value="(v: 'asc'|'desc') => {
                      const cols = [...idx.columns]; cols[ci] = { ...cols[ci], order: v }
                      updateIndex(ti, ii, { columns: cols })
                    }" />
                  <NButton size="tiny" text type="error" @click="removeIndexColumn(ti, ii, ci)">✕</NButton>
                </div>
                <NButton size="tiny" tertiary @click="addIndexColumn(ti, ii)">+ 添加列</NButton>
              </div>
            </div>
            <NButton size="tiny" tertiary @click="addIndex(ti)" style="margin-top:4px">+ 添加索引</NButton>
          </NCollapseItem>
        </NCollapse>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ft-panel { display:flex; flex-direction:column; min-height:0; gap:6px; }
.ft-table-bar { flex-shrink:0; }
.ft-table-wrap { display:flex; flex-direction:column; min-height:0; gap:6px; }
.ft-props-grid { display:flex; flex-wrap:wrap; gap:8px 16px; }
.ft-prop { display:flex; align-items:center; gap:6px; }
.ft-prop-l { font-size:12px; color:var(--text-2); white-space:nowrap; }
.ft-fields-bar { display:flex; align-items:center; justify-content:space-between; flex-shrink:0; }
.ft-fields-title { font-size:12px; font-weight:600; color:var(--text-2); }
.ft-table-scroll { flex:1; min-height:0; overflow:auto; border:1px solid var(--border); border-radius:6px; }
.ft-idx-collapse { flex-shrink:0; }
.ft-idx-row { border:1px solid var(--border); border-radius:6px; padding:8px; margin-bottom:6px; }
.ft-idx-header { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.ft-idx-cols { display:flex; flex-direction:column; gap:4px; padding-left:8px; }
.ft-idx-col { display:flex; align-items:center; gap:6px; }
</style>
