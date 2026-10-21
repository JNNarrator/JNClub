<script setup lang="ts">
/* ─── JSON ⇄ JavaBean 互转工具 ───
 * 方向 A：JSON → Java 类代码（类型推断 + 四框架注解 + Lombok/手写 + 内部类）
 * 方向 B：Java 类源码 → 示例 JSON（轻量解析 + 类型→示例值 + 循环引用截断）
 */

import { defineOptions } from 'vue'
import { computed, ref, watch, shallowRef } from 'vue'
import { NSelect, NSwitch, NButton, NInput, NCollapse, NCollapseItem, useMessage } from 'naive-ui'
import { useToolDraft } from '../composables/useToolDraft'
import CodeEditor from '../components/CodeEditor.vue'
import {
  DEFAULT_OPTIONS, type JsonJavabeanOptions,
  type SerializationFramework, type NumberStrategy, type NullStrategy,
  type FieldNaming, type NestedClassMode, type ExampleStyle,
  type DateFormat, type OutputKeyStyle,
  lightFormatJava,
} from '../utils/naming'
import { jsonToJava, type GenOptions } from '../utils/jsonToJava'
import { javaToJson, type JavaToJsonOptions } from '../utils/javaToJson'

defineOptions({ name: 'JsonJavabean' })

function stripHtml(s: string): string {
  return s
    .replace(/<[^>]*>/g, '')                // full tags: <span class="hl-...">, </span>
    .replace(/"hl-\w+"?>/g, '')             // attribute remnant: "hl-keyword"> or "hl-keyword">
    .replace(/&quot;hl-\w+&quot;>/g, '')    // entity-escaped: &quot;hl-keyword&quot;>
}

interface JsonJavabeanDraft {
  jsonText: string
  javaText: string
  direction: 'json2java' | 'java2json'
  opts: JsonJavabeanOptions
}

const msg = useMessage()

const { state: draft, resetDraft } = useToolDraft<JsonJavabeanDraft>('json-javabean', {
  jsonText: '',
  javaText: '',
  direction: 'json2java',
  opts: { ...DEFAULT_OPTIONS },
}, {
  validate: (raw): raw is JsonJavabeanDraft => {
    return typeof raw === 'object' && raw !== null
      && typeof (raw as any).jsonText === 'string'
      && typeof (raw as any).javaText === 'string'
      && typeof (raw as any).direction === 'string'
      && typeof (raw as any).opts === 'object'
  },
})

/* ─── 双向绑定 ─── */
const jsonText = computed({
  get: () => stripHtml(draft.value.jsonText),
  set: (v: string) => { draft.value = { ...draft.value, jsonText: stripHtml(v) } },
})
const javaText = computed({
  get: () => stripHtml(draft.value.javaText),
  set: (v: string) => { draft.value = { ...draft.value, javaText: stripHtml(v) } },
})
const direction = computed({
  get: () => draft.value.direction,
  set: (v: 'json2java' | 'java2json') => { draft.value = { ...draft.value, direction: v } },
})
const opts = computed({
  get: () => draft.value.opts,
  set: (v: JsonJavabeanOptions) => { draft.value = { ...draft.value, opts: v } },
})

/* ─── 输出 ─── */
const output = shallowRef('')
const error = shallowRef<string | null>(null)
const busy = ref(false)

