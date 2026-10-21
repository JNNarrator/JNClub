<script setup lang="ts">
import { defineOptions } from 'vue'
import { computed, ref, shallowRef, watch } from 'vue'
import { NSelect, NSwitch, NButton, useMessage } from 'naive-ui'
import CodeEditor from '../components/CodeEditor.vue'
import { convert, FORMAT_META, lossyHint, type ConvertResult, type DataFormat } from '../utils/converter'
import { useToolDraft } from '../composables/useToolDraft'
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut'

defineOptions({ name: 'ToolConverter' })

const msg = useMessage()
const FORMATS = Object.keys(FORMAT_META) as DataFormat[]
const fmtOptions = FORMATS.map(f => ({ label: FORMAT_META[f].displayName, value: f }))

interface ToolConverterDraft {
  inputText: string
  sourceFmt: DataFormat
  targetFmt: DataFormat
  liveMode: boolean
  autoFlatten: boolean
}

const { state: draft, resetDraft } = useToolDraft<ToolConverterDraft>('converter', {
  inputText: '',
  sourceFmt: 'json',
  targetFmt: 'yaml',
  liveMode: true,
  autoFlatten: false,
}, {
  validate: (raw): raw is ToolConverterDraft => {
    return typeof raw === 'object' && raw !== null
      && typeof (raw as any).inputText === 'string'
      && typeof (raw as any).sourceFmt === 'string'
      && typeof (raw as any).targetFmt === 'string'
      && typeof (raw as any).liveMode === 'boolean'
      && typeof (raw as any).autoFlatten === 'boolean'
  },
})

const fromFmt = computed({
  get: () => draft.value.sourceFmt,
  set: (v: DataFormat) => { draft.value = { ...draft.value, sourceFmt: v } },
})
const toFmt = computed({
  get: () => draft.value.targetFmt,
  set: (v: DataFormat) => { draft.value = { ...draft.value, targetFmt: v } },
})
const input = computed({
  get: () => draft.value.inputText,
  set: (v: string) => { draft.value = { ...draft.value, inputText: v } },
})
const liveMode = computed({
  get: () => draft.value.liveMode,
  set: (v: boolean) => { draft.value = { ...draft.value, liveMode: v } },
})
const autoFlatten = computed({
  get: () => draft.value.autoFlatten,
  set: (v: boolean) => { draft.value = { ...draft.value, autoFlatten: v } },
})
const output = shallowRef('')
const parseErr = ref<ConvertResult | null>(null)
const stringifyErr = ref<ConvertResult | null>(null)
const elapsed = ref(0)
const busy = ref(false)

const lossy = computed(() => lossyHint(fromFmt.value, toFmt.value))
const inputCount = computed(() => input.value.length)

let timer: ReturnType<typeof setTimeout> | null = null

function normalizeQuotes(s: string) { return s.replace(/[\u201C\u201D\u201E\u201F\u2018\u2019]/g, '"') }

function runConvert() {
  const cleaned = normalizeQuotes(input.value)
  if (!cleaned.trim()) { output.value = ''; parseErr.value = null; stringifyErr.value = null; return }
  busy.value = true
  const start = performance.now()
  const res = convert(cleaned, fromFmt.value, toFmt.value, { autoFlatten: autoFlatten.value })
  elapsed.value = Math.round((performance.now() - start) * 1000) / 1000
  busy.value = false
  if (res.ok) {
    output.value = res.text; parseErr.value = null; stringifyErr.value = null
  } else if (res.stage === 'parse') {
    parseErr.value = res; stringifyErr.value = null; output.value = ''
  } else {
    stringifyErr.value = res; parseErr.value = null; output.value = ''
  }
}

function scheduleRun() {
  if (!liveMode.value) return
  if (timer) clearTimeout(timer)
  timer = setTimeout(runConvert, 300)
}

watch([input, fromFmt, toFmt, autoFlatten], scheduleRun, { flush: 'post' })

function manualConvert() { runConvert() }
function swap() {
  const tmpF = fromFmt.value; fromFmt.value = toFmt.value; toFmt.value = tmpF
  if (output.value) { input.value = output.value; output.value = '' }
  runConvert()
}
function clearAll() {
  output.value = ''; parseErr.value = null; stringifyErr.value = null
  resetDraft()
}
function formatInput() {
  const res = convert(normalizeQuotes(input.value), fromFmt.value, fromFmt.value, { autoFlatten: autoFlatten.value })
  if (res.ok) { input.value = res.text; parseErr.value = null }
  else { parseErr.value = res; msg.warning('格式化失败：' + res.message) }
}
function fillSample() { input.value = FORMAT_META[fromFmt.value].sample; runConvert() }

