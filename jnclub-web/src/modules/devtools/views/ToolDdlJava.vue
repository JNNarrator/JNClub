<script setup lang="ts">
import { defineOptions } from 'vue'
import { computed, ref, shallowRef, watch } from 'vue'
import { NSelect, NSwitch, NButton, NInput, NCollapse, NCollapseItem, useMessage } from 'naive-ui'
import { useToolDraft } from '../composables/useToolDraft'
import CodeEditor from '../components/CodeEditor.vue'
import FieldTable from '../components/FieldTable.vue'
import type { DdlDialect, AnnotationStyle, DdlDirection, MultiClassLayout, IRModel } from '../utils/ddlTypes'
import type { DdlOptions as DDOpts } from '../utils/ddlOptions'
import { DEFAULT_DDL_OPTIONS } from '../utils/ddlOptions'
import { parseDdl } from '../utils/ddlParser'
import { parseJavaToModel } from '../utils/javaEntityParser'
import { generateJavaWithImports } from '../utils/ddlToJava'
import { renderDdl } from '../utils/ddlRenderer'

defineOptions({ name: 'ToolDdlJava' })

interface DdlJavaDraft {
  inputText: string
  direction: DdlDirection
  opts: DDOpts
}

const msg = useMessage()

const { state: draft, resetDraft } = useToolDraft<DdlJavaDraft>('ddl-java', {
  inputText: '',
  direction: 'ddl2java',
  opts: { ...DEFAULT_DDL_OPTIONS },
}, {
  validate: (raw): raw is DdlJavaDraft => {
    return typeof raw === 'object' && raw !== null
      && typeof (raw as any).inputText === 'string'
      && typeof (raw as any).direction === 'string'
      && typeof (raw as any).opts === 'object'
  },
})

const inputText = computed({
  get: () => stripHtml(draft.value.inputText),
  set: (v: string) => { draft.value = { ...draft.value, inputText: stripHtml(v) } },
})

const direction = computed({
  get: () => draft.value.direction,
  set: (v: DdlDirection) => { draft.value = { ...draft.value, direction: v } },
})

const opts = computed({
  get: () => draft.value.opts,
  set: (v: DDOpts) => { draft.value = { ...draft.value, opts: v } },
})

function stripHtml(s: string): string {
  return s.replace(/<[^>]*>/g, '').replace(/"hl-\w+"?>/g, '').replace(/&quot;hl-\w+&quot;>/g, '')
}

const error = shallowRef<string | null>(null)
const busy = ref(false)

const irModel = ref<IRModel>({ tables: [] })

const outputText = shallowRef('')

const inputLang = computed(() => direction.value === 'ddl2java' ? 'sql' as const : 'java' as const)
const outputLang = computed(() => direction.value === 'ddl2java' ? 'java' as const : 'sql' as const)

const inputPlaceholder = computed(() => direction.value === 'ddl2java'
  ? '粘贴 CREATE TABLE DDL…'
  : '粘贴 Java 实体类源码…')

const directionLabel = computed(() => direction.value === 'ddl2java' ? 'DDL → Java' : 'Java → DDL')

const targetDialect = computed({
  get: () => opts.value.targetDialect,
  set: (v: DdlDialect) => opts.value = { ...opts.value, targetDialect: v },
})

const annotationStyle = computed({
  get: () => opts.value.annotationStyle,
  set: (v: AnnotationStyle) => opts.value = { ...opts.value, annotationStyle: v },
})

const multiClassLayout = computed({
  get: () => opts.value.multiClassLayout,
  set: (v: MultiClassLayout) => opts.value = { ...opts.value, multiClassLayout: v },
})

const packageName = computed({
  get: () => opts.value.packageName,
  set: (v: string) => opts.value = { ...opts.value, packageName: v },
})

const livePreview = computed({
  get: () => opts.value.livePreview,
  set: (v: boolean) => opts.value = { ...opts.value, livePreview: v },
})

const quoteIdentifiers = computed({
  get: () => opts.value.quoteIdentifiers,
  set: (v: boolean) => opts.value = { ...opts.value, quoteIdentifiers: v },
})

const emitIndexes = computed({
  get: () => opts.value.emitIndexes,
  set: (v: boolean) => opts.value = { ...opts.value, emitIndexes: v },
})

const commentToJavaDoc = computed({
  get: () => opts.value.commentToJavaDoc,
  set: (v: boolean) => opts.value = { ...opts.value, commentToJavaDoc: v },
})