/* ─── 选项绑定 ─── */
const framework = computed({ get: () => opts.value.framework, set: (v: SerializationFramework) => opts.value = { ...opts.value, framework: v } })
const indentSize = computed({ get: () => opts.value.indentSize, set: (v: number) => opts.value = { ...opts.value, indentSize: v } })
const numberStrategy = computed({ get: () => opts.value.numberStrategy, set: (v: NumberStrategy) => opts.value = { ...opts.value, numberStrategy: v } })
const nullStrategy = computed({ get: () => opts.value.nullStrategy, set: (v: NullStrategy) => opts.value = { ...opts.value, nullStrategy: v } })
const fieldNaming = computed({ get: () => opts.value.fieldNaming, set: (v: FieldNaming) => opts.value = { ...opts.value, fieldNaming: v } })
const nestedMode = computed({ get: () => opts.value.nestedMode, set: (v: NestedClassMode) => opts.value = { ...opts.value, nestedMode: v } })
const rootClassName = computed({ get: () => opts.value.rootClassName, set: (v: string) => opts.value = { ...opts.value, rootClassName: v } })
const packageName = computed({ get: () => opts.value.packageName, set: (v: string) => opts.value = { ...opts.value, packageName: v } })
const lombok = computed({ get: () => opts.value.lombok, set: (v: boolean) => opts.value = { ...opts.value, lombok: v } })
const lombokBuilder = computed({ get: () => opts.value.lombokBuilder, set: (v: boolean) => opts.value = { ...opts.value, lombokBuilder: v } })
const exampleStyle = computed({ get: () => opts.value.exampleStyle, set: (v: ExampleStyle) => opts.value = { ...opts.value, exampleStyle: v } })
const dateFormat = computed({ get: () => opts.value.dateFormat, set: (v: DateFormat) => opts.value = { ...opts.value, dateFormat: v } })
const outputKeyStyle = computed({ get: () => opts.value.outputKeyStyle, set: (v: OutputKeyStyle) => opts.value = { ...opts.value, outputKeyStyle: v } })
const autoConvert = computed({ get: () => opts.value.autoConvert, set: (v: boolean) => opts.value = { ...opts.value, autoConvert: v } })

/* ─── 选项作用于标注 ─── */
const OPT_META: Record<string, { label: string; affect: string; options: { label: string; value: string }[] | null }> = {
  framework: { label: '序列化框架', affect: '[双向]', options: [{ label: 'Jackson', value: 'jackson' }, { label: 'Gson', value: 'gson' }, { label: 'Fastjson', value: 'fastjson' }, { label: 'None', value: 'none' }] },
  indentSize: { label: '缩进宽度', affect: '[双向]', options: [{ label: '2', value: '2' }, { label: '4', value: '4' }] },
  numberStrategy: { label: '数字策略', affect: '[A] JSON→Java', options: [{ label: 'Auto（自动推断）', value: 'auto' }, { label: 'BigDecimal（全转小数为BigDecimal）', value: 'bigdecimal' }] },
  nullStrategy: { label: 'Null 兜底', affect: '[A] JSON→Java', options: [{ label: 'Object', value: 'object' }, { label: 'String', value: 'string' }] },
  fieldNaming: { label: '字段命名', affect: '[A] JSON→Java', options: [{ label: 'camelCase', value: 'camelCase' }, { label: 'Keep（保持原样）', value: 'keep' }] },
  nestedMode: { label: '嵌套类形态', affect: '[A] JSON→Java', options: [{ label: '内部类（static inner）', value: 'inner' }, { label: '独立类（separate files）', value: 'separate' }] },
  exampleStyle: { label: '示例风格', affect: '[B] Java→JSON', options: [{ label: '占位（placeholder）', value: 'placeholder' }, { label: '空值（empty）', value: 'empty' }, { label: '类型名（typename）', value: 'typename' }] },
  dateFormat: { label: '日期格式', affect: '[B] Java→JSON', options: [{ label: 'ISO 字符串', value: 'iso' }, { label: '时间戳', value: 'epoch' }] },
  outputKeyStyle: { label: '输出 Key 风格', affect: '[B] Java→JSON', options: [{ label: '保持字段名', value: 'keep' }, { label: 'camelCase', value: 'camelCase' }, { label: 'snake_case', value: 'snake_case' }] },
}

/* ─── 执行转换 ─── */
let debounceTimer: ReturnType<typeof setTimeout> | null = null

