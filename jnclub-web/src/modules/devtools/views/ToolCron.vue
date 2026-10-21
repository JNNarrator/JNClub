<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, onMounted } from 'vue'
import { NSelect, NSwitch, NButton, NInput, NInputNumber, NCollapse, NCollapseItem, useMessage } from 'naive-ui'
import { useToolDraft } from '../composables/useToolDraft'
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut'
import {
  type Dialect, type FieldName, type FieldMode, type CronModel, type CronField,
  type RunTime, type ZeroPadStrategy,
  DIALECT_FIELDS, FIELD_RANGES, CRON_SYMBOLS,
  buildExpression, parseExpression, createDefaultModel,
  describeCron, nextRuns, hasDstTransition, formatDateTime, getCurrentTimezone,
  applyMutualExclusion, EXAMPLES,
} from '../utils/cron'

defineOptions({ name: 'ToolCron' })

interface ToolCronDraft {
  dialect: Dialect
  model: CronModel
  locale: 'zh_CN' | 'en'
  zeroPad: ZeroPadStrategy
  futureCount: number
  tz: string
}

const { state: draft, resetDraft } = useToolCronDraft()

function useToolCronDraft() {
  return useToolDraft<ToolCronDraft>('cron', {
    dialect: 'with-seconds-6',
    model: createDefaultModel('with-seconds-6'),
    locale: 'zh_CN',
    zeroPad: 'none',
    futureCount: 5,
    tz: getCurrentTimezone(),
  }, {
    validate: (raw): raw is ToolCronDraft => {
      return typeof raw === 'object' && raw !== null
        && typeof (raw as any).dialect === 'string'
        && typeof (raw as any).model === 'object' && (raw as any).model !== null
        && typeof (raw as any).locale === 'string'
        && typeof (raw as any).futureCount === 'number'
        && typeof (raw as any).tz === 'string'
    },
  })
}

const msg = useMessage()

// ─── State ───
const dialect = computed({
  get: () => draft.value.dialect as Dialect,
  set: (v: Dialect) => { draft.value = { ...draft.value, dialect: v } },
})
const model = computed({
  get: () => draft.value.model,
  set: (v: CronModel) => { draft.value = { ...draft.value, model: v } },
})
const exprText = ref('0 */5 * * * *')
const exprError = ref<string | null>(null)
const humanDesc = ref('')
const runs = ref<RunTime[]>([])
const dstWarning = ref(false)

// ─── Layout & interaction state ───
const isScrolled = ref(false)
const validateSuccess = ref(false)
const staggerKey = ref(0)
const scrollRef = ref<HTMLElement | null>(null)

function onScroll() {
  if (!scrollRef.value) return
  isScrolled.value = scrollRef.value.scrollTop > 4
}

onMounted(() => {
  scrollRef.value?.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  scrollRef.value?.removeEventListener('scroll', onScroll)
})

// ─── Options ───
const locale = computed({
  get: () => draft.value.locale as 'zh_CN' | 'en',
  set: (v: 'zh_CN' | 'en') => { draft.value = { ...draft.value, locale: v } },
})
const zeroPad = computed({
  get: () => draft.value.zeroPad as ZeroPadStrategy,
  set: (v: ZeroPadStrategy) => { draft.value = { ...draft.value, zeroPad: v } },
})
const futureCount = computed({
  get: () => draft.value.futureCount,
  set: (v: number) => { draft.value = { ...draft.value, futureCount: v } },
})
const tz = computed({
  get: () => draft.value.tz,
  set: (v: string) => { draft.value = { ...draft.value, tz: v } },
})
const activeFieldIdx = ref(0)

const dialectOptions = [
  { label: 'Standard（5 段：分 时 日 月 周）', value: 'standard-5' as Dialect },
  { label: '带秒（6 段：秒 分 时 日 月 周）', value: 'with-seconds-6' as Dialect },
  { label: 'Quartz（7 段：秒 分 时 日 月 周 年）', value: 'quartz-7' as Dialect },
]

const localeOptions = [
  { label: '中文', value: 'zh_CN' as const },
  { label: 'English', value: 'en' as const },
]