const pgAutoIncrementStyle = computed({
  get: () => opts.value.pgAutoIncrementStyle,
  set: (v: 'identity' | 'serial') => opts.value = { ...opts.value, pgAutoIncrementStyle: v },
})

const defaultDecimalPS = computed({
  get: () => opts.value.defaultDecimalPS,
  set: (v: string) => opts.value = { ...opts.value, defaultDecimalPS: v },
})

const fieldNaming = computed({
  get: () => opts.value.fieldNaming,
  set: (v: 'camelCase' | 'snakeCase' | 'PascalCase') => opts.value = { ...opts.value, fieldNaming: v },
})

const indentSize = computed({
  get: () => opts.value.indentSize,
  set: (v: number) => opts.value = { ...opts.value, indentSize: v },
})

const inputCollapsed = ref(true)
const converting = ref(false)

function toggleInput() { inputCollapsed.value = !inputCollapsed.value }

const inputStats = computed(() => {
  const txt = inputText.value
  const chars = txt.length
  const lines = (txt.match(/\n/g) || []).length + 1
  const tables = irModel.value.tables.length
  const fields = irModel.value.tables.reduce((s, t) => s + t.fields.length, 0)
  return { chars, lines, tables, fields }
})

watch(direction, () => {
  inputCollapsed.value = direction.value === 'java2ddl'
}, { immediate: true })

function doConvert() {
  if (busy.value) return
  busy.value = true
  error.value = null
  outputText.value = ''

  try {
    const text = stripHtml(inputText.value).trim()
    if (!text) {
      irModel.value = { tables: [] }
      outputText.value = ''
      busy.value = false
      return
    }

    if (direction.value === 'ddl2java') {
      const model = parseDdl(text, targetDialect.value)
      irModel.value = model
      if (model.tables.length > 0) {
        outputText.value = generateJavaWithImports(model.tables, opts.value)
      }
    } else {
      const model = parseJavaToModel(text)
      irModel.value = model
      if (model.tables.length > 0) {
        outputText.value = renderDdl(model.tables, targetDialect.value, opts.value)
      }
    }
  } catch (e) {
    error.value = `转换异常：${e instanceof Error ? e.message : String(e)}`
    outputText.value = ''
    if (outputText.value) {
      converting.value = true
      setTimeout(() => { converting.value = false }, 400)
    }
  } finally {
    busy.value = false
  }
}

function onIrModelChange(model: IRModel) {
  irModel.value = model
  try {
    if (direction.value === 'ddl2java') {
      outputText.value = generateJavaWithImports(model.tables, opts.value)
    } else {
      outputText.value = renderDdl(model.tables, targetDialect.value, opts.value)
    }
  } catch (e) {
    error.value = `输出渲染异常：${e instanceof Error ? e.message : String(e)}`
  }
}

