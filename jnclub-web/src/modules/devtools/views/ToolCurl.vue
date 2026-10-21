<script setup lang="ts">
import { defineOptions, ref, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { useHttpStore } from '../stores/http'
import { useHttpSend } from '../composables/useHttpSend'
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut'
import { parseCurl } from '../utils/parseCurl'
import Kbd from '../components/Kbd.vue'
import HeaderEditor from '../components/HeaderEditor.vue'
import CodeEditor from '../components/CodeEditor.vue'

defineOptions({ name: 'ToolCurl' })
// 工具记忆（useToolDraft）：跳过 — HTTP 请求包含敏感 Header（API Key、Auth Token），不持久化

const store = useHttpStore()
const { send, cancel, loading } = useHttpSend()
const msg = useMessage()
const urlInput = ref<HTMLInputElement | null>(null)
const curlDialogOpen = ref(false)
const curlInput = ref('')
const curlError = ref('')
const responseTab = ref<'pretty' | 'raw' | 'headers'>('pretty')
const dragOver = ref(false)

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

const methodColors: Record<string, string> = {
  GET: 'var(--brand)', POST: 'var(--state-success)',
  PUT: 'var(--state-warning)', PATCH: 'var(--brand)',
  DELETE: 'var(--danger)', HEAD: 'var(--text-3)',
  OPTIONS: 'var(--text-3)',
}

const statusColor = computed(() => {
  if (!store.response) return ''
  const s = store.response.status
  if (s >= 200 && s < 300) return 'var(--state-success)'
  if (s >= 300 && s < 400) return 'var(--brand)'
  if (s >= 400 && s < 500) return 'var(--state-warning)'
  return 'var(--danger)'
})

const responseSize = computed(() => {
  if (!store.response) return ''
  const b = store.response.sizeBytes
  return b >= 1024 ? (b / 1024).toFixed(1) + ' KB' : b + ' B'
})

const prettyBody = computed(() => {
  if (!store.response) return ''
  try { return JSON.stringify(JSON.parse(store.response.body), null, 2) }
  catch { return store.response.body }
})

const resHeaders = computed(() => {
  if (!store.response) return []
  return Object.entries(store.response.headers).map(([k, v]) => ({ k, v }))
})

async function doSend() {
  if (!store.request.url.trim()) { msg.warning('请输入 URL'); return }
  store.response = await send(store.request)
}

function handlePaste(e: ClipboardEvent) {
  const text = e.clipboardData?.getData('text') || ''
  if (text.trim().toLowerCase().startsWith('curl ')) {
    e.preventDefault()
    if (confirm('检测到 cURL 命令，是否导入？')) {
      const parsed = parseCurl(text)
      if (parsed.error) { msg.error(parsed.error); return }
      store.setFromParsed(parsed)
      msg.success(`已导入 ${parsed.method} ${parsed.url.slice(0, 40)}…`)
    }
  }
}

function openCurlDialog() {
  curlInput.value = ''
  curlError.value = ''
  curlDialogOpen.value = true
}

function doCurlParse() {
  curlError.value = ''
  if (!curlInput.value.trim()) { curlError.value = '请输入 cURL 命令'; return }
  const parsed = parseCurl(curlInput.value)
  if (parsed.error) { curlError.value = parsed.error; return }
  store.setFromParsed(parsed)
  curlDialogOpen.value = false
  msg.success(`已导入 ${parsed.method} ${parsed.url.slice(0, 50)}`)
}

function onDragOver(e: DragEvent) {
  e.preventDefault(); dragOver.value = true
}
function onDragLeave() { dragOver.value = false }
function onDrop(e: DragEvent) {
  e.preventDefault(); dragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (!file || !file.name.match(/\.(txt|sh|curl)$/i)) { msg.warning('请拖入 .txt / .sh / .curl 文件'); return }
  const reader = new FileReader()
  reader.onload = () => {
    const text = reader.result as string
    const parsed = parseCurl(text)
    if (parsed.error) { msg.error(parsed.error); return }
    store.setFromParsed(parsed)
    msg.success(`已从文件导入 ${parsed.method} ${parsed.url.slice(0, 40)}…`)
  }
  reader.readAsText(file)
}

function focusUrl() { urlInput.value?.focus() }
function copyResponse() {
  if (store.response) navigator.clipboard.writeText(store.response.body).catch(() => {})
}

// ─── 快捷键 ───
/*
 * 走 shortcuts/index.ts 的注册表而不是硬编码键位：
 * 这样设置页改了「发送 / 导入 cURL / 聚焦 URL」的键，这里会立刻跟着变。
 * scope='curl' 由快捷键表声明，KeepAlive 里失活的工具会自动让位。
 */
useKeyboardShortcut('curl.send', () => doSend(), { skipWhenEditing: false })
useKeyboardShortcut('curl.import', () => openCurlDialog(), { skipWhenEditing: true })
useKeyboardShortcut('curl.focusUrl', () => focusUrl(), { skipWhenEditing: false })

function statusTextClass(c: number): string {
  if (c >= 200 && c < 300) return 'suc'
  if (c >= 300 && c < 400) return 'inf'
  if (c >= 400 && c < 500) return 'war'
  return 'err'
}
</script>

<template>
  <div class="http-tool" @dragover="onDragOver" @dragleave="onDragLeave" @drop="onDrop" :class="{ 'drag-over': dragOver }">
    <!-- ─── Toolbar ─── -->
    <div class="toolbar">
      <select v-model="store.request.method" class="method-sel" aria-label="HTTP 方法">
        <option v-for="m in METHODS" :key="m" :value="m">{{ m }}</option>
      </select>
      <div class="url-wrap">
        <span class="method-dot" :style="{ background: methodColors[store.request.method] }"></span>
        <input
          ref="urlInput"
          v-model="store.request.url"
          class="url-input"
          placeholder="https://api.example.com/endpoint"
          @paste="handlePaste"
          aria-label="URL"
        />
      </div>
      <button class="btn-primary" :disabled="!store.request.url.trim() || loading" @click="doSend">
        <template v-if="loading">
          <span class="spinner"></span> 发送中…
        </template>
        <template v-else>发送 <Kbd :keys="['mod', 'Enter']" /></template>
      </button>
      <button v-if="loading" class="btn-ghost" @click="cancel">取消</button>
      <button class="btn-ghost" @click="openCurlDialog" title="从 cURL 命令导入">
        <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M5 10l-3 3 3 3M11 10l3 3-3 3"/><path d="M2 13h12M8 5v8"/></svg>
        cURL
      </button>
    </div>

    <!-- ─── Grid ─── -->
    <div class="grid">
      <!-- Left: Request -->
      <div class="req-col">
        <div class="card">
          <div class="card-header">
            <span class="card-label">请求头</span>
          </div>
          <div class="card-body no-pad">
            <HeaderEditor v-model:headers="store.request.headers" />
          </div>
        </div>
        <div class="card">
          <div class="card-header">
            <span class="card-label">请求体</span>
            <span v-if="['GET','HEAD'].includes(store.request.method)" class="card-hint">GET/HEAD 无请求体</span>
            <template v-else>
              <button class="card-btn" @click="store.request.body = JSON.stringify(JSON.parse(store.request.body || '{}'), null, 2)" :disabled="!store.request.body">格式化 JSON</button>
              <button class="card-btn" @click="store.request.body = ''">清空</button>
              <span class="card-meta">{{ store.request.body.length }} 字符</span>
            </template>
          </div>
          <div class="card-body">
            <CodeEditor v-if="!['GET','HEAD'].includes(store.request.method)"
              v-model="store.request.body"
              language="json"
              placeholder='{"key": "value"}'
              :readonly="['GET','HEAD'].includes(store.request.method)"
            />
          </div>
        </div>
      </div>

      <!-- Right: Response -->
      <div class="res-col">
        <div class="card">
          <div class="card-header">
            <span class="card-label">响应</span>
          </div>

          <!-- Empty state -->
          <div v-if="!store.response" class="card-body empty">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" stroke="var(--text-3)" stroke-width="1" opacity="0.35">
              <path d="M10 18h28M10 26h20M10 34h14"/><rect x="6" y="6" width="36" height="36" rx="4"/>
            </svg>
            <div class="empty-title">等待请求</div>
            <div class="empty-desc">填写 URL 后点击「发送」</div>
          </div>

          <template v-else>
            <!-- Status bar -->
            <div class="status-bar">
              <div class="status-left">
                <span class="status-badge" :class="statusTextClass(store.response.status)" :style="{ background: statusColor + '20', color: statusColor }">
                  {{ store.response.status }} {{ store.response.statusText }}
                </span>
                <span v-if="store.response.timeMs" class="stat">{{ store.response.timeMs }} ms</span>
                <span v-if="store.response.sizeBytes" class="stat">{{ responseSize }}</span>
              </div>
              <div class="status-right">
                <button class="card-btn" @click="copyResponse()" title="复制响应体">复制</button>
                <span v-if="store.response.error" class="error-tag">错误</span>
              </div>
            </div>

            <!-- Error banner -->
            <div v-if="store.response.error" class="err-banner">{{ store.response.error }}</div>

            <!-- Tabs -->
            <div class="tab-bar">
              <button class="tab" :class="{ active: responseTab === 'pretty' }" @click="responseTab = 'pretty'">Pretty</button>
              <button class="tab" :class="{ active: responseTab === 'raw' }" @click="responseTab = 'raw'">Raw</button>
              <button class="tab" :class="{ active: responseTab === 'headers' }" @click="responseTab = 'headers'">Headers</button>
            </div>

            <!-- Tab content -->
            <div class="tab-content">
              <pre v-if="responseTab === 'pretty'" class="res-pre">{{ prettyBody }}</pre>
              <pre v-else-if="responseTab === 'raw'" class="res-pre">{{ store.response.body }}</pre>
              <div v-else class="res-headers">
                <div v-for="h in resHeaders" :key="h.k" class="rh-row">
                  <span class="rh-key">{{ h.k }}</span>
                  <span class="rh-val">{{ h.v }}</span>
                </div>
                <div v-if="!resHeaders.length" class="empty-desc" style="padding:20px">无响应头</div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- ─── cURL 导入弹窗 ─── -->
    <Teleport to="body">
      <div v-if="curlDialogOpen" class="modal-overlay" @click.self="curlDialogOpen = false">
        <div class="modal-panel">
          <h3 class="modal-title">导入 cURL 命令</h3>
          <div style="height:180px;display:flex;flex-direction:column">
            <CodeEditor v-model="curlInput" language="plaintext" placeholder="粘贴 cURL 命令…&#10;curl https://api.example.com -H '...' -d '...'" />
          </div>
          <div v-if="curlError" class="modal-err">{{ curlError }}</div>
          <div class="modal-actions">
            <button class="btn-primary" @click="doCurlParse">解析并填充</button>
            <button class="btn-ghost" @click="curlDialogOpen = false">取消</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.http-tool { height: 100%; display: flex; flex-direction: column; gap: 8px; padding: 12px 16px; }
.http-tool.drag-over { outline: 2px dashed var(--brand); outline-offset: -4px; border-radius: 8px; background: var(--brand-soft); }

/* ─── Toolbar ─── */
.toolbar { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.method-sel {
  height: 36px; padding: 0 10px; border: 1px solid var(--border); border-radius: 8px;
  background: var(--bg-card, #FFF); color: var(--text-1, #2D2528); font-size: 13px;
  font-weight: 600; cursor: pointer; outline: none; font-family: inherit;
}
.url-wrap {
  flex: 1; display: flex; align-items: center; gap: 8px;
  height: 36px; padding: 0 12px;
  border: 1px solid var(--border); border-radius: 8px;
  background: var(--bg-card);
  transition: border-color 0.15s;
}
.url-wrap:focus-within { border-color: var(--brand); }
.method-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.url-input {
  flex: 1; border: none; outline: none; background: transparent;
  font-size: 13px; color: var(--text-1, #2D2528); font-family: inherit;
}
.url-input::placeholder { color: var(--text-3); }

.btn-primary {
  display: inline-flex; align-items: center; gap: 4px; height: 36px; padding: 0 16px;
  border: none; border-radius: 8px;
  background: linear-gradient(135deg, var(--brand), var(--brand-hover));
  color: #FFF; font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit;
  white-space: nowrap; transition: box-shadow 0.15s, transform 0.1s;
}
.btn-primary:hover { box-shadow: var(--shadow-card-hover); transform: translateY(-1px); }
.btn-primary:disabled { opacity: 0.45; cursor: not-allowed; transform: none; box-shadow: none; }
.spinner { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,0.3); border-top-color: #FFF; border-radius: 50%; animation: spin 0.6s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.btn-ghost {
  display: inline-flex; align-items: center; gap: 4px; height: 36px; padding: 0 12px;
  border: 1px solid var(--border); border-radius: 8px;
  background: var(--bg-card, #FFF); color: var(--text-2, #6A5A60);
  font-size: 13px; cursor: pointer; font-family: inherit; transition: all 0.15s;
}
.btn-ghost:hover { border-color: var(--brand); color: var(--brand); }

/* ─── Grid ─── */
.grid { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; min-height: 0; }
.req-col, .res-col { display: flex; flex-direction: column; gap: 10px; min-height: 0; }

.card {
  display: flex; flex-direction: column;
  border: 1px solid var(--border); border-radius: 10px;
  background: var(--bg-card, #FFF);
  overflow: hidden;
}
.card-header {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px; flex-shrink: 0;
  border-bottom: 1px solid var(--border);
}
.card-label { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.4px; color: var(--text-2, #6A5A60); }
.card-hint { font-size: 12px; color: var(--text-3); margin-left: auto; }
.card-btn {
  font-size: 11px; padding: 2px 8px; border: 1px solid var(--border); border-radius: 5px;
  background: transparent; color: var(--text-2, #6A5A60); cursor: pointer; font-family: inherit;
  transition: all 0.15s;
}
.card-btn:hover { border-color: var(--brand); color: var(--brand); }
.card-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.card-meta { font-size: 11px; color: var(--text-3); font-variant-numeric: tabular-nums; }
.card-body { flex: 1; padding: 8px 14px; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.card-body.no-pad { padding: 0; }

/* Empty state */
.empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; padding: 40px 20px; }
.empty-title { font-size: 14px; font-weight: 600; color: var(--text-2, #6A5A60); }
.empty-desc { font-size: 13px; color: var(--text-3); }

/* Response status bar */
.status-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 14px; border-bottom: 1px solid var(--border); flex-shrink: 0;
}
.status-left { display: flex; align-items: center; gap: 12px; }
.status-right { display: flex; align-items: center; gap: 6px; }
.status-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 10px; border-radius: 20px;
  font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums;
}
.status-badge.suc { background: color-mix(in srgb, var(--state-success) 18%, transparent); color: var(--success-text); }
.status-badge.inf { background: var(--brand-soft); color: var(--brand); }
.status-badge.war { background: var(--state-warning-soft); color: var(--warning-text); }
.status-badge.err { background: var(--danger-soft); color: var(--danger-text); }
.stat { font-size: 11px; color: var(--text-3); font-variant-numeric: tabular-nums; }
.error-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; background: var(--danger-soft); color: var(--danger-text); }
.err-banner { padding: 8px 14px; font-size: 12px; color: var(--danger-text); background: var(--danger-soft); border-bottom: 1px solid var(--border); }

/* Tabs */
.tab-bar { display: flex; border-bottom: 1px solid var(--border); flex-shrink: 0; padding: 0 14px; }
.tab {
  padding: 8px 14px; font-size: 12px; font-weight: 500;
  border: none; background: transparent; color: var(--text-2, #6A5A60);
  cursor: pointer; position: relative; font-family: inherit;
  transition: color 0.15s;
}
.tab:hover { color: var(--text-1, #2D2528); }
.tab.active { color: var(--brand); }
.tab.active::after {
  content: ''; position: absolute; bottom: 0; left: 14px; right: 14px;
  height: 2px; background: var(--brand); border-radius: 2px;
}
.tab-content { flex: 1; overflow-y: auto; }
.res-pre {
  margin: 0; padding: 12px 14px; white-space: pre-wrap; word-break: break-all;
  font-family: var(--font-mono); font-size: 12px; line-height: 1.6;
  color: var(--text-1, #2D2528);
}
.res-headers { padding: 8px 14px; }
.rh-row { display: flex; gap: 12px; padding: 4px 0; font-size: 12px; font-family: var(--font-mono); }
.rh-key { flex-shrink: 0; font-weight: 600; color: var(--brand); }
.rh-val { color: var(--text-1, #2D2528); word-break: break-all; }

/* ─── cURL Import Modal ─── */
.modal-overlay {
  position: fixed; inset: 0; z-index: 9998;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.35); backdrop-filter: blur(4px);
}
.modal-panel {
  width: 540px; max-width: 90vw; padding: 24px;
  background: var(--bg-card, #FFF); border-radius: 14px;
  box-shadow: var(--shadow-popover);
}
.modal-title { margin: 0 0 14px; font-size: 16px; font-weight: 700; color: var(--text-1, #2D2528); }
.modal-ta {
  width: 100%; padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-1, #2D2528); font-family: var(--font-mono);
  font-size: 13px; line-height: 1.6; resize: vertical; outline: none;
}
.modal-ta:focus { border-color: var(--brand); }
.modal-err { font-size: 13px; color: var(--danger); margin-top: 8px; }
.modal-actions { display: flex; gap: 8px; margin-top: 14px; justify-content: flex-end; }

/* ─── 移动端：请求/响应改为上下堆叠 ─── */
@media (max-width: 900px) {
  .http-tool { padding: 8px 12px; gap: 6px; }
  .toolbar { flex-wrap: wrap; gap: 6px; }
  .url-wrap { min-width: 0; }
  .grid {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
    gap: 8px;
  }
  .card-header { padding: 6px 10px; flex-wrap: wrap; gap: 4px; }
  .card-body { padding: 6px 10px; }
  .card-btn { min-height: var(--tap-min, 32px); }
  .tab { min-height: var(--tap-min, 32px); padding: 0 12px; }
  .modal-panel { width: 100%; max-width: 100%; padding: 16px; }
}

@media (max-width: 767px) {
  .method-sel { min-width: 78px; }
  .btn-primary, .btn-ghost { height: var(--tap-min, 32px); }
}
</style>
