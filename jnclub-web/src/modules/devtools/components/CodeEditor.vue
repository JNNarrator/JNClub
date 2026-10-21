<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'

const props = defineProps<{
  modelValue: string
  language: 'json' | 'java' | 'yaml' | 'toml' | 'xml' | 'csv' | 'properties' | 'sql' | 'plaintext'
  placeholder?: string
  readonly?: boolean
  errorLine?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const highlightRef = ref<HTMLDivElement | null>(null)
const gutterRef = ref<HTMLDivElement | null>(null)
const focused = ref(false)

const lines = computed(() => {
  const n = (props.modelValue.match(/\n/g) || []).length + 1
  return Array.from({ length: n }, (_, i) => i + 1)
})

const charCount = computed(() => props.modelValue.length)
const lineCount = computed(() => lines.value.length)

function syncScroll() {
  if (gutterRef.value && textareaRef.value) {
    gutterRef.value.scrollTop = textareaRef.value.scrollTop
  }
  if (highlightRef.value && textareaRef.value) {
    highlightRef.value.scrollTop = textareaRef.value.scrollTop
    highlightRef.value.scrollLeft = textareaRef.value.scrollLeft
  }
}

function onInput(e: Event) {
  const raw = (e.target as HTMLTextAreaElement).value
  emit('update:modelValue', /<[^>]*>/.test(raw) || /"hl-\w+/.test(raw) ? stripHtml(raw) : raw)
}

function getIndent(line: string): string {
  const m = line.match(/^(\s*)/)
  return m ? m[1] : ''
}

function onKeydown(e: KeyboardEvent) {
  const ta = textareaRef.value!
  if (e.key === 'Tab') {
    e.preventDefault()
    const start = ta.selectionStart
    const end = ta.selectionEnd
    const val = props.modelValue
    const newVal = val.substring(0, start) + '  ' + val.substring(end)
    emit('update:modelValue', newVal)
    nextTick(() => { ta.selectionStart = ta.selectionEnd = start + 2 })
  } else if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
    e.preventDefault()
    const start = ta.selectionStart
    const val = props.modelValue
    const lineStart = val.lastIndexOf('\n', start > 0 ? start - 1 : 0) + 1
    const currentLine = val.slice(lineStart, val.indexOf('\n', start) === -1 ? val.length : val.indexOf('\n', start))
    const indent = getIndent(currentLine)
    if (/:$/.test(currentLine.trim())) {
      emit('update:modelValue', val.slice(0, start) + '\n' + indent + '  ' + val.slice(ta.selectionEnd))
      nextTick(() => { ta.selectionStart = ta.selectionEnd = start + 1 + indent.length + 2 })
    } else {
      emit('update:modelValue', val.slice(0, start) + '\n' + indent + val.slice(ta.selectionEnd))
      nextTick(() => { ta.selectionStart = ta.selectionEnd = start + 1 + indent.length })
    }
  }
}

function getCursorLine(): number {
  const ta = textareaRef.value
  if (!ta) return 1
  return (props.modelValue.substring(0, ta.selectionStart).match(/\n/g) || []).length + 1
}

const cursorLine = ref(1)
function onCursorMove() { cursorLine.value = getCursorLine() }

/* ─── 语法高亮：escape → tokenize ───
 * 铁律：文本内容先 escape 再包 span，前缀后缀完整闭合，纯函数无副作用。
 * escape 顺序 & → < >（& 必须最先）。
 * 仅在检测到高亮残片时做针对性标签剥离，不盲剥。
 */
function stripHtml(s: string): string {
  let t = s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&').replace(/&quot;/g, '"')
  t = t.replace(/<[^>]*>/g, '')
  t = t.replace(/(?:class|style)\s*=\s*"[^"]*"\s*/g, '')
  t = t.replace(/"hl-\w+"?>?/g, '')
  return t
}

function highlight(text: string): string {
  if (!text) return ''
  if (/<span\s|<\/span>|"hl-\w+|&quot;hl-|&lt;span/i.test(text)) {
    text = stripHtml(text)
  }
  let escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  if (props.language === 'json') return highlightJson(escaped)
  if (props.language === 'java') return highlightJava(escaped)
  if (props.language === 'yaml') return highlightYaml(escaped)
  if (props.language === 'toml') return highlightToml(escaped)
  if (props.language === 'xml') return highlightXml(escaped)
  if (props.language === 'csv') return highlightCsv(escaped)
  if (props.language === 'sql') return highlightSql(escaped)
  if (props.language === 'properties') return highlightProperties(escaped)
  return escaped  // plaintext
}

/* ─── JSON ─── */
function highlightJson(s: string): string {
  s = s.replace(/"([^"\\]*(?:\\.[^"\\]*)*)"/g, '<span class="hl-string">"$1"</span>')
  s = s.replace(/\b(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b/g, '<span class="hl-number">$1</span>')
  s = s.replace(/\b(true|false|null)\b/g, '<span class="hl-keyword">$1</span>')
  return s
}

/* ─── Java ─── */
function highlightJava(s: string): string {
  s = s.replace(/"([^"\\]*(?:\\.[^"\\]*)*)"/g, '<span class="hl-string">"$1"</span>')
  s = s.replace(/'[^'\\]*(?:\\.[^'\\]*)*'/g, '<span class="hl-string">$&</span>')
  const kw = /\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|var)\b/g
  s = s.replace(kw, '<span class="hl-keyword">$1</span>')
  s = s.replace(/@[a-zA-Z_$][a-zA-Z0-9_$.]*/g, '<span class="hl-annotation">$&</span>')
  s = s.replace(/\b(-?\d+(?:\.\d+)?[lLfFdD]?)\b/g, '<span class="hl-number">$1</span>')
  s = s.replace(/\b([A-Z][a-zA-Z0-9_<>]+)\b/g, (m) => {
    if (/^(String|Integer|Boolean|Long|Double|Float|Byte|Short|Character|Void|Object|List|Map|Set|Collection|BigDecimal|BigInteger|Date|LocalDate|LocalDateTime|Instant|UUID)$/.test(m))
      return `<span class="hl-type">${m}</span>`
    return m
  })
  s = s.replace(/(\/\/.*)/g, '<span class="hl-comment">$1</span>')
  s = s.replace(/\/\*[\s\S]*?\*\//g, '<span class="hl-comment">$&</span>')
  return s
}

/* ─── YAML ─── */
function highlightYaml(s: string): string {
  s = s.replace(/"([^"\\]*(?:\\.[^"\\]*)*)"/g, '<span class="hl-string">"$1"</span>')
  s = s.replace(/'[^']*'/g, '<span class="hl-string">$&</span>')
  s = s.replace(/\b(true|false|yes|no|on|off|null|~)\b/g, '<span class="hl-keyword">$1</span>')
  s = s.replace(/\b(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b/g, '<span class="hl-number">$1</span>')
  s = s.replace(/(^|\s)(#[^\n]*)/gm, '$1<span class="hl-comment">$2</span>')
  s = s.replace(/^([ \t]*)([a-zA-Z_][\w .-]*?)(\s*:)/gm, '$1<span class="hl-attr">$2</span>$3')
  s = s.replace(/([&*]\w[\w-]*)/g, '<span class="hl-annotation">$1</span>')
  s = s.replace(/(!!?\w+(?:[.\w]*)?)/g, '<span class="hl-keyword">$1</span>')
  s = s.replace(/^([ \t]*)(- )(?!\s)/gm, '$1<span class="hl-punct">$2</span>')
  return s
}

/* ─── TOML ─── */
function highlightToml(s: string): string {
  s = s.replace(/"""(?:[^"\\]|\\.)*?"""/g, '<span class="hl-string">$&</span>')
  s = s.replace(/'''[\s\S]*?'''/g, '<span class="hl-string">$&</span>')
  s = s.replace(/"([^"\\]*(?:\\.[^"\\]*)*)"/g, '<span class="hl-string">"$1"</span>')
  s = s.replace(/'[^']*'/g, '<span class="hl-string">$&</span>')
  s = s.replace(/\b(true|false)\b/g, '<span class="hl-keyword">$1</span>')
  s = s.replace(/\b([+-]?\d+(?:_\d+)*(?:\.\d+(?:_\d+)*)?(?:[eE][+-]?\d+(?:_\d+)*)?)\b/g, '<span class="hl-number">$1</span>')
  s = s.replace(/\b(\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})?)?)\b/g, '<span class="hl-string">$1</span>')
  s = s.replace(/(\[+[^\]]*\]+)/g, '<span class="hl-punct">$1</span>')
  s = s.replace(/([a-zA-Z_][\w.-]*)(\s*=)/g, '<span class="hl-attr">$1</span>$2')
  s = s.replace(/(#[^\n]*)/g, '<span class="hl-comment">$1</span>')
  return s
}

/* ─── XML ─── */
function highlightXml(s: string): string {
  s = s.replace(/(&lt;!--[\s\S]*?--&gt;)/g, '<span class="hl-comment">$1</span>')
  s = s.replace(/(&lt;\?[\s\S]*?\?&gt;)/g, '<span class="hl-keyword">$1</span>')
  s = s.replace(/(&lt;!\[CDATA\[[\s\S]*?\]\]&gt;)/g, '<span class="hl-keyword">$1</span>')
  s = s.replace(/(&lt;\/?)([a-zA-Z_:][\w:.-]*)/g, '$1<span class="hl-tag">$2</span>')
  s = s.replace(/(\/?&gt;)/g, '<span class="hl-punct">$1</span>')
  s = s.replace(/\s([a-zA-Z_:][\w:.-]*)(=)/g, ' <span class="hl-attr">$1</span>$2')
  s = s.replace(/(="[^"]*")/g, '=<span class="hl-string">$1</span>')
  s = s.replace(/(='[^']*')/g, '=<span class="hl-string">$1</span>')
  return s
}

/* ─── CSV ─── */
function highlightCsv(s: string): string {
  const lines = s.split('\n')
  if (lines.length > 0)
    lines[0] = lines[0].replace(/([^,\n"]+|"[^"]*")/g, '<span class="hl-attr">$1</span>')
  for (let i = 1; i < lines.length; i++) {
    lines[i] = lines[i]
      .replace(/("[^"]*")/g, '<span class="hl-string">$1</span>')
      .replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="hl-number">$1</span>')
      .replace(/\b(true|false|null)\b/g, '<span class="hl-keyword">$1</span>')
  }
  return lines.join('\n')
}

/* ─── .properties ─── */
function highlightProperties(s: string): string {
  s = s.replace(/^([ \t]*[#!][^\n]*)/gm, '<span class="hl-comment">$1</span>')
  s = s.replace(/^([a-zA-Z0-9_.\[\]\\][\w.\[\]\\]*)(\s*[=:])/gm, '<span class="hl-attr">$1</span>$2')
  s = s.replace(/([=:]\s*)([^\n#]+)/g, '$1<span class="hl-string">$2</span>')
  return s
}

/* ─── SQL ─── */
function highlightSql(s: string): string {
  const keywords = /\b(ADD|ALL|ALTER|AND|ANY|AS|ASC|BEGIN|BETWEEN|BIGINT|BINARY|BIT|BLOB|BOOLEAN|BY|CASCADE|CASE|CHANGE|CHAR|CHARACTER|CHECK|COALESCE|COLLATE|COLUMN|COMMIT|CONSTRAINT|CONVERT|CREATE|CROSS|CURRENT_TIMESTAMP|CURRENT_DATE|CURRENT_TIME|DATABASE|DATE|DATETIME|DECIMAL|DECLARE|DEFAULT|DELETE|DESC|DESCRIBE|DISTINCT|DOUBLE|DROP|ELSE|END|ENUM|ESCAPE|EXISTS|EXPLAIN|FLOAT|FOREIGN|FOR|FROM|FULL|FUNCTION|GROUP|HAVING|IDENTITY|IF|IN|INDEX|INNER|INSERT|INT|INTEGER|INTERSECT|INTO|INOUT|IS|JOIN|KEY|LEFT|LIKE|LIMIT|LONGBLOB|LONGTEXT|MATCHED|MEDIUMBLOB|MEDIUMINT|MEDIUMTEXT|MODIFY|NOT|NULL|NUMERIC|ON|OPTION|OR|ORDER|OUT|OUTER|PARTITION|PLAIN|PRECISION|PRIMARY|PROCEDURE|RANGE|REAL|REFERENCES|REGEXP|RENAME|REPLACE|RIGHT|ROLLBACK|ROW|ROWS|SCHEMA|SELECT|SESSION|SET|SHOW|SMALLINT|SOME|START|TABLE|TEXT|THEN|TIME|TIMESTAMP|TINYBLOB|TINYINT|TINYTEXT|TO|TRANSACTION|TRIGGER|TRUNCATE|UNION|UNIQUE|UNSIGNED|UPDATE|USAGE|USE|USING|VALUES|VARCHAR|VARYING|VIEW|WHEN|WHERE|WHILE|WITH|WRITE|YEAR|ZEROFILL)\b/gi
  s = s.replace(/'(?:[^'\\]|\\.)*'/g, '<span class="hl-string">$&</span>')
  s = s.replace(/`[^`]+`/g, '<span class="hl-string">$&</span>')
  s = s.replace(/\b(-?\d+(?:\.\d+)?)\b/g, '<span class="hl-number">$1</span>')
  s = s.replace(keywords, '<span class="hl-keyword">$1</span>')
  s = s.replace(/(--[\s\S]*?)(?:\n|$)/g, '<span class="hl-comment">$1</span>')
  s = s.replace(/\/\*[\s\S]*?\*\//g, '<span class="hl-comment">$&</span>')
  return s
}

const highlighted = computed(() => highlight(props.modelValue))

const LANG_LABEL: Record<string, string> = {
  json: 'JSON', java: 'Java', yaml: 'YAML', toml: 'TOML',
  xml: 'XML', csv: 'CSV', properties: 'Properties', sql: 'SQL', plaintext: 'Text',
}
const langLabel = computed(() => LANG_LABEL[props.language] || 'Text')

defineExpose({ charCount, lineCount, cursorLine, textareaRef })

function handlePaste() {
  navigator.clipboard.readText().then(t => emit('update:modelValue', t)).catch(() => {})
}
</script>

<template>
  <div class="editor-wrap" :class="{ focused, readonly, error: errorLine !== null }">
    <div class="editor-header">
      <div class="header-left">
        <span class="header-label">{{ langLabel }}</span>
        <span v-if="!readonly" class="header-meta">{{ charCount }} 字符 · {{ lineCount }} 行</span>
        <span v-else class="header-meta">输出</span>
      </div>
      <div class="header-right" v-if="!readonly">
        <button class="icon-btn" title="粘贴" @click="handlePaste">
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M8 2H5a1 1 0 00-1 1v14a1 1 0 001 1h10a1 1 0 001-1V3a1 1 0 00-1-1h-3"/>
            <rect x="8" y="2" width="4" height="3" rx="1"/>
          </svg>
        </button>
      </div>
    </div>
    <div class="editor-body">
      <div class="gutter" ref="gutterRef">
        <div v-for="(n, i) in lines" :key="i" class="line-num"
          :class="{ current: n === cursorLine, error: n === errorLine }">
          <template v-if="errorLine && n === errorLine">✗ {{ n }}</template>
          <template v-else>{{ n }}</template>
        </div>
      </div>
      <div class="highlight-layer" ref="highlightRef" :class="{ 'is-readonly': readonly }">
        <pre class="highlight-pre"><code class="highlight-code" v-html="highlighted"></code></pre>
      </div>
      <textarea v-if="!readonly" ref="textareaRef" :value="modelValue"
        class="editor-textarea" spellcheck="false"
        @input="onInput" @keydown="onKeydown"
        @scroll="syncScroll"
        @focus="focused = true" @blur="focused = false"
        @click="onCursorMove" @keyup="onCursorMove"
        :placeholder="placeholder || '粘贴或输入…'" />
    </div>
  </div>
</template>

<style scoped>
.editor-wrap {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-card);
  transition: border-color 0.2s, box-shadow 0.2s;
  overflow: hidden;
}
.editor-wrap.focused { border-color: var(--brand); box-shadow: 0 0 0 2px color-mix(in srgb, var(--brand) 15%, transparent); }
.editor-wrap.error { border-color: var(--danger); }

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  background: var(--hover-bg);
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header-label { font-size: 12px; font-weight: 600; color: var(--text-2); text-transform: uppercase; letter-spacing: 0.4px; }
.header-meta { font-size: 11px; color: var(--text-2); }
.header-right { display: flex; gap: 4px; }

.icon-btn {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border: none; border-radius: 6px;
  background: transparent; color: var(--text-2);
  cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { background: color-mix(in srgb, var(--brand) 10%, transparent); color: var(--brand); }
.icon-btn svg { width: 16px; height: 16px; }

.editor-body {
  display: flex;
  flex: 1; min-height: 0;
  overflow: hidden;
  position: relative;
}

.gutter {
  width: 44px; flex-shrink: 0;
  background: var(--hover-bg);
  overflow: hidden;
  padding: 8px 0;
  text-align: right;
  border-right: 1px solid var(--border);
  z-index: 2;
}
.line-num {
  height: 22px; line-height: 22px;
  padding-right: 8px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}
.line-num.current { color: var(--brand); font-weight: 600; }
.line-num.error { color: var(--danger); font-weight: 700; background: color-mix(in srgb, var(--danger) 8%, transparent); }

.highlight-layer {
  position: absolute;
  top: 0; left: 44px; right: 0; bottom: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 1;
}
.highlight-layer.is-readonly {
  pointer-events: auto;
  overflow: auto;
}

.highlight-pre {
  font-family: var(--font-mono);
  font-size: 13px; line-height: 22px;
  padding: 8px 14px;
  margin: 0;
  tab-size: 2;
  white-space: pre-wrap;
  word-wrap: break-word;
  min-height: 100%;
  color: transparent;
}

.highlight-code { color: var(--text-2); }

/* Syntax highlighting tokens */
.highlight-code :deep(.hl-string) { color: var(--brand); }
.highlight-code :deep(.hl-number) { color: var(--state-success); }
.highlight-code :deep(.hl-keyword) { color: var(--brand); font-weight: 500; }
.highlight-code :deep(.hl-annotation) { color: var(--text-3); }
.highlight-code :deep(.hl-type) { color: var(--text-3); }
.highlight-code :deep(.hl-attr) { color: var(--brand); }
.highlight-code :deep(.hl-tag) { color: var(--brand); }
.highlight-code :deep(.hl-punct) { color: var(--text-2); }
.highlight-code :deep(.hl-comment) { color: var(--text-3); font-style: italic; }

.editor-textarea {
  position: absolute;
  top: 0; left: 44px; right: 0; bottom: 0;
  padding: 8px 14px;
  border: none; outline: none; resize: none;
  font-family: var(--font-mono);
  font-size: 13px; line-height: 22px;
  color: transparent;
  caret-color: var(--text-2);
  background: transparent;
  tab-size: 2;
  overflow-y: auto;
  z-index: 3;
}
.editor-textarea::selection { background: color-mix(in srgb, var(--brand) 30%, transparent); }
.editor-textarea::placeholder { color: var(--text-3); }
.editor-textarea::-webkit-scrollbar { width: 5px; }
.editor-textarea::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
</style>