async function doConvert() {
  if (busy.value) return
  busy.value = true
  error.value = null

  // Belt-and-suspenders: sanitize inputs before conversion
  jsonText.value = stripHtml(jsonText.value)
  javaText.value = stripHtml(javaText.value)

  try {
    if (direction.value === 'json2java') {
      const jsonInput = jsonText.value.trim()
      if (!jsonInput) { output.value = ''; error.value = null; busy.value = false; return }

      const genOpts: GenOptions = {
        framework: framework.value,
        indentSize: indentSize.value,
        numberStrategy: numberStrategy.value,
        nullStrategy: nullStrategy.value,
        fieldNaming: fieldNaming.value,
        nestedMode: nestedMode.value,
        rootClassName: rootClassName.value || 'Root',
        packageName: packageName.value,
        lombok: lombok.value,
        lombokBuilder: lombokBuilder.value,
      }
      const result = jsonToJava(jsonInput, genOpts)

      if (result.ok) {
        output.value = result.code
      } else {
        error.value = result.message
        output.value = ''
      }
    } else {
      const javaInput = javaText.value.trim()
      if (!javaInput) { output.value = ''; error.value = null; busy.value = false; return }

      const jsonOpts: JavaToJsonOptions = {
        exampleStyle: exampleStyle.value,
        dateFormat: dateFormat.value,
        outputKeyStyle: outputKeyStyle.value,
        indentSize: indentSize.value,
      }
      const result = javaToJson(javaInput, jsonOpts)

      if (result.ok) {
        output.value = result.json
      } else {
        error.value = result.message
        output.value = ''
      }
    }
  } catch (e) {
    error.value = `转换异常：${e instanceof Error ? e.message : String(e)}`
    output.value = ''
  } finally {
    busy.value = false
  }
}

function scheduleConvert() {
  if (!autoConvert.value) return
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(doConvert, 600)
}

function manualConvert() {
  if (debounceTimer) clearTimeout(debounceTimer)
  doConvert()
}

/* ─── Auto-convert watch ─── */
watch([jsonText, javaText, direction, framework, indentSize, numberStrategy,
       nullStrategy, fieldNaming, nestedMode, rootClassName, packageName,
       lombok, lombokBuilder, exampleStyle, dateFormat, outputKeyStyle], scheduleConvert, { flush: 'post', deep: false })

/* ─── 操作 ─── */

function swapContent() {
  const tmpJ = jsonText.value
  jsonText.value = javaText.value
  javaText.value = tmpJ
  output.value = ''
  error.value = null
  direction.value = direction.value === 'json2java' ? 'java2json' : 'json2java'
  manualConvert()
}

function clearAll() {
  jsonText.value = ''
  javaText.value = ''
  output.value = ''
  error.value = null
  resetDraft()
}

function formatJson() {
  if (direction.value !== 'json2java') {
    msg.info('当前方向 JSON 面板为只读输出，不可格式化')
    return
  }
  try {
    const parsed = JSON.parse(jsonText.value)
    jsonText.value = JSON.stringify(parsed, null, indentSize.value)
    msg.success('JSON 已格式化')
  } catch (e) {
    msg.warning(`JSON 格式化失败：${e instanceof Error ? e.message : String(e)}`)
  }
}

function formatJava() {
  if (direction.value !== 'java2json') {
    msg.info('当前方向 Java 面板为只读输出，不可格式化')
    return
  }
  if (!javaText.value) { msg.info('Java 源码为空'); return }
  javaText.value = lightFormatJava(javaText.value, indentSize.value)
  msg.info('已执行轻量缩进规整（非完整 java-format）')
}

function copyOutput() {
  if (!output.value) { msg.info('暂无输出可复制'); return }
  navigator.clipboard.writeText(output.value).then(() => msg.success('已复制')).catch(() => msg.error('复制失败'))
}

/* ─── 填充示例 ─── */
function fillJsonSample() {
  jsonText.value = JSON.stringify({
    name: 'jnclub',
    version: '0.1.0',
    description: 'Desktop dev toolbox',
    tags: ['json', 'java', 'pojo'],
    isOpen: true,
    count: 42,
    metadata: {
      createdAt: '2024-01-15',
      score: 9.5,
      maintainer: null,
    },
  }, null, 2)
}