// ─── 快捷键（scope=converter，仅本工具激活时生效） ───
useKeyboardShortcut('converter.swap', () => swap(), { skipWhenEditing: true })
function copyOut() {
  if (!output.value) { msg.info('暂无输出可复制'); return }
  navigator.clipboard.writeText(output.value).then(() => msg.success('已复制')).catch(() => msg.error('复制失败'))
}
function errText(e: ConvertResult): string {
  if (!e.ok) {
    const pos = e.line != null ? `（行 ${e.line}${e.col != null ? `·列 ${e.col}` : ''}）` : ''
    return `${e.fmt.toUpperCase()} ${e.stage} 错误${pos}：${e.message}`
  }
 return ''
}



</script>

<template>
  <div class="cv-panel">
    <div class="cv-toolbar">
      <div class="cv-toolbar-left">
        <span class="cv-title">格式互转</span>
        <NSelect v-model:value="fromFmt" :options="fmtOptions" size="small" style="width: 100px" />
        <button class="cv-arrow" @click="swap" title="交换源/目标">⇄</button>
        <NSelect v-model:value="toFmt" :options="fmtOptions" size="small" style="width: 100px" />
      </div>
      <div class="cv-toolbar-right">
        <label class="cv-flip"><NSwitch v-model:value="autoFlatten" size="small" /><span class="cv-flip-l">自动扁平化（CSV）</span></label>
        <label class="cv-flip"><NSwitch v-model:value="liveMode" size="small" /><span class="cv-flip-l">实时转换</span></label>
        <NButton size="small" tertiary @click="formatInput">格式化输入</NButton>
        <NButton size="small" tertiary @click="fillSample">示例</NButton>
        <NButton size="small" tertiary @click="clearAll">清空</NButton>
        <NButton size="small" type="primary" :disabled="busy" @click="manualConvert">转换</NButton>
      </div>
    </div>

    <p v-if="lossy" class="cv-lossy">⚠ {{ lossy }}</p>

    <div class="cv-pair">
      <div class="cv-col">
        <div class="cv-col-h">
          <span>输入 · {{ FORMAT_META[fromFmt].displayName }}</span>
          <span class="cv-meta">{{ inputCount }} 字符</span>
        </div>
        <CodeEditor v-model="input" :language="fromFmt === 'properties' ? 'properties' : fromFmt === 'csv' ? 'csv' : fromFmt" placeholder="粘贴或在此输入…" />
        <div v-if="parseErr" class="cv-err">{{ errText(parseErr!) }}</div>
      </div>
      <div class="cv-col">
        <div class="cv-col-h">
          <span>输出 · {{ FORMAT_META[toFmt].displayName }}</span>
          <span class="cv-meta">
            <NButton v-if="output" size="tiny" tertiary @click="copyOut">复制</NButton>
            {{ elapsed }} ms
          </span>
        </div>
        <CodeEditor :model-value="output" :language="toFmt === 'properties' ? 'properties' : toFmt === 'csv' ? 'csv' : toFmt" readonly placeholder="转换结果…" />
        <div v-if="stringifyErr" class="cv-err">{{ errText(stringifyErr!) }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cv-panel { padding: 12px 20px; height: 100%; display: flex; flex-direction: column; gap: 10px; overflow: hidden; }
.cv-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0; flex-wrap: wrap; }
.cv-toolbar-left, .cv-toolbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cv-title { font-size: 16px; font-weight: 700; color: var(--text-1); }
.cv-arrow { width: 28px; height: 26px; display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid var(--border); border-radius: 6px; background: transparent; color: var(--text-2);
  cursor: pointer; font-size: 16px; transition: all .15s; }
.cv-arrow:hover { color: var(--brand); border-color: var(--brand); }
.cv-flip { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
.cv-flip-l { font-size: 12px; color: var(--text-2); user-select: none; }
.cv-lossy { font-size: 12px; color: var(--state-warning); margin: 0; padding: 6px 10px;
  background: var(--state-warning-soft); border-radius: 8px; flex-shrink: 0; }
.cv-pair { flex: 1; min-height: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 720px) { .cv-pair { grid-template-columns: 1fr; grid-template-rows: 1fr 1fr; } }
.cv-col { display: flex; flex-direction: column; min-height: 0; gap: 6px; }
.cv-col-h { display: flex; align-items: center; justify-content: space-between; font-size: 12px;
  color: var(--text-2); padding: 0 2px; }
.cv-meta { display: inline-flex; align-items: center; gap: 8px; font-size: 11px; color: var(--text-3); }
.cv-err { font-size: 12px; color: var(--danger); padding: 6px 10px;
  background: var(--brand-soft); border-radius: 8px; }
</style>