const tzOptions = [
  { label: '本地时区 (' + getCurrentTimezone() + ')', value: getCurrentTimezone() },
  { label: 'UTC', value: 'UTC' },
  { label: 'America/New_York', value: 'America/New_York' },
  { label: 'America/Los_Angeles', value: 'America/Los_Angeles' },
  { label: 'Europe/London', value: 'Europe/London' },
  { label: 'Europe/Berlin', value: 'Europe/Berlin' },
  { label: 'Asia/Tokyo', value: 'Asia/Tokyo' },
  { label: 'Asia/Shanghai', value: 'Asia/Shanghai' },
  { label: 'Asia/Singapore', value: 'Asia/Singapore' },
  { label: 'Australia/Sydney', value: 'Australia/Sydney' },
]

// Normalize tzOptions to remove duplicate labels
const uniqueTzOptions = computed(() => {
  const seen = new Set<string>()
  return tzOptions.filter(o => {
    const k = o.value
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
})

// ─── Field tab definition ───
const fieldDefs = computed(() => {
  const names = DIALECT_FIELDS[dialect.value]
  const labels: Record<FieldName, string> = {
    second: '秒', minute: '分', hour: '时',
    dayOfMonth: '日', month: '月', dayOfWeek: '周', year: '年',
  }
  return names.map((n, i) => ({ name: n, label: labels[n] || n, idx: i }))
})

const activeField = computed(() => fieldDefs.value[activeFieldIdx.value])
const activeFieldName = computed(() => activeField.value?.name || 'minute')

// ─── Build expression from model → text ───
let isProgrammatic = false

function rebuildExpr() {
  isProgrammatic = true
  try {
    const expr = buildExpression(model.value, zeroPad.value)
    exprText.value = expr
  } finally {
    isProgrammatic = false
  }
}

// ─── Parse text → model ───
let parseGuard = false

function reparseExpr() {
  if (parseGuard || isProgrammatic) return
  const trimmed = exprText.value.trim()
  if (!trimmed) {
    exprError.value = '表达式为空'
    return
  }
  const result = parseExpression(trimmed, dialect.value)
  if (result.model) {
    model.value = result.model
    exprError.value = null
  } else {
    exprError.value = result.error
  }
}

// ─── Watch model → auto-build expression ───
watch(model, () => {
  rebuildExpr()
  updateMeta()
}, { deep: true })

// ─── Watch text → auto-parse with debounce ───
let parseTimer: ReturnType<typeof setTimeout> | null = null

watch(exprText, (newVal) => {
  if (isProgrammatic) return
  if (parseTimer) clearTimeout(parseTimer)
  // Dedupe: skip if buildExpression would produce same text
  const built = buildExpression(model.value, zeroPad.value)
  if (built === newVal.trim()) {
    exprError.value = null
    return
  }
  parseTimer = setTimeout(() => reparseExpr(), 250)
})

// ─── Watch dialect → rebuild model ───
watch(dialect, (newD) => {
  const oldModel = model.value
  const newFieldNames = DIALECT_FIELDS[newD]
  const fields: Partial<Record<FieldName, CronField>> = {}
  for (const fn of newFieldNames) {
    const existing = oldModel.fields[fn]
    if (existing) {
      fields[fn] = { ...existing }
    } else {
      const range = FIELD_RANGES[fn]
      fields[fn] = { mode: 'every', from: range.min, to: range.max, step: 1, set: [] }
    }
  }
  // Quartz default: dayOfWeek = ?
  if (newD === 'quartz-7' && fields.dayOfWeek && fields.dayOfWeek.mode === 'every') {
    fields.dayOfWeek.raw = '?'
  }
  model.value = { dialect: newD, fields }
  activeFieldIdx.value = 0
  rebuildExpr()
  updateMeta()
})

// ─── Update human description and future runs ───
let refreshTimer: ReturnType<typeof setTimeout> | null = null

function updateMeta() {
  const expr = exprText.value
  if (!expr.trim() || exprError.value) {
    humanDesc.value = exprError.value ? '表达式无效' : ''
    runs.value = []
    dstWarning.value = false
    return
  }
  humanDesc.value = describeCron(expr, locale.value)
  runs.value = nextRuns(expr, futureCount.value, new Date(), tz.value === 'UTC' ? 'UTC' : undefined)
  dstWarning.value = hasDstTransition(runs.value)
}

// Debounced updateMeta
function scheduleMetaUpdate() {
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = setTimeout(updateMeta, 300)
}

watch([locale, futureCount, tz, exprError], () => scheduleMetaUpdate())
watch(runs, () => {})

// ─── Refresh relative times periodically (only first item) ───
let relativeTimer: ReturnType<typeof setInterval> | null = null

function refreshRelative() {
  const now = new Date()
  runs.value = runs.value.map(r => ({
    ...r,
    relativeText: r.date.getTime() > now.getTime()
      ? getRelativeTime(r.date, now)
      : '已过时',
  }))
}

function getRelativeTime(date: Date, now: Date): string {
  const diffMs = date.getTime() - now.getTime()
  if (diffMs <= 0) return '已过时'
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return `${diffSec} 秒后`
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return `${diffMin} 分 ${diffSec % 60} 秒后`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时 ${diffMin % 60} 分后`
  const diffDay = Math.floor(diffHour / 24)
  return `${diffDay} 天后`
}

onBeforeUnmount(() => {
  if (parseTimer) clearTimeout(parseTimer)
  if (refreshTimer) clearTimeout(refreshTimer)
  if (relativeTimer) clearInterval(relativeTimer)
})

// Start relative time refresh
relativeTimer = setInterval(refreshRelative, 1000)
function clearAll() {
  resetDraft()
  exprText.value = buildExpression(draft.value.model, draft.value.zeroPad as ZeroPadStrategy)
  exprError.value = null
  humanDesc.value = ''
  runs.value = []
  dstWarning.value = false
}

// ─── Field interaction handlers ───

function setFieldMode(fn: FieldName, mode: FieldMode) {
  const f = model.value.fields[fn]
  if (!f) return
  const range = FIELD_RANGES[fn]
  f.mode = mode
  f.raw = undefined
  if (mode === 'every') {
    f.from = range.min
    f.to = range.max
    f.step = 1
    f.set = []
  } else if (mode === 'range') {
    f.from = range.min
    f.to = range.max
    f.step = 1
  } else if (mode === 'step') {
    f.from = range.min
    f.to = range.max
    f.step = 2
  } else if (mode === 'list') {
    f.set = []
    f.from = 0
    f.to = 0
    f.step = 1
  }
  // 互斥
  model.value = applyMutualExclusion(model.value, fn)
  // 触发 watch
  rebuildExpr()
  scheduleMetaUpdate()
}

function toggleSetValue(fn: FieldName, val: number) {
  const f = model.value.fields[fn]
  if (!f) return
  if (f.mode !== 'list') setFieldMode(fn, 'list')
  const f2 = model.value.fields[fn]
  if (!f2) return
  const idx = f2.set.indexOf(val)
  if (idx >= 0) f2.set.splice(idx, 1)
  else f2.set.push(val)
  model.value = applyMutualExclusion(model.value, fn)
  rebuildExpr()
  scheduleMetaUpdate()
}

function fillSample(idx: number) {
  const samples = EXAMPLES[dialect.value]
  if (idx < samples.length) exprText.value = samples[idx]
}

// ─── 快捷选择预设（按方言区分） ───
interface QuickPreset { label: string; expr: string; custom?: boolean }

const QUICK_PRESETS: Record<Dialect, QuickPreset[]> = {
  'standard-5': [
    { label: '每分钟', expr: '* * * * *' },
    { label: '每小时', expr: '0 * * * *' },
    { label: '每天 0 点', expr: '0 0 * * *' },
    { label: '每天 8 点', expr: '0 8 * * *' },
    { label: '工作日 9 点', expr: '0 9 * * 1-5' },
    { label: '每周日', expr: '0 0 * * 0' },
    { label: '每周一', expr: '0 0 * * 1' },
    { label: '每周五', expr: '0 0 * * 5' },
    { label: '每月 1 号 0 点', expr: '0 0 1 * *' },
    { label: '每月 1 号 8 点', expr: '0 8 1 * *' },
    { label: '自定义', expr: '', custom: true },
  ],
  'with-seconds-6': [
    { label: '每分钟', expr: '0 * * * * *' },
    { label: '每小时', expr: '0 0 * * * *' },
    { label: '每天 0 点', expr: '0 0 0 * * *' },
    { label: '每天 8 点', expr: '0 0 8 * * *' },
    { label: '工作日 9 点', expr: '0 0 9 * * 1-5' },
    { label: '每周日', expr: '0 0 0 * * 0' },
    { label: '每周一', expr: '0 0 0 * * 1' },
    { label: '每周五', expr: '0 0 0 * * 5' },
    { label: '每月 1 号 0 点', expr: '0 0 0 1 * *' },
    { label: '每月 1 号 8 点', expr: '0 0 8 1 * *' },
    { label: '自定义', expr: '', custom: true },
  ],
  'quartz-7': [
    { label: '每分钟', expr: '0 * * * * ? *' },
    { label: '每小时', expr: '0 0 * * * ? *' },
    { label: '每天 0 点', expr: '0 0 0 * * ? *' },
    { label: '每天 8 点', expr: '0 0 8 * * ? *' },
    { label: '工作日 9 点', expr: '0 0 9 * * MON-FRI *' },
    { label: '每周日', expr: '0 0 0 * * SUN *' },
    { label: '每周一', expr: '0 0 0 * * MON *' },
    { label: '每周五', expr: '0 0 0 * * FRI *' },
    { label: '每月 1 号 0 点', expr: '0 0 0 1 * ? *' },
    { label: '每月 1 号 8 点', expr: '0 0 8 1 * ? *' },
    { label: '自定义', expr: '', custom: true },
  ],
}

// 快捷选择分组（按频率）
interface QuickPresetGroup { label: string; presets: QuickPreset[] }

const quickPresetGroups = computed<QuickPresetGroup[]>(() => {
  const all = QUICK_PRESETS[dialect.value] || []
  return [
    { label: '高频', presets: all.filter(p => p.label === '每分钟' || p.label === '每小时') },
    { label: '每天', presets: all.filter(p => p.label.startsWith('每天') || p.label === '工作日 9 点') },
    { label: '每周', presets: all.filter(p => p.label.startsWith('每周')) },
    { label: '每月', presets: all.filter(p => p.label.startsWith('每月')) },
    { label: '特殊', presets: all.filter(p => p.custom) },
  ]
})

// 当前激活的预设（匹配表达式文本）
const activePreset = computed(() => {
  const current = exprText.value.trim()
  const all = QUICK_PRESETS[dialect.value] || []
  return all.find(p => !p.custom && p.expr === current)?.label || null
})

// 应用预设
function applyPreset(preset: QuickPreset) {
  if (preset.custom) {
    exprText.value = ''
    return
  }
  exprText.value = preset.expr
}

function copyExpr() {
  if (!exprText.value) { msg.info('暂无表达式可复制'); return }
  navigator.clipboard.writeText(exprText.value).then(() => msg.success('已复制')).catch(() => msg.error('复制失败'))
}

function handleFormat() {
  reparseExpr()
}

function handleValidate() {
  reparseExpr()
  if (!exprError.value) {
    validateSuccess.value = true
    msg.success('表达式有效')
    setTimeout(() => { validateSuccess.value = false }, 2000)
  } else {
    validateSuccess.value = false
    msg.warning(exprError.value)
  }
}

function handleGenerate() {
  staggerKey.value++
  validateSuccess.value = false
  updateMeta()
}

// ─── 快捷键（scope=cron，仅本工具激活时生效） ───
useKeyboardShortcut('cron.preview', () => {
  reparseExpr()
  updateMeta()
  msg.success('已刷新执行时间预览')
}, { skipWhenEditing: false })

// ─── The field tab sub-component data ───
function getFieldValue(fn: FieldName): CronField | undefined {
  return model.value.fields[fn]
}

const currentField = computed(() => getFieldValue(activeFieldName.value))
const currentRange = computed(() => FIELD_RANGES[activeFieldName.value])

// ─── Column layout for checkbox grid ───
function gridColumns(range: { min: number; max: number }): number {
  const count = range.max - range.min + 1
  if (count <= 10) return 5
  if (count <= 12) return 6
  return 8
}

function rangeValues(range: { min: number; max: number }): number[] {
  const vals: number[] = []
  for (let i = range.min; i <= range.max; i++) vals.push(i)
  return vals
}
</script>

<template>
  <div ref="scrollRef" class="tool-cron">
    <!-- ═══ 1. 方言与环境 ═══ -->
    <section class="cron-section">
      <div class="section-header">
        <h2 class="section-title">方言与环境</h2>
        <NButton quaternary circle size="tiny" @click="clearAll" title="清空所有设置">✕</NButton>
      </div>
      <div class="settings-grid">
        <div class="s-row cols-2">
          <label class="s-label">方言</label>
          <NSelect v-model:value="dialect" :options="dialectOptions" size="small" class="sel-dialect" />
          <label class="s-label">时区</label>
          <NSelect v-model:value="tz" :options="uniqueTzOptions" size="small" class="sel-tz" />
        </div>
        <div class="s-row cols-3">
          <label class="s-label">语言</label>
          <NSelect v-model:value="locale" :options="localeOptions" size="small" class="sel-locale" />
          <label class="s-label">补零</label>
          <NSwitch v-model:value="zeroPad" :checked-value="'auto'" :unchecked-value="'none'" size="small" />
          <label class="s-label">预览次数</label>
          <NInputNumber v-model:value="futureCount" :min="1" :max="20" size="small" class="count-input" />
        </div>
      </div>
    </section>

    <!-- ═══ 2. 表达式 + 分段编辑 ═══ -->
    <section class="cron-section">
      <h2 class="section-title">Cron 表达式</h2>
      <div class="expr-unit">
        <div class="expr-textarea-wrap">
          <NInput
            v-model:value="exprText"
            type="textarea"
            :rows="2"
            class="expr-input mono"
            :class="{ 'expr-error': exprError }"
            placeholder="输入 Cron 表达式…"
          />
        </div>
        <div class="expr-toolbar">
          <NButton size="tiny" quaternary @click="copyExpr">复制</NButton>
          <NButton size="tiny" quaternary @click="handleFormat">格式化</NButton>
          <NButton size="tiny" quaternary @click="handleValidate">校验</NButton>
          <span v-if="exprError" class="expr-status err">⚠ {{ exprError }}</span>
          <span v-else-if="validateSuccess" class="expr-status ok">✓ 有效</span>
        </div>
        <div class="field-segments" :style="{ gridTemplateColumns: 'repeat(' + fieldDefs.length + ', 1fr)' }">
          <div
            v-for="(fd, i) in fieldDefs"
            :key="fd.name"
            class="segment"
            :class="{ active: activeFieldIdx === i }"
            @click="activeFieldIdx = i"
          >
            <span class="segment-label">{{ fd.label }}</span>
            <span class="segment-value mono">{{ buildExpression(model, zeroPad).split(' ')[i] || '*' }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══ 3. 快捷选择 ═══ -->
    <section class="cron-section">
      <h2 class="section-title">快捷选择</h2>
      <div class="quick-select-grid">
        <div v-for="group in quickPresetGroups" :key="group.label" class="qs-row">
          <span class="qs-row-label">{{ group.label }}</span>
          <div class="qs-chips">
            <button
              v-for="preset in group.presets"
              :key="preset.label"
              class="qs-chip"
              :class="{ active: activePreset === preset.label, custom: preset.custom }"
              @click="applyPreset(preset)"
            >{{ preset.label }}</button>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══ 4. 字段编辑器 ═══ -->
    <section v-if="currentField" class="cron-section field-editor-section">
      <h2 class="section-title">字段编辑<span class="section-sub">— {{ activeField?.label }}</span></h2>
      <div class="field-tabs">
        <button
          v-for="(fd, i) in fieldDefs"
          :key="fd.name"
          class="field-tab"
          :class="{ active: activeFieldIdx === i }"
          @click="activeFieldIdx = i"
        >{{ fd.label }}</button>
      </div>

      <div class="field-config">
        <div class="config-row">
          <label class="radio-label" :class="{ active: currentField.mode === 'every' }">
            <input type="radio" :checked="currentField.mode === 'every'" @change="setFieldMode(activeFieldName, 'every')" />
            每×（通配）
          </label>
          <label class="radio-label" :class="{ active: currentField.mode === 'range' }">
            <input type="radio" :checked="currentField.mode === 'range'" @change="setFieldMode(activeFieldName, 'range')" />
            范围
          </label>
          <label class="radio-label" :class="{ active: currentField.mode === 'step' }">
            <input type="radio" :checked="currentField.mode === 'step'" @change="setFieldMode(activeFieldName, 'step')" />
            步长
          </label>
          <label class="radio-label" :class="{ active: currentField.mode === 'list' }">
            <input type="radio" :checked="currentField.mode === 'list'" @change="setFieldMode(activeFieldName, 'list')" />
            指定
          </label>
        </div>

        <div v-if="currentField.raw && !['*', '?'].includes(currentField.raw) && currentField.mode === 'list' && currentField.set.length === 0" class="raw-hint">
          高级表达式：<code>{{ currentField.raw }}</code>（不可视化编辑，切换配置模式将覆盖）
        </div>

        <div v-if="currentField.mode === 'range'" class="config-inputs">
          <div class="input-group">
            <label>从</label>
            <NInputNumber v-model:value="currentField.from" :min="currentRange.min" :max="currentField.to" size="small" class="num-input" @update:value="scheduleMetaUpdate" />
          </div>
          <div class="input-group">
            <label>到</label>
            <NInputNumber v-model:value="currentField.to" :min="currentField.from" :max="currentRange.max" size="small" class="num-input" @update:value="scheduleMetaUpdate" />
          </div>
        </div>

        <div v-if="currentField.mode === 'step'" class="config-inputs">
          <div class="input-group">
            <label>从</label>
            <NInputNumber v-model:value="currentField.from" :min="currentRange.min" :max="currentRange.max" size="small" class="num-input" @update:value="scheduleMetaUpdate" />
          </div>
          <div class="input-group">
            <label>步长</label>
            <NInputNumber v-model:value="currentField.step" :min="1" :max="currentRange.max - currentRange.min" size="small" class="num-input" @update:value="scheduleMetaUpdate" />
          </div>
          <div class="input-hint">结果: {{ currentField.from === currentRange.min ? '*'+'/'+currentField.step : currentField.from+'/'+currentField.step }}</div>
        </div>

        <div v-if="currentField.mode === 'list'" class="checkbox-grid" :style="{ gridTemplateColumns: 'repeat(' + gridColumns(currentRange) + ', 1fr)' }">
          <label v-for="v in rangeValues(currentRange)" :key="v" class="checkbox-cell" :class="{ checked: currentField.set.includes(v) }">
            <input type="checkbox" :checked="currentField.set.includes(v)" @change="toggleSetValue(activeFieldName, v)" />
            <span>{{ v }}</span>
          </label>
        </div>
      </div>
    </section>

    <!-- ═══ 粘性操作栏 ═══ -->
    <div class="cron-action-bar">
      <div class="action-bar-inner">
        <div class="action-left">
          <label class="action-label">执行次数</label>
          <NInputNumber v-model:value="futureCount" :min="1" :max="20" size="small" class="action-count-input" />
        </div>
        <div class="action-right">
          <span v-if="runs.length" class="action-count-text">共 {{ runs.length }} 个时间点</span>
          <NButton type="primary" class="generate-btn" @click="handleGenerate">生成</NButton>
        </div>
      </div>
    </div>

    <!-- ═══ 5. 解析结果 ═══ -->
    <section class="cron-section" id="cron-results">
      <h2 class="section-title">解析结果</h2>

      <div class="human-desc mono" :class="{ muted: humanDesc === '表达式无效' || !humanDesc }">
        {{ humanDesc || '等待输入…' }}
      </div>

      <div v-if="dstWarning" class="dst-warning">⚠ 跨越夏令时切换</div>

      <div v-if="runs.length === 0" class="runs-empty mono">
        {{ exprError ? '表达式无效，无法计算' : '等待输入…' }}
      </div>

      <div v-else class="runs-table-wrap" :key="'runs-' + staggerKey">
        <table class="runs-table">
          <thead>
            <tr>
              <th>#</th>
              <th>本地时间</th>
              <th>UTC</th>
              <th>相对</th>
              <th>DST</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in runs" :key="i" :style="{ animationDelay: i * 30 + 'ms' }">
              <td class="runs-idx">{{ i + 1 }}</td>
              <td class="mono">
                {{ formatDateTime(r.date, tz !== 'UTC' ? undefined : 'UTC').local }}
                <span v-if="i === 0" class="now-tag">（现在）</span>
              </td>
              <td class="mono">{{ formatDateTime(r.date).utc }}</td>
              <td class="mono relative">{{ r.relativeText }}</td>
              <td>
                <span v-if="i > 0 && r.utcOffset !== runs[i - 1].utcOffset" class="dst-flag">⚠</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- ═══ 6. 语法参考 ═══ -->
    <section class="cron-section">
      <NCollapse>
        <NCollapseItem title="语法说明 & 符号速查" name="syntax">
          <div class="syntax-content">
            <div class="examples-area">
              <div class="sect-title">快速插入示例</div>
              <div class="example-chips">
                <span v-for="(ex, i) in EXAMPLES[dialect]" :key="i" class="example-chip mono" @click="fillSample(i)">{{ ex }}</span>
              </div>
            </div>
            <div class="symbols-table-wrap">
              <div class="sect-title">符号速查</div>
              <table class="symbols-table">
                <thead>
                  <tr><th>符号</th><th>名称</th><th>说明</th><th>可用方言</th></tr>
                </thead>
                <tbody>
                  <tr v-for="sym in CRON_SYMBOLS" :key="sym.symbol">
                    <td class="mono sym-symbol">{{ sym.symbol }}</td>
                    <td>{{ sym.name }}</td>
                    <td>{{ sym.desc }}</td>
                    <td class="mono sym-dialects">{{ sym.dialects.join(', ') }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </NCollapseItem>
      </NCollapse>
    </section>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════════════════════
   Cron 工具页 — 精细重构
   滚动修复 + 设置条 grid + 表达式核心区 + 快捷选择对齐
   + 粘性操作栏 + 结果区 stagger + 整体节奏
   ═══════════════════════════════════════════════════════════ */

/* ─── 页面根容器：撑满父容器并滚动 ─── */
.tool-cron {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  max-width: 820px;
  margin: 0 auto;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  color: var(--text-1);
}

/* ─── 通用 section 卡片 ─── */
.cron-section {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  margin: 0;
  line-height: 1.4;
}

.section-sub {
  font-weight: 400;
  color: var(--text-3);
  margin-left: 4px;
}

/* ═══ 1. 设置条 grid ═══ */
.settings-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.s-row {
  display: grid;
  gap: 8px 16px;
  align-items: center;
}

.s-row.cols-2 {
  grid-template-columns: auto 1fr auto 1fr;
}

.s-row.cols-3 {
  grid-template-columns: auto 1fr auto 1fr auto 1fr;
}

.s-label {
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
}

.sel-dialect { width: 100%; }
.sel-tz { width: 100%; }
.sel-locale { width: 100%; }
.count-input { width: 70px; }

/* ═══ 2. 表达式核心区 ═══ */
.expr-unit {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.expr-textarea-wrap {
  position: relative;
}

.expr-input :deep(textarea) {
  font-family: var(--font-mono) !important;
  font-size: 15px !important;
  background: transparent !important;
}

.expr-input.expr-error :deep(textarea) {
  border-color: var(--danger) !important;
  box-shadow: 0 0 0 1px var(--danger) !important;
}

.expr-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.expr-status {
  font-size: 12px;
  margin-left: 8px;
}

.expr-status.err {
  color: var(--danger);
}

.expr-status.ok {
  color: var(--state-success);
}

/* 分段导航（===7 格等宽） */
.field-segments {
  display: grid;
  gap: 6px;
}

.segment {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border-radius: 6px;
  background: var(--hover-bg);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: all 0.12s;
}

.segment:hover {
  border-color: var(--brand-hover);
}

.segment.active {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.segment-label {
  font-size: 10px;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.segment-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

/* ═══ 3. 快捷选择 ═══ */
.quick-select-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qs-row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  align-items: baseline;
}

.qs-row-label {
  font-size: 12px;
  color: var(--text-3);
  min-width: 36px;
  text-align: right;
  padding-top: 4px;
  flex-shrink: 0;
}

.qs-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.qs-chip {
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--hover-bg);
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
  line-height: 1.4;
}

.qs-chip:hover {
  border-color: var(--brand-hover);
  color: var(--text-1);
}

.qs-chip.active {
  background: var(--brand);
  color: var(--text-1);
  border-color: var(--brand);
}

.qs-chip.custom {
  border-style: dashed;
  color: var(--text-3);
}

/* ═══ 4. 字段编辑器 ═══ */
.field-editor-section {
  gap: 0;
}

.field-tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
  background: var(--hover-bg);
  margin: 0 -20px;
  padding: 0 20px;
}

.field-tab {
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  flex-shrink: 0;
  font-family: inherit;
}

.field-tab:hover { color: var(--text-1); }

.field-tab.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
}

.field-config {
  padding: 14px 0 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.config-row {
  display: flex;
  gap: 4px;
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text-2);
  border: 1px solid var(--border);
  transition: all 0.15s;
  background: var(--hover-bg);
}

.radio-label:hover { border-color: var(--brand-hover); }

.radio-label.active {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-soft);
}

.radio-label input { display: none; }

.raw-hint {
  font-size: 12px;
  color: var(--state-warning);
  background: var(--state-warning-soft);
  padding: 6px 10px;
  border-radius: 6px;
}

.raw-hint code {
  font-family: var(--font-mono);
  font-size: 12px;
  background: var(--border);
  padding: 1px 5px;
  border-radius: 3px;
}

.config-inputs {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.input-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.input-group label {
  font-size: 12px;
  color: var(--text-2);
}

.num-input { width: 80px; }

.input-hint {
  font-size: 12px;
  color: var(--text-3);
  font-family: var(--font-mono);
  margin-top: 4px;
}

.checkbox-grid {
  display: grid;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.checkbox-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 4px 0;
  border-radius: 5px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-2);
  border: 1px solid transparent;
  transition: all 0.12s;
}

.checkbox-cell:hover { border-color: var(--brand-hover); }

.checkbox-cell.checked {
  background: var(--brand-soft);
  color: var(--brand);
  border-color: var(--brand);
}

.checkbox-cell input { display: none; }
.checkbox-cell span { pointer-events: none; }

/* ═══ 操作栏 ═══ */
.cron-action-bar {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 14px 20px;
}

.action-bar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.action-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-label {
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
}

.action-count-input {
  width: 80px;
}

.action-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-count-text {
  font-size: 13px;
  color: var(--text-2);
  font-weight: 500;
}

.generate-btn {
  height: 44px;
  font-weight: 600;
  font-size: 15px;
  min-width: 110px;
}



/* ═══ 5. 解析结果 ═══ */
.human-desc {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-1);
  padding: 12px 16px;
  background: var(--hover-bg);
  border-radius: 6px;
}

.human-desc.muted {
  color: var(--text-3);
  font-weight: 400;
  font-size: 13px;
}

.dst-warning {
  font-size: 12px;
  color: var(--state-warning);
  padding: 6px 12px;
  background: var(--state-warning-soft);
  border-radius: 4px;
}

.runs-empty {
  font-size: 13px;
  color: var(--text-3);
  padding: 12px 0;
}

.runs-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
}

.runs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.runs-table th {
  text-align: left;
  font-size: 12px;
  color: var(--text-3);
  font-weight: 500;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  text-transform: uppercase;
  background: var(--hover-bg);
}

.runs-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text-1);
}

.runs-table tr:last-child td { border-bottom: none; }
.runs-table tr:hover td { background: var(--hover-bg); }
.runs-idx { color: var(--text-3); }
.relative { color: var(--text-2); font-size: 12px; }
.dst-flag { color: var(--state-warning); }

.now-tag {
  font-size: 12px;
  color: var(--brand);
  margin-left: 8px;
  font-weight: 500;
}

/* ─── Stagger 淡入动画 ─── */
@keyframes stagger-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.runs-table tbody tr {
  animation: stagger-in 0.3s ease both;
}

@media (prefers-reduced-motion: reduce) {
  .runs-table tbody tr,
  .segment,
  .qs-chip,
  .field-tab,
  .checkbox-cell,
  .radio-label,
  .example-chip {
    animation: none !important;
    transition: none !important;
  }
}

/* ═══ 6. 语法参考 ═══ */
.syntax-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0;
}

.sect-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 6px;
}

.example-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.example-chip {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  background: var(--hover-bg);
  border: 1px solid var(--border);
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.12s;
}

.example-chip:hover {
  border-color: var(--brand-hover);
  color: var(--brand);
}

.symbols-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.symbols-table th {
  text-align: left;
  font-size: 11px;
  color: var(--text-3);
  font-weight: 500;
  padding: 4px 8px;
  border-bottom: 1px solid var(--border);
}

.symbols-table td {
  padding: 4px 8px;
  border-bottom: 1px solid var(--border);
  color: var(--text-1);
}

.sym-symbol { font-weight: 600; color: var(--brand); }
.sym-dialects { font-size: 11px; color: var(--text-3); }

/* ─── Naive UI Collapse 覆盖 ─── */
:deep(.n-collapse-item__header) {
  font-size: 13px !important;
  font-weight: 500;
  padding: 8px 14px;
}

:deep(.n-collapse-item__content) {
  padding: 0 14px 14px;
}

/* ─── Mono / Muted 工具类 ─── */
.mono { font-family: var(--font-mono); }

/* ═══ 响应式 ═══ */
@media (max-width: 600px) {
  .tool-cron {
    padding: 16px;
    gap: 14px;
  }

  .cron-section {
    padding: 16px;
    gap: 14px;
  }

  .s-row.cols-2,
  .s-row.cols-3 {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .s-row.cols-2 > .s-label,
  .s-row.cols-3 > .s-label {
    margin-top: 4px;
  }

  .field-tabs {
    margin: 0 -16px;
    padding: 0 16px;
  }

  .cron-action-bar {
    padding: 12px 16px;
  }

  .action-bar-inner {
    flex-direction: column;
    gap: 10px;
  }

  .action-right {
    width: 100%;
    justify-content: flex-end;
  }

  .qs-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .qs-row-label {
    text-align: left;
    min-width: 0;
    padding-top: 0;
  }

  .action-count-input {
    flex: 1;
  }
}
</style>