function scheduleConvert() {
  if (!opts.value.livePreview) return
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(doConvert, 600)
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch([inputText, direction, annotationStyle, packageName, targetDialect,
       multiClassLayout, quoteIdentifiers, emitIndexes, commentToJavaDoc,
       pgAutoIncrementStyle, defaultDecimalPS, fieldNaming, indentSize],
  scheduleConvert, { flush: 'post', deep: false })

function swapDirection() {
  direction.value = direction.value === 'ddl2java' ? 'java2ddl' : 'ddl2java'
  inputText.value = outputText.value
  outputText.value = ''
  error.value = null
  doConvert()
}

function clearAll() {
  inputText.value = ''
  outputText.value = ''
  error.value = null
  irModel.value = { tables: [] }
  resetDraft()
}

function copyOutput() {
  if (!outputText.value) { msg.info('暂无输出可复制'); return }
  navigator.clipboard.writeText(outputText.value).then(() => msg.success('已复制')).catch(() => msg.error('复制失败'))
}

function fillDdlSample() {
  inputText.value = `CREATE TABLE \`user\` (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  username VARCHAR(50) NOT NULL COMMENT '用户名',
  email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  password VARCHAR(255) NOT NULL COMMENT '密码',
  age INT DEFAULT 0 COMMENT '年龄',
  is_deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
  version INT DEFAULT 0 COMMENT '乐观锁',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
  create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';`
}

function fillJavaSample() {
  inputText.value = `import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String password;

    private Integer age;

    @TableLogic
    private Boolean isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long tenantId;

    private String createBy;

    private String updateBy;
}`
}
</script>

<template>
  <div class="dj-panel">
    <div class="dj-toolbar">
      <div class="dj-toolbar-left">
        <span class="dj-title">DDL ⇄ Java</span>
        <NButton size="small" :type="direction === 'ddl2java' ? 'primary' : 'default'"
          @click="direction = 'ddl2java'; scheduleConvert()">DDL → Java</NButton>
        <NButton size="small" :type="direction === 'java2ddl' ? 'primary' : 'default'"
          @click="direction = 'java2ddl'; scheduleConvert()">Java → DDL</NButton>
      </div>
      <div class="dj-toolbar-right">
        <label class="dj-toggle"><NSwitch v-model:value="livePreview" size="small" /><span class="dj-toggle-l">自动</span></label>
        <NButton size="small" tertiary @click="fillDdlSample">DDL 示例</NButton>
        <NButton size="small" tertiary @click="fillJavaSample">Java 示例</NButton>
        <NButton size="small" tertiary @click="swapDirection">⇄ 交换</NButton>
        <NButton size="small" tertiary @click="clearAll">清空</NButton>
        <NButton size="small" type="primary" :disabled="busy" @click="doConvert">转换</NButton>
      </div>
    </div>

    <NCollapse class="dj-options">
      <NCollapseItem title="选项" name="opts">
        <div class="dj-opts-grid">
          <div class="dj-opt">
            <span class="dj-opt-l">目标方言</span>
            <NSelect v-model:value="targetDialect" size="tiny" style="width:140px"
              :options="[{label:'MySQL',value:'mysql'},{label:'PostgreSQL',value:'postgresql'},{label:'OceanBase',value:'oceanbase'}]" />
          </div>
          <div class="dj-opt" v-if="direction === 'ddl2java'">
            <span class="dj-opt-l">注解风格</span>
            <NSelect v-model:value="annotationStyle" size="tiny" style="width:140px"
              :options="[{label:'无',value:'none'},{label:'Lombok',value:'lombok'},{label:'MyBatis-Plus',value:'mybatis-plus'},{label:'Lombok+JPA',value:'lombok+jpa'},{label:'Lombok+MP',value:'lombok+mp'}]" />
          </div>
          <div class="dj-opt" v-if="direction === 'ddl2java'">
            <span class="dj-opt-l">多类布局</span>
            <NSelect v-model:value="multiClassLayout" size="tiny" style="width:140px"
              :options="[{label:'同文件',value:'oneFile'},{label:'分表区块',value:'perTableBlock'}]" />
          </div>
          <div class="dj-opt" v-if="direction === 'ddl2java'">
            <span class="dj-opt-l">包名</span>
            <NInput v-model:value="packageName" size="tiny" placeholder="com.example" style="width:160px" />
          </div>
          <div class="dj-opt">
            <span class="dj-opt-l">引号标识符</span>
            <label class="dj-toggle"><NSwitch v-model:value="quoteIdentifiers" size="small" />{{ quoteIdentifiers ? '开' : '关' }}</label>
          </div>
          <div class="dj-opt">
            <span class="dj-opt-l">生成索引</span>
            <label class="dj-toggle"><NSwitch v-model:value="emitIndexes" size="small" />{{ emitIndexes ? '开' : '关' }}</label>
          </div>
          <div class="dj-opt" v-if="direction === 'java2ddl'">
            <span class="dj-opt-l">注释→JavaDoc</span>
            <label class="dj-toggle"><NSwitch v-model:value="commentToJavaDoc" size="small" />{{ commentToJavaDoc ? '开' : '关' }}</label>
          </div>
          <div class="dj-opt" v-if="targetDialect === 'postgresql'">
            <span class="dj-opt-l">PG 自增风格</span>
            <NSelect v-model:value="pgAutoIncrementStyle" size="tiny" style="width:120px"
              :options="[{label:'Identity',value:'identity'},{label:'Serial',value:'serial'}]" />
          </div>
          <div class="dj-opt">
            <span class="dj-opt-l">Decimal 精度</span>
            <NInput v-model:value="defaultDecimalPS" size="tiny" placeholder="19,4" style="width:80px" />
          </div>
          <div class="dj-opt">
            <span class="dj-opt-l">缩进</span>
            <NSelect v-model:value="indentSize" size="tiny" style="width:70px"
              :options="[{label:'2',value:2},{label:'4',value:4}]" />
          </div>
        </div>
      </NCollapseItem>
    </NCollapse>

    <div v-if="error" class="dj-error">{{ error }}</div>

    <div class="dj-input-section" :class="{ collapsed: inputCollapsed }">
      <div class="dj-input-header" @click="toggleInput">
        <div class="dj-input-summary">
          <span class="dj-input-title">{{ directionLabel }} 输入</span>
          <span class="dj-input-stats">{{ inputStats.chars }} 字符 · {{ inputStats.lines }} 行 · {{ inputStats.tables }} 表 · {{ inputStats.fields }} 字段</span>
        </div>
        <span class="dj-input-toggle">{{ inputCollapsed ? '展开' : '折叠' }}</span>
      </div>
      <div class="dj-input-body">
        <CodeEditor v-model="inputText" :language="inputLang" :placeholder="inputPlaceholder" />
      </div>
    </div>

    <div class="dj-body">
      <div class="dj-table-area">
        <div class="dj-col-h">
          <span class="dj-col-title">字段编辑器 ({{ irModel.tables.length }} 表)</span>
          <NButton size="tiny" tertiary :disabled="!outputText" @click="copyOutput">复制输出</NButton>
        </div>
        <FieldTable :model="irModel" :options="opts" @update:model="onIrModelChange" />
      </div>
      <div class="dj-output-area" :class="{ converting }">
        <div class="dj-col-h">
          <span class="dj-col-title">输出</span>
        </div>
        <CodeEditor :model-value="outputText" :language="outputLang" readonly :placeholder="outputText ? '' : '转换结果…'" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.dj-panel {
  padding: 12px 20px; height: 100%; display: flex; flex-direction: column; gap: 6px; overflow-y: auto;
  scroll-padding-top: 52px;
}

.dj-toolbar {
  position: sticky; top: 0; z-index: 10;
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  flex-shrink: 0; flex-wrap: wrap;
  padding: 4px 0;
  background: var(--bg-page);
}
.dj-toolbar-left, .dj-toolbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dj-title { font-size: 16px; font-weight: 700; color: var(--text-2); }

.dj-toggle { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; }
.dj-toggle-l { font-size: 12px; color: var(--text-2); user-select: none; }

.dj-options { flex-shrink: 0; }
.dj-options :deep(.n-collapse-item__header) { font-size: 13px; font-weight: 600; color: var(--text-2); padding: 4px 0; }
.dj-options :deep(.n-collapse-item__content) { padding-top: 6px; }
.dj-opts-grid { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.dj-opt { display: flex; align-items: center; gap: 6px; }
.dj-opt-l { font-size: 12px; color: var(--text-2); white-space: nowrap; }

.dj-error { font-size: 12px; color: var(--danger); padding: 6px 10px; background: color-mix(in srgb, var(--danger) 8%, transparent); border-radius: 6px; flex-shrink: 0; white-space: pre-wrap; }

/* Collapsible input section */
.dj-input-section {
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-card);
  transition: all 200ms ease;
  overflow: hidden;
}
.dj-input-section.collapsed .dj-input-body {
  max-height: 0;
  opacity: 0;
  padding: 0 12px;
  margin: 0;
}
.dj-input-body {
  max-height: 30vh;
  opacity: 1;
  overflow: hidden;
  transition: max-height 200ms ease, opacity 150ms ease;
  display: flex; flex-direction: column;
}
.dj-input-body > :deep(.editor-wrap) {
  max-height: 30vh;
}
.dj-input-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
}
.dj-input-header:hover { background: var(--hover-bg); }
.dj-input-summary { display: flex; align-items: center; gap: 10px; }
.dj-input-title { font-size: 12px; font-weight: 600; color: var(--text-2); }
.dj-input-stats { font-size: 11px; color: var(--text-2); }
.dj-input-toggle { font-size: 11px; color: var(--brand); }

/* Main body: table + output side by side on wide screens, stacked on narrow */
.dj-body {
  flex: 1; min-height: 0;
  display: grid;
  grid-template-columns: 6fr 4fr;
  gap: 10px;
  overflow: hidden;
}
@media (max-width: 1099px) {
  .dj-body {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr auto;
  }
}

.dj-table-area {
  display: flex; flex-direction: column; min-height: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px;
  background: var(--bg-card);
}

.dj-output-area {
  display: flex; flex-direction: column; min-height: 0;
  overflow: hidden;
  transition: opacity 300ms ease;
}
.dj-output-area.converting {
  opacity: 0.5;
}
@media (max-width: 1099px) {
  .dj-output-area {
    position: sticky; bottom: 0;
    max-height: 28vh;
    background: var(--bg-page);
    border-top: 1px solid var(--border);
    padding-top: 4px;
  }
}

.dj-col-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; flex-shrink: 0; }
.dj-col-title { font-size: 12px; font-weight: 600; color: var(--text-2); }
</style>