function fillJavaSample() {
  javaText.value = `import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @JsonProperty("user_name")
    private String userName;
    private int age;
    private String email;
    private boolean active;
    private List<String> tags;
    private Address address;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String zipCode;
        private Location location;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private double lat;
        private double lng;
    }
}`
}
</script>

<template>
  <div class="jb-panel">
    <!-- 工具条 -->
    <div class="jb-toolbar">
      <div class="jb-toolbar-left">
        <span class="jb-title">JSON ⇄ JavaBean</span>
        <NButton size="small" :type="direction === 'json2java' ? 'primary' : 'default'"
          @click="direction = 'json2java'; manualConvert()">➜ JSON → Java</NButton>
        <NButton size="small" :type="direction === 'java2json' ? 'primary' : 'default'"
          @click="direction = 'java2json'; manualConvert()">➜ Java → JSON</NButton>
      </div>
      <div class="jb-toolbar-right">
        <label class="jb-toggle"><NSwitch v-model:value="autoConvert" size="small" /><span class="jb-toggle-l">自动转换</span></label>
        <NButton size="small" tertiary @click="formatJson">格式化 JSON</NButton>
        <NButton size="small" tertiary @click="formatJava" title="轻量缩进规整，非完整 java-format">格式化 Java</NButton>
        <NButton size="small" tertiary @click="fillJsonSample">JSON 示例</NButton>
        <NButton size="small" tertiary @click="fillJavaSample">Java 示例</NButton>
        <NButton size="small" tertiary @click="swapContent" title="交换两栏内容">⇄ 交换</NButton>
        <NButton size="small" tertiary @click="clearAll">清空</NButton>
        <NButton size="small" type="primary" :disabled="busy" @click="manualConvert">转换</NButton>
      </div>
    </div>

    <!-- 选项面板（可折叠） -->
    <NCollapse class="jb-options" :default-expanded-names="['opts']">
      <NCollapseItem title="选项" name="opts">
        <div class="jb-opts-grid">
          <div class="jb-opt" v-for="(meta, key) in OPT_META" :key="key">
            <span class="jb-opt-l">
              <span class="jb-opt-affect">{{ meta.affect }}</span>
              {{ meta.label }}
            </span>
            <template v-if="key === 'framework'">
              <NSelect v-model:value="framework" :options="meta.options!" size="tiny" style="width:120px" />
            </template>
            <template v-else-if="key === 'indentSize'">
              <NSelect v-model:value="indentSize" :options="[{ label:'2', value:2 }, { label:'4', value:4 }]" size="tiny" style="width:80px" />
            </template>
            <template v-else-if="key === 'numberStrategy'">
              <NSelect v-model:value="numberStrategy" :options="meta.options!" size="tiny" style="width:180px" />
            </template>
            <template v-else-if="key === 'nullStrategy'">
              <NSelect v-model:value="nullStrategy" :options="meta.options!" size="tiny" style="width:120px" />
            </template>
            <template v-else-if="key === 'fieldNaming'">
              <NSelect v-model:value="fieldNaming" :options="meta.options!" size="tiny" style="width:140px" />
            </template>
            <template v-else-if="key === 'nestedMode'">
              <NSelect v-model:value="nestedMode" :options="meta.options!" size="tiny" style="width:180px" />
            </template>
            <template v-else-if="key === 'exampleStyle'">
              <NSelect v-model:value="exampleStyle" :options="meta.options!" size="tiny" style="width:160px" />
            </template>
            <template v-else-if="key === 'dateFormat'">
              <NSelect v-model:value="dateFormat" :options="meta.options!" size="tiny" style="width:140px" />
            </template>
            <template v-else-if="key === 'outputKeyStyle'">
              <NSelect v-model:value="outputKeyStyle" :options="meta.options!" size="tiny" style="width:140px" />
            </template>
          </div>
          <!-- Special opts (Lombok, rootClassName, packageName) -->
          <div class="jb-opt">
            <span class="jb-opt-l"><span class="jb-opt-affect">[A]</span> Lombok</span>
            <label class="jb-toggle"><NSwitch v-model:value="lombok" size="small" /><span class="jb-toggle-l">{{ lombok ? '开' : '关' }}</span></label>
          </div>
          <div class="jb-opt" v-if="lombok">
            <span class="jb-opt-l"><span class="jb-opt-affect">[A]</span> @Builder</span>
            <label class="jb-toggle"><NSwitch v-model:value="lombokBuilder" size="small" /><span class="jb-toggle-l">{{ lombokBuilder ? '开' : '关' }}</span></label>
          </div>
          <div class="jb-opt">
            <span class="jb-opt-l"><span class="jb-opt-affect">[A]</span> 根类名</span>
            <NInput v-model:value="rootClassName" size="tiny" placeholder="Root" style="width:120px" />
          </div>
          <div class="jb-opt">
            <span class="jb-opt-l"><span class="jb-opt-affect">[A]</span> 包名</span>
            <NInput v-model:value="packageName" size="tiny" placeholder="com.example" style="width:180px" />
          </div>
        </div>
      </NCollapseItem>
    </NCollapse>

    <!-- 错误提示 -->
    <div v-if="error" class="jb-error">{{ error }}</div>

    <!-- 编辑器双栏（根据方向动态切换输入/输出） -->
    <div class="jb-pair">
      <div class="jb-col">
        <div class="jb-col-h">
          <span class="jb-col-title">{{ direction === 'json2java' ? 'JSON 输入' : 'JSON 输出' }}</span>
          <NButton v-if="direction === 'java2json' && output" size="tiny" tertiary @click="copyOutput">复制</NButton>
        </div>
        <CodeEditor v-if="direction === 'json2java'" v-model="jsonText" language="json" placeholder="粘贴或输入 JSON…" />
        <CodeEditor v-else :model-value="output" language="json" readonly placeholder="转换结果…" />
      </div>
      <div class="jb-col">
        <div class="jb-col-h">
          <span class="jb-col-title">{{ direction === 'java2json' ? 'Java 输入' : 'Java 输出' }}</span>
          <NButton v-if="direction === 'json2java' && output" size="tiny" tertiary @click="copyOutput">复制</NButton>
        </div>
        <CodeEditor v-if="direction === 'java2json'" v-model="javaText" language="java" placeholder="粘贴或输入 Java 源码…" />
        <CodeEditor v-else :model-value="output" language="java" readonly placeholder="转换结果…" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.jb-panel { padding: 12px 20px; height: 100%; display: flex; flex-direction: column; gap: 8px; overflow: hidden; }

