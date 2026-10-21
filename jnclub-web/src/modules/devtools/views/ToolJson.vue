<script setup lang="ts">
import { defineOptions, ref, watch, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut'
import { useToolDraft } from '../composables/useToolDraft'
import CodeEditor from '../components/CodeEditor.vue'
import JsonTree from '../components/JsonTree.vue'
import DraggableSplitter from '../components/DraggableSplitter.vue'
import type { FlatJsonNode } from '../types'

defineOptions({ name: 'ToolJson' })

interface ToolJsonDraft {
  input: string
  searchQuery: string
}

const { state: draft, resetDraft } = useToolDraft<ToolJsonDraft>('json', {
  input: '',
  searchQuery: '',
}, {
  validate: (raw): raw is ToolJsonDraft => {
    return typeof raw === 'object' && raw !== null
      && typeof (raw as any).input === 'string'
      && typeof (raw as any).searchQuery === 'string'
  },
})

// 为了让模板继续用 input/searchQuery 名，创建 computed 代理
const input = computed({
  get: () => draft.value.input,
  set: (v: string) => { draft.value = { ...draft.value, input: v } },
})
const searchQuery = computed({
  get: () => draft.value.searchQuery,
  set: (v: string) => { draft.value = { ...draft.value, searchQuery: v } },
})

const msg = useMessage()
const treeNodes = ref<FlatJsonNode[]>([])
const splitRatio = ref(0.5)
const isValid = ref<'valid' | 'invalid' | null>(null)
const errorMsg = ref('')
const errorLineRef = ref<number | null>(null)
const keyCount = ref(0)
const maxDepth = ref(0)

function normalizeQuotes(s: string) { return s.replace(/\u201C|\u201D|\u201E|\u201F|\u2018|\u2019/g, '"') }

function getErrorLine(err: any): number | null {
  const msgStr = err.message || ''
  const lm = msgStr.match(/line\s+(\d+)/i)
  if (lm) return parseInt(lm[1])
  const pm = msgStr.match(/position\s+(\d+)/i)
  if (pm) {
    const pos = parseInt(pm[1])
    return (input.value.substring(0, pos).match(/\n/g) || []).length + 1
  }
  return null
}

function buildNodes(parsed: any) {
  const nodes: FlatJsonNode[] = []
  let nid = 0
  let keys = 0
  let deepest = 0
  function traverse(val: any, key: string, parentId: string | null, depth: number, path: string) {
    const id = `n${nid++}`
    const valType = val === null ? 'null' : Array.isArray(val) ? 'array' : typeof val
    const hasChildren = (valType === 'object' && val !== null) || valType === 'array'
    const childCount = hasChildren && val !== null
      ? (valType === 'array' ? val.length : Object.keys(val).length)
      : 0
    if (depth > 0) keys++
    if (depth > deepest) deepest = depth
    nodes.push({ id, parentId, key, value: val, valueType: valType as any, depth,
      expanded: depth < 3, hasChildren, childCount, path })
    if (hasChildren && val !== null) {
      if (valType === 'array') {
        for (let i = 0; i < val.length; i++) traverse(val[i], String(i), id, depth + 1, `${path}[${i}]`)
      } else {
        for (const k of Object.keys(val)) traverse(val[k], k, id, depth + 1, `${path}.${k}`)
      }
    }
  }
  traverse(parsed, 'root', null, 0, '$')
  keyCount.value = keys
  maxDepth.value = deepest
  return nodes
}

function execute() {
  errorMsg.value = ''
  errorLineRef.value = null
  if (!input.value.trim()) { msg.warning('请输入 JSON'); return }
  try {
    const normalized = normalizeQuotes(input.value)
    const parsed = JSON.parse(normalized)
    const formatted = JSON.stringify(parsed, null, 2)
    input.value = formatted
    treeNodes.value = buildNodes(parsed)
    isValid.value = 'valid'
  } catch (e: any) {
    errorMsg.value = e.message
    errorLineRef.value = getErrorLine(e)
    treeNodes.value = []
    isValid.value = 'invalid'
  }
}

function loadSample() {
  const sample = {
    name: "jnclub",
    version: "0.1.0",
    description: "Developer Swiss Army Knife",
    tools: ["json", "http", "clipboard", "settings"],
    metadata: { author: "developer", theme: "pink", shortcuts: [{ key: "⌘K", action: "search" }, { key: "⌘J", action: "json_tool" }] }
  }
  input.value = JSON.stringify(sample, null, 2)
  execute()
}

function beautify() {
  if (!input.value.trim()) return
  try { input.value = JSON.stringify(JSON.parse(normalizeQuotes(input.value)), null, 2); execute() }
  catch { msg.warning('JSON 格式有误') }
}

function compact() {
  if (!input.value.trim()) return
  try { input.value = JSON.stringify(JSON.parse(normalizeQuotes(input.value))); execute() }
  catch { msg.warning('JSON 格式有误') }
}

function copyAll() {
  if (!input.value.trim()) { msg.warning('没有内容可复制'); return }
  navigator.clipboard.writeText(input.value)
  msg.success('已复制')
}

function clearAll() {
  input.value = ''; treeNodes.value = []; isValid.value = null; errorMsg.value = ''
  errorLineRef.value = null; searchQuery.value = ''
  resetDraft()
}

useKeyboardShortcut('json.run', () => execute(), { skipWhenEditing: false })
useKeyboardShortcut('json.format', () => beautify(), { skipWhenEditing: false })

function toggleExpand(id: string) {
  const n = treeNodes.value.find(x => x.id === id); if (n) n.expanded = !n.expanded
}
function expandAll() { treeNodes.value.forEach(n => n.expanded = true) }
function collapseAll() { treeNodes.value.forEach(n => { if (n.depth > 0) n.expanded = false }) }

// For sample JSON loading from tree empty state
watch(searchQuery, (val) => {
  if (val === 'loadSample') { loadSample(); searchQuery.value = '' }
})
</script>

<template>
  <div class="json-tool">
    <!-- ─── Toolbar ─── -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="toolbar-title">JSON 工具</h2>
        <span v-if="isValid" class="status-badge" :class="isValid">
          <svg v-if="isValid === 'valid'" viewBox="0 0 14 14" width="12" height="12"><circle cx="7" cy="7" r="6" fill="currentColor"/><path d="M4.5 7L6.5 9 10 5" stroke="#fff" stroke-width="1.5" fill="none" stroke-linecap="round"/></svg>
          <svg v-else viewBox="0 0 14 14" width="12" height="12"><circle cx="7" cy="7" r="6" fill="currentColor"/><path d="M5 5l4 4M9 5l-4 4" stroke="#fff" stroke-width="1.5" fill="none" stroke-linecap="round"/></svg>
          {{ isValid === 'valid' ? '合法' : `非法 · 第 ${errorLineRef} 行` }}
        </span>
      </div>
      <div class="toolbar-right">
        <button class="icon-btn" title="美化" @click="beautify">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 11l4 4 4-4M11 5l4 4 4-4"/></svg>
        </button>
        <button class="icon-btn" title="压缩" @click="compact">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 7l4-4 4 4M17 13l-4 4-4-4"/></svg>
        </button>
        <button class="icon-btn" title="复制" @click="copyAll">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="6" y="4" width="11" height="13" rx="1.5"/><path d="M3 16V3a1 1 0 011-1h10"/></svg>
        </button>
        <button class="icon-btn" title="清空（同时清除记忆）" @click="clearAll">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 5h12M7 5V3.5A.5.5 0 017.5 3h5a.5.5 0 01.5.5V5M8 8v6M12 8v6M3 5h14l-1.5 12a2 2 0 01-2 2h-7a2 2 0 01-2-2L3 5z"/></svg>
        </button>
        <div class="divider-v"></div>
        <button class="primary-btn" @click="execute" :disabled="!input.trim()">
          执行 / 格式化
          <kbd>⌘↵</kbd>
        </button>
      </div>
    </div>

    <!-- ─── Panels ─── -->
    <div class="panels">
      <div class="panel-left" :style="{ width: (splitRatio * 100) + '%' }">
        <CodeEditor v-model="input" language="json" :error-line="errorLineRef" />
      </div>
      <DraggableSplitter @update:ratio="splitRatio = $event" />
      <div class="panel-right">
        <JsonTree
          :nodes="treeNodes"
          :search-query="searchQuery"
          @update:search-query="searchQuery = $event"
          @expand-all="expandAll"
          @collapse-all="collapseAll"
          @toggle-expand="toggleExpand"
          @load-sample="loadSample"
        />
      </div>
    </div>

    <!-- ─── Status bar ─── -->
    <div class="statusbar">
      <div class="status-left">
        <span v-if="isValid === 'valid'" class="status-item status-ok">
          <svg viewBox="0 0 12 12" width="10" height="10"><circle cx="6" cy="6" r="5" fill="currentColor"/><path d="M4 6l1.5 1.5L8.5 4" stroke="#fff" stroke-width="1.5" fill="none" stroke-linecap="round"/></svg>
          JSON 合法
        </span>
        <span v-else-if="isValid === 'invalid'" class="status-item status-err">
          <svg viewBox="0 0 12 12" width="10" height="10"><circle cx="6" cy="6" r="5" fill="currentColor"/><path d="M4.5 4.5l3 3M7.5 4.5l-3 3" stroke="#fff" stroke-width="1.5" fill="none" stroke-linecap="round"/></svg>
          {{ errorMsg.slice(0, 50) }}{{ errorMsg.length > 50 ? '…' : '' }}
        </span>
        <span v-else class="status-item status-idle">等待执行</span>
      </div>
      <div class="status-right">
        <span class="status-item">UTF-8</span>
        <span v-if="treeNodes.length" class="status-item">{{ keyCount }} 个键</span>
        <span v-if="treeNodes.length" class="status-item">深度 {{ maxDepth }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
 * JSON 工具私有变量：全部映射到 JNClub 设计 token。
 * 原实现硬编码了一套粉色主题色 + 一段非 scoped 的 [data-theme="dark"] 覆盖块，
 * 迁到 JNClub 后 token 自身已随 data-theme 切换，因此不再需要暗色覆盖，
 * 也不会出现「暗色下仍用浅色硬编码值」的脱节问题。
 */
.json-tool {
  height: 100%; display: flex; flex-direction: column;
  --json-primary: var(--brand);
  --json-primary-light: var(--brand-soft);
  --json-bg: var(--bg-card);
  --json-surface: var(--bg-page);
  --json-border: var(--border);
  --json-text: var(--text-1);
  --json-text-secondary: var(--text-3);
  --json-line-num: var(--text-4);
  --json-gutter-bg: var(--hover-bg);
  --json-green: var(--state-success);
  --json-blue: var(--brand);
  --json-purple: var(--brand-suppl);
  --json-gray: var(--text-3);
  --json-error-wavy: var(--danger);
  --json-error-line: var(--danger-soft);
}

/* ─── Toolbar ─── */
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 20px; flex-shrink: 0;
  border-bottom: 1px solid var(--json-border);
}
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.toolbar-title { font-size: 16px; font-weight: 700; color: var(--json-text); margin: 0; }
.toolbar-right { display: flex; align-items: center; gap: 4px; }

/* Status badge */
.status-badge {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 10px; border-radius: 20px;
  font-size: 12px; font-weight: 600;
}
.status-badge.valid { background: color-mix(in srgb, var(--state-success) 18%, transparent); color: var(--success-text); }
.status-badge.invalid { background: var(--json-error-line); color: var(--json-error-wavy); }

.icon-btn {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid transparent; border-radius: 8px;
  background: transparent; color: var(--json-text-secondary);
  cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { background: var(--json-primary-light); color: var(--json-primary); border-color: var(--json-border); }
.icon-btn svg { width: 18px; height: 18px; }

.divider-v {
  width: 1px; height: 24px; margin: 0 4px;
  background: var(--json-border);
}

.primary-btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 0 18px; height: 32px;
  border: none; border-radius: 8px;
  background: linear-gradient(135deg, var(--brand), var(--brand-hover));
  color: #FFF; font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
  white-space: nowrap;
}
.primary-btn:hover { box-shadow: var(--shadow-card-hover); transform: translateY(-1px); }
.primary-btn:active { transform: translateY(0); }
.primary-btn:disabled { opacity: 0.4; cursor: not-allowed; transform: none; box-shadow: none; }
.primary-btn kbd {
  font-size: 10px; padding: 1px 5px; border-radius: 4px;
  background: rgba(255,255,255,0.2); font-family: inherit;
}

/* ─── Panels ─── */
.panels {
  flex: 1; display: flex; min-height: 0;
  padding: 12px 20px; gap: 0;
}
.panel-left { flex: none; display: flex; flex-direction: column; min-width: 0; }
.panel-right { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* ─── Status bar ─── */
.statusbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 20px; flex-shrink: 0;
  border-top: 1px solid var(--json-border);
  font-size: 11px; font-family: var(--font-mono);
  color: var(--json-text-secondary);
  background: var(--json-surface);
}
.status-left, .status-right { display: flex; align-items: center; gap: 16px; }
.status-item { display: inline-flex; align-items: center; gap: 4px; }
.status-ok { color: var(--state-success); }
.status-err { color: var(--json-error-wavy); }
.status-idle { color: var(--json-gray); }

/* ─── 移动端：上下分栏，隐藏拖拽分隔条 ─── */
@media (max-width: 767px) {
  .toolbar { padding: 8px 12px; gap: 8px; flex-wrap: wrap; }
  .toolbar-title { font-size: 14px; }
  .toolbar-left, .toolbar-right { gap: 6px; }
  .toolbar-right .icon-btn { width: var(--tap-min, 32px); height: var(--tap-min, 32px); }
  .primary-btn { padding: 0 12px; }
  .panels {
    flex-direction: column;
    padding: 8px 12px;
    gap: 8px;
  }
  .panel-left,
  .panel-right {
    width: 100% !important;
    flex: 1 1 50%;
    min-height: 0;
  }
  .panels > :deep(.splitter) { display: none; }
  .statusbar { padding: 4px 12px; font-size: 11px; }
  .status-left, .status-right { gap: 10px; }
}
</style>