.jb-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0; flex-wrap: wrap; }
.jb-toolbar-left, .jb-toolbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.jb-title { font-size: 16px; font-weight: 700; color: var(--text-2); }

.jb-toggle { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; }
.jb-toggle-l { font-size: 12px; color: var(--text-2); user-select: none; }

/* Options */
.jb-options { flex-shrink: 0; }
.jb-options :deep(.n-collapse-item__header) { font-size: 13px; font-weight: 600; color: var(--text-2); padding: 4px 0; }
.jb-options :deep(.n-collapse-item__content) { padding-top: 6px; }
.jb-opts-grid { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.jb-opt { display: flex; align-items: center; gap: 6px; }
.jb-opt-l { font-size: 12px; color: var(--text-2); white-space: nowrap; }
.jb-opt-affect { font-size: 10px; color: var(--text-3); background: var(--hover-bg); padding: 1px 4px; border-radius: 3px; margin-right: 4px; }

/* Error */
.jb-error { font-size: 12px; color: var(--danger); padding: 6px 10px; background: color-mix(in srgb, var(--danger) 8%, transparent); border-radius: 6px; flex-shrink: 0; white-space: pre-wrap; }

/* Column headers */
.jb-col-h { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.jb-col-title { font-size: 12px; font-weight: 600; color: var(--text-2); }

/* Editor pair */
.jb-pair { flex: 1; min-height: 200px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
@media (max-width: 860px) { .jb-pair { grid-template-columns: 1fr; grid-template-rows: 1fr 1fr; } }
.jb-col { display: flex; flex-direction: column; min-height: 0; }

</style>
