<script setup lang="ts">
/**
 * NoteEditor.vue — vditor Markdown 编辑器封装
 * 默认 ir 即时渲染模式（Typora 风格），toolbar 内置 edit-mode / both / preview 页内切换
 * 能力：自动保存（3s）+ Ctrl/⌘+S + 保存状态指示 + 空态引导 + 快捷键帮助 + 图片上传
 * 自托管 cdn（public/vendor/vditor），不依赖 unpkg 外网
 */
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { NButton, NIcon, NInput, NModal, NSwitch, useMessage, useDialog } from 'naive-ui'
import { Keyboard, ArrowLeft, CheckCircle2, CloudOff, LoaderCircle } from 'lucide-vue-next'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import axios from 'axios'
import type { Note } from '../stores/note'

const props = defineProps<{
  note: Note | null
  isDark: boolean
}>()

const emit = defineEmits<{
  close: []
  saved: [note: Note]
  deleted: [note: Note]
}>()

const message = useMessage()
const dialog = useDialog()

const vditorEl = ref<HTMLDivElement | null>(null)
let vditor: Vditor | null = null
/** 保存成功后父组件回写 note 触发的 id 变化，无需重建编辑器（内容已在 vditor 内） */
let skipNextSync = false
/** 只读模式：查看已有便签默认只读，新建默认可编辑 */
const readonlyMode = ref(props.note ? props.note.id !== 0 : true)
const isViewNote = computed(() => !!props.note && props.note.id !== 0)

const content = ref('')
const title = ref('')
const saving = ref(false)
const autoSaveTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const hasUnsavedChanges = ref(false)

/** 保存状态：未保存 / 保存中 / 已保存（含自动保存） */
const saveState = ref<'idle' | 'dirty' | 'saving' | 'saved'>('idle')
const lastSavedAt = ref('')
const formatTime = (d: Date) => d.toTimeString().slice(0, 8)

/** 字数/行数统计 */
const charCount = computed(() => content.value.length)
const lineCount = computed(() => (content.value ? content.value.split('\n').length : 0))

/** 底部状态文字：最后修改时间 */
const statusTimeText = computed(() => {
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'saved') return `已保存 ${lastSavedAt.value}`
  if (saveState.value === 'dirty') return '编辑中…'
  return ''
})

/** 保存同步状态图标：自动保存已开启，按钮弱化为状态指示（点击可手动保存） */
const syncIcon = computed(() => {
  if (saveState.value === 'saving') return LoaderCircle
  if (saveState.value === 'saved') return CheckCircle2
  return CloudOff
})
const syncTitle = computed(() => {
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'saved') return `已同步 ${lastSavedAt.value} · 点击手动保存`
  if (saveState.value === 'dirty') return '未同步 · 自动保存中，点击可手动保存'
  return '点击手动保存'
})

/** 空态引导：内容为空时显示（新建文档不是白板） */
const showGuide = computed(() => !content.value.trim())

/** 只读→编辑切换时编辑框高亮动画 */
const editorFlash = ref(false)

/** 快捷键帮助弹窗 */
const showHelp = ref(false)
const shortcutRows = [
  { keys: 'Ctrl / ⌘ + S', desc: '手动保存' },
  { keys: 'Ctrl + B', desc: '加粗' },
  { keys: 'Ctrl + I', desc: '斜体' },
  { keys: 'Ctrl + K', desc: '插入链接' },
  { keys: 'Ctrl + Shift + C', desc: '代码块' },
  { keys: 'Ctrl + Z / Ctrl + Shift + Z', desc: '撤销 / 重做' },
]

/** 外部 note 变化（切换便签/加载）→ 同步内容；保存回写（同 id 或 skipNextSync）不重建编辑器 */
watch(() => props.note?.id, (newId, oldId) => {
  if (!props.note) return
  content.value = props.note.content || ''
  title.value = props.note.title || ''
  hasUnsavedChanges.value = false
  saveState.value = 'idle'
  lastSavedAt.value = ''
  if (vditor && newId !== oldId && !skipNextSync) {
    vditor.setValue(content.value, true)
  }
  skipNextSync = false
}, { immediate: true })

/** 暗色主题切换 */
watch(() => props.isDark, (dark) => {
  if (vditor) vditor.setTheme(dark ? 'dark' : 'classic')
})

/** 图片上传：vditor handler 语义——返回 null 表示成功、字符串视为错误提示；图片由 handler 内部插入编辑器 */
const handleUploadImg = async (files: File[]): Promise<string | null> => {
  if (!files.length) return null
  try {
    const formData = new FormData()
    formData.append('file', files[0])
    const res = await axios.post('/api/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (res.data.code === 200) {
      const url = res.data.data.url
      vditor?.insertMD(`![${files[0].name}](${url})`)
      // insertMD 不触发 vditor input 回调：手动同步内容并触发自动保存
      if (vditor) {
        const md = vditor.getValue()
        handleEditorInput(md)
      }
      return null
    }
    return res.data.message || '上传失败'
  } catch (e: any) {
    return e.response?.status === 401 ? '请先登录' : (e.response?.data?.message || '图片上传失败')
  }
}

/** vditor 输入 → 触发自动保存 */
const handleEditorInput = (value: string) => {
  content.value = value
  hasUnsavedChanges.value = true
  saveState.value = 'dirty'
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  // 内容为空时不自动保存 — 等用户真正输入了再入库
  if (!value.trim()) return
  autoSaveTimer.value = setTimeout(() => handleSave(true), 3000)
}

const handleTitleChange = () => {
  if (readonlyMode.value) return
  hasUnsavedChanges.value = true
  saveState.value = 'dirty'
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  if (!title.value.trim() && !content.value.trim()) return
  autoSaveTimer.value = setTimeout(() => handleSave(true), 3000)
}

/** 保存 */
const handleSave = async (silent = false) => {
  if (readonlyMode.value) return
  if (!props.note || saving.value) return
  if (!hasUnsavedChanges.value && silent) return

  // 内容门槛：标题和内容都为空时，不保存
  if (!title.value.trim() && !content.value.trim()) return

  saving.value = true
  saveState.value = 'saving'
  try {
    const isNew = props.note.id === 0
    if (isNew) {
      // 新建便签 — POST 创建
      const res = await axios.post('/api/notes', {
        title: title.value || '',
        content: content.value,
        directoryId: props.note.directoryId,
      })
      if (res.data.code === 200 && res.data.data) {
        hasUnsavedChanges.value = false
        saveState.value = 'saved'
        lastSavedAt.value = formatTime(new Date())
        skipNextSync = true // 本次保存回写不再重建编辑器
        if (!silent) message.success('已保存')
        emit('saved', res.data.data as Note)
      } else {
        saveState.value = 'dirty'
        message.error(res.data.message || '创建失败')
      }
    } else {
      // 已有便签 — PUT 更新
      await axios.put(`/api/notes/${props.note.id}`, {
        title: title.value || '',
        content: content.value,
      })
      hasUnsavedChanges.value = false
      saveState.value = 'saved'
      lastSavedAt.value = formatTime(new Date())
      skipNextSync = true // 本次保存回写不再重建编辑器
      if (!silent) message.success('已保存')
      emit('saved', { ...props.note, title: title.value, content: content.value } as Note)
    }
  } catch (e: any) {
    saveState.value = 'dirty'
    message.error(e.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

/** 关闭前检查未保存（主动对比 vditor 内容，防 input 回调防抖延迟漏判） */
const handleRequestClose = () => {
  if (vditor) {
    const current = vditor.getValue()
    if (current !== content.value) {
      content.value = current
      hasUnsavedChanges.value = true
      saveState.value = 'dirty'
    }
  }
  if (hasUnsavedChanges.value) {
    dialog.warning({
      title: '未保存的修改',
      content: '有未保存的修改，确定放弃吗？',
      positiveText: '放弃',
      negativeText: '继续编辑',
      onPositiveClick: () => {
        hasUnsavedChanges.value = false
        emit('close')
      },
    })
  } else {
    emit('close')
  }
}

/** 编辑类工具栏按钮（只读时禁用，与 vditor 内置 EDIT_TOOLBARS 一致） */
const EDIT_TOOLBAR_KEYS = ['emoji', 'headings', 'bold', 'italic', 'strike', 'link', 'list',
  'ordered-list', 'outdent', 'indent', 'check', 'line', 'quote', 'code', 'inline-code',
  'insert-after', 'insert-before', 'upload', 'record', 'table']

/** 只读/可编辑切换：只读时隐藏编辑区、显示 vditor 预览面板（排版渲染），并禁用编辑类工具栏 */
const applyReadonly = () => {
  if (!vditor) return
  const v = vditor.vditor
  const modeKey = v.currentMode
  const editWrap = (v as any)[modeKey]?.element?.parentElement as HTMLElement | undefined
  const previewEl = v.preview?.element as HTMLElement | undefined
  if (!editWrap || !previewEl) return
  if (readonlyMode.value) {
    editWrap.style.display = 'none'
    previewEl.style.display = 'block'
    v.preview?.render(v) // getMarkdown → lute 渲染排版（正常文档样式）
  } else {
    editWrap.style.display = 'block'
    previewEl.style.display = 'none'
  }
  // 只读（纯预览）时隐藏预览操作栏与工具栏（CSS 层兜底）
  const els = v.toolbar?.elements
  if (els) {
    EDIT_TOOLBAR_KEYS.forEach((k) => {
      els[k]?.firstElementChild?.classList.toggle('vditor-menu--disabled', readonlyMode.value)
    })
  }
}
const toggleReadonly = () => {
  readonlyMode.value = !readonlyMode.value
  applyReadonly()
  // 进入编辑模式时触发一次编辑框高亮动画
  if (!readonlyMode.value) {
    editorFlash.value = false
    requestAnimationFrame(() => { editorFlash.value = true })
  }
  if (!readonlyMode.value && vditor) {
    const el = (vditor.vditor as any)?.[vditor.vditor.currentMode]?.element as HTMLElement | undefined
    el?.focus()
  }
}

/** Ctrl/Cmd+S 快捷保存 */
const handleKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') { e.preventDefault(); handleSave() }
}

const vditorToolbar: Array<string> = [
  'undo', 'redo', '|',
  'headings', 'bold', 'italic', 'strike', 'link', '|',
  'list', 'ordered-list', 'check', '|',
  'quote', 'code', 'inline-code', 'table', '|',
  'upload', '|', 'fullscreen', 'edit-mode', 'both', 'preview', '|', 'outline',
]

onMounted(() => {
  if (!vditorEl.value) return
  vditor = new Vditor(vditorEl.value, {
    mode: 'ir',
    theme: props.isDark ? 'dark' : 'classic',
    icon: 'ant',
    lang: 'zh_CN',
    cdn: `${import.meta.env.BASE_URL}vendor/vditor`,
    placeholder: '支持 Markdown 语法，试试输入 # 标题，或 ** 加粗',
    cache: { enable: false },
    height: '100%',
    outline: { enable: true, position: 'left' },
    // 预览操作栏：编辑模式可用；只读（纯预览）时在 applyReadonly 中隐藏
    preview: { actions: ['desktop', 'tablet', 'mobile', 'mp-wechat', 'zhihu'] },
    value: content.value,
    toolbar: vditorToolbar,
    input: handleEditorInput,
    upload: {
      handler: handleUploadImg as (files: File[]) => string | null | Promise<string> | Promise<null>,
    },
    keydown: (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 's') { e.preventDefault(); handleSave() }
    },
    after: () => { applyReadonly() },
  })
})

onBeforeUnmount(() => {
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  if (vditor) {
    try { vditor.destroy() } catch { /* 忽略 */ }
    vditor = null
  }
})

const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (hasUnsavedChanges.value) e.preventDefault()
}
if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('keydown', handleKeydown)
}
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('keydown', handleKeydown)
})

defineExpose({ hasUnsavedChanges })
</script>

<template>
  <div class="note-editor" :class="{ 'readonly-mode': readonlyMode }">
    <div class="editor-topbar">
      <NButton quaternary size="small" @click="handleRequestClose" title="返回列表（有未保存修改时二次确认）">
        <template #icon><NIcon :component="ArrowLeft" size="18" /></template>
        返回列表
      </NButton>
      <div class="editor-title-wrap">
        <NInput
          v-model:value="title"
          :bordered="false"
          placeholder="输入标题"
          class="editor-title-input"
          :disabled="readonlyMode"
          @keyup.enter.prevent
          @input="handleTitleChange"
        />
      </div>
      <div v-if="isViewNote" class="mode-toggle" title="只读 / 编辑切换">
        <span class="mode-toggle-label" :class="{ active: !readonlyMode }">编辑</span>
        <NSwitch :value="!readonlyMode" size="small" @update:value="toggleReadonly" />
      </div>
      <NButton quaternary circle size="small" title="快捷键帮助" @click="showHelp = true">
        <template #icon><NIcon :component="Keyboard" size="16" /></template>
      </NButton>
      <!-- 保存同步状态图标（自动保存已开启，点击手动保存） -->
      <NButton
        v-if="!readonlyMode"
        quaternary
        circle
        size="small"
        :title="syncTitle"
        :class="['sync-indicator', 'sync-' + saveState]"
        @click="handleSave()"
      >
        <NIcon :component="syncIcon" size="17" />
      </NButton>
    </div>

    <div class="editor-body" :class="{ flash: editorFlash }">
      <!-- vditor 挂载点 -->
      <div ref="vditorEl" class="vditor-wrap"></div>

      <!-- 空态引导：首次打开不是白板 -->
      <div v-if="showGuide" class="editor-guide">
        <div class="guide-card">
          <div class="guide-title">直接开始写作…</div>
          <div class="guide-tip">
            支持 Markdown 语法 · 试试输入 <code># 标题</code> 或 <code>**加粗**</code>
          </div>
          <button type="button" class="guide-help" @click="showHelp = true">查看快捷键帮助 →</button>
        </div>
      </div>
    </div>

    <!-- 底部状态栏：字数 / 时间 / 自动保存圆环（只读时提示解锁） -->
    <div class="editor-statusbar">
      <div class="statusbar-left">
        <span v-if="readonlyMode" class="statusbar-hint">只读模式 · 打开右侧「编辑」开关解锁</span>
        <template v-else>
          <span class="statusbar-stats">{{ charCount }} 字符 · {{ lineCount }} 行</span>
          <span v-if="statusTimeText" class="statusbar-time" :class="'time-' + saveState">{{ statusTimeText }}</span>
        </template>
      </div>
      <div class="statusbar-right">
        <!-- 自动保存 3 秒进度圆环：输入后倒计时、保存中转圈、完成后变绿 -->
        <svg v-if="!readonlyMode" class="save-ring" :class="'ring-' + saveState" viewBox="0 0 20 20" width="18" height="18">
          <circle class="ring-track" cx="10" cy="10" r="8" />
          <circle class="ring-bar" cx="10" cy="10" r="8" />
        </svg>
        <span class="statusbar-keys">Ctrl / ⌘ + S 手动保存</span>
      </div>
    </div>

    <!-- 快捷键帮助弹窗 -->
    <NModal v-model:show="showHelp" preset="card" title="快捷键帮助" style="width: 520px; max-width: 92vw;">
      <div class="help-list">
        <div v-for="row in shortcutRows" :key="row.keys" class="help-row">
          <span class="help-keys">{{ row.keys }}</span>
          <span class="help-desc">{{ row.desc }}</span>
        </div>
      </div>
      <div class="help-foot">
        支持 Markdown 语法：<code># 标题</code>、<code>**加粗**</code>、<code>&gt; 引用</code>、<code>``` 代码块</code>、<code>- 列表</code>、<code>1. 有序列表</code>、<code>| 表格 |</code>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.note-editor {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-card);
}
.editor-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.editor-title-wrap { flex: 1; min-width: 0; }
.editor-title-input {
  width: 100%;
  font-size: 16px;
  font-weight: 600;
}
:deep(.editor-title-input .n-input__input-el) {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
:deep(.editor-title-input .n-input__placeholder) {
  color: var(--text-4);
}

/* 编辑/只读 Toggle 开关 */
.mode-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.mode-toggle-label {
  font-size: 12px;
  color: var(--text-3);
  transition: color .18s ease;
}
.mode-toggle-label.active {
  color: var(--brand);
  font-weight: 600;
}

/* 保存同步状态图标 */
.sync-indicator :deep(.n-icon) { color: var(--text-3); }
.sync-dirty :deep(.n-icon) { color: #f59e0b; }
.sync-saved :deep(.n-icon) { color: #52c41a; }
.sync-saving :deep(.n-icon) {
  color: var(--brand);
  animation: sync-spin .9s linear infinite;
}
@keyframes sync-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 编辑区：vditor 撑满 */
.editor-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}
/* 只读→编辑切换：编辑框高亮动画（inset 描边，不影响布局） */
.editor-body.flash {
  animation: editor-enter .6s ease;
}
@keyframes editor-enter {
  0% { box-shadow: inset 0 0 0 2px var(--brand), 0 0 0 1px var(--brand-soft); }
  100% { box-shadow: inset 0 0 0 0 transparent; }
}
.vditor-wrap { height: 100%; }
:deep(.vditor) { height: 100% !important; border-radius: 0 !important; border: none !important; }
:deep(.vditor-toolbar) {
  background: var(--bg-card) !important;
  border-bottom: 1px solid var(--border) !important;
  padding: 4px 8px !important;
}
:deep(.vditor-toolbar__item button) { color: var(--text-2) !important; }
:deep(.vditor-toolbar__item button:hover) { color: var(--brand) !important; background: var(--hover-bg) !important; }
:deep(.vditor-toolbar__item button.vditor-menu--current) { color: var(--brand) !important; }
:deep(.vditor-ir) { background: var(--bg-card) !important; }
:deep(.vditor-ir__editor) { background: var(--bg-card) !important; }
:deep(.vditor-ir__editor .vditor-reset) { color: var(--text-1) !important; }
:deep(.vditor-preview) { background: var(--bg-card) !important; }
:deep(.vditor-preview .vditor-reset) { color: var(--text-1) !important; padding: 16px 22px 40px; }
:deep(.vditor-content) { background: var(--bg-card) !important; }

/* 只读（纯预览）时隐藏预览操作栏（CSS 层兜底，action 生成即隐藏） */
.readonly-mode :deep(.vditor-preview__action) { display: none !important; }
/* 只读（纯预览）时隐藏 vditor 工具栏 */
.readonly-mode :deep(.vditor-toolbar) { display: none !important; }

/* 大纲栏（左侧，参考 ld246 风格） */
:deep(.vditor-outline) {
  background: var(--bg-card) !important;
  border-right: 1px solid var(--border) !important;
  width: 220px !important;
  padding: 14px 10px !important;
  overflow-y: auto !important;
}
:deep(.vditor-outline__title) {
  font-size: 11px;
  color: var(--text-3);
  font-weight: 600;
  letter-spacing: 1px;
  padding: 0 10px 8px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 10px;
}
:deep(.vditor-outline__content) { padding: 4px 2px; }
:deep(.vditor-outline__content a) {
  display: block;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.5;
  padding: 4px 10px;
  margin: 1px 0;
  border-radius: 8px;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: all .15s ease;
}
:deep(.vditor-outline__content a:hover) {
  color: var(--brand);
  background: var(--hover-bg);
}
:deep(.vditor-outline__content a.vditor-outline__item--current),
:deep(.vditor-outline__content .vditor-outline__item--current) {
  color: var(--brand) !important;
  background: var(--brand-soft) !important;
  font-weight: 600;
}

/* 底部状态条 */
.editor-statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 16px;
  font-size: 12px;
  color: var(--text-3);
  border-top: 1px solid var(--border);
  background: var(--bg-card);
  flex-shrink: 0;
}
.statusbar-left,
.statusbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.statusbar-stats {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.statusbar-time { white-space: nowrap; }
.time-saved { color: #52c41a; }
.time-dirty { color: #f59e0b; }
.statusbar-hint {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.statusbar-keys {
  color: var(--brand);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  white-space: nowrap;
}

/* 自动保存 3 秒进度圆环 */
.save-ring {
  display: block;
  flex-shrink: 0;
}
.save-ring .ring-track {
  fill: none;
  stroke: var(--border);
  stroke-width: 2;
}
.save-ring .ring-bar {
  fill: none;
  stroke: var(--brand);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-dasharray: 50.27;
  stroke-dashoffset: 50.27;
  transform: rotate(-90deg);
  transform-origin: center;
  transition: stroke .2s ease;
}
.ring-dirty .ring-bar {
  animation: ring-countdown 3s linear forwards;
}
@keyframes ring-countdown {
  to { stroke-dashoffset: 0; }
}
.ring-saving .ring-bar {
  stroke-dashoffset: 0;
  animation: sync-spin .9s linear infinite;
}
.ring-saved .ring-bar {
  stroke: #52c41a;
  stroke-dashoffset: 0;
}
.ring-idle .ring-bar {
  stroke-dashoffset: 50.27;
}

/* 空态引导 */
.editor-guide {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.guide-card {
  pointer-events: none;
  text-align: center;
  padding: 30px 44px;
  background: var(--bg-card);
  border: 1px dashed var(--border);
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, .06);
  animation: fade-in-up .3s ease;
}
.guide-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 10px;
}
.guide-tip {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 16px;
}
.guide-tip code {
  background: var(--hover-bg);
  color: var(--brand);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.guide-help {
  pointer-events: auto;
  border: none;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all .18s ease;
}
.guide-help:hover {
  background: var(--brand);
  color: #fff;
}
@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 快捷键帮助弹窗 */
.help-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.help-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--hover-bg);
  border-radius: 8px;
  font-size: 13px;
}
.help-keys {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--brand);
}
.help-desc {
  color: var(--text-2);
}
.help-foot {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.9;
}
.help-foot code {
  background: var(--hover-bg);
  color: var(--brand);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 11px;
}

/* ==== Markdown 排版（.vditor-reset 覆盖，编辑态与预览态一致）==== */
:deep(.vditor-reset) {
  font-size: 15px;
  line-height: 1.85;
  word-break: break-word;
}

/* 标题：大号粗体 + 明显下边距 */
:deep(.vditor-reset h1),
:deep(.vditor-reset h2),
:deep(.vditor-reset h3),
:deep(.vditor-reset h4),
:deep(.vditor-reset h5),
:deep(.vditor-reset h6) {
  color: var(--text-1) !important;
  font-weight: 700;
  margin: 1.6em 0 .6em;
  line-height: 1.3;
}
:deep(.vditor-reset h1) { font-size: 2em; padding-bottom: .4em; border-bottom: 1px solid var(--border); }
:deep(.vditor-reset h2) { font-size: 1.6em; padding-bottom: .3em; border-bottom: 1px solid var(--border); }
:deep(.vditor-reset h3) { font-size: 1.3em; }
:deep(.vditor-reset h4) { font-size: 1.15em; }
:deep(.vditor-reset h5) { font-size: 1em; }
:deep(.vditor-reset h6) { font-size: .9em; color: var(--text-2) !important; }

/* 段落与加粗/斜体 */
:deep(.vditor-reset p) { margin: .6em 0; }
:deep(.vditor-reset strong) { font-weight: 700; color: var(--text-1); }
:deep(.vditor-reset em) { font-style: italic; }

/* 引用：左侧灰色竖边框 */
:deep(.vditor-reset blockquote) {
  margin: 1em 0;
  padding: .5em 1em;
  border-left: 4px solid var(--border);
  background: var(--hover-bg);
  color: var(--text-2) !important;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
:deep(.vditor-reset blockquote p) { margin: .4em 0; }

/* 代码：行内浅底 + 代码块深灰底等宽字体
   预览态代码块：.vditor-reset 容器内 pre
   ir 编辑态代码块：源码层 .vditor-ir__marker--pre + 渲染层 .vditor-ir__preview（两层流式排布，不重叠） */
:deep(.vditor-reset code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: .88em;
  background: var(--hover-bg) !important;
  color: var(--brand) !important;
  padding: .15em .4em;
  border-radius: 6px;
}
/* 预览态代码块 */
:deep(.vditor-reset pre) {
  background: var(--hover-bg) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-md);
  padding: 14px 16px !important;
  overflow-x: auto;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace !important;
}
:deep(.vditor-reset pre code) {
  background: transparent !important;
  color: var(--text-1) !important;
  padding: 0;
  font-size: .9em;
  font-family: inherit !important;
}
/* ir 编辑态代码块：源码编辑层 */
:deep(.vditor-ir__marker--pre) {
  background: var(--hover-bg) !important;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius-md);
  padding: 12px 16px !important;
  margin: 6px 0 4px !important;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace !important;
  color: var(--text-1) !important;
}
/* ir 编辑态代码块：聚焦编辑（--expand）时隐藏渲染预览层，只显示源码编辑框；
   失焦（非 expand）时隐藏源码层，只显示渲染层（避免源码折叠窄条与渲染层叠加遮挡）；
   隐藏代码块节点两端的 ``` 伪元素装饰（半椭圆 pill，不美观）+ 清除语言标签多余样式 */
:deep(.vditor-ir__node--expand pre.vditor-ir__preview),
:deep(.vditor-ir__node--expand pre.vditor-ir__preview code) {
  display: none !important;
}
:deep(.vditor-ir__node:not(.vditor-ir__node--expand) .vditor-ir__marker--pre) {
  display: none !important;
}
/* 隐藏代码块 ::before/::after 的 ``` 标记与半椭圆装饰 */
:deep(.vditor-ir__node::before),
:deep(.vditor-ir__node::after) {
  display: none !important;
}
/* 清除语言标签 marker--info 的多余 outline/装饰，保持纯文本 */
:deep(.vditor-ir__marker--info) {
  outline: none !important;
  background: transparent !important;
  border-radius: 0 !important;
  padding: 0 !important;
}
:deep(.vditor-reset pre code.hljs) { color: var(--text-1) !important; }

/* 列表：圆点 / 数字缩进 */
:deep(.vditor-reset ul) { list-style: disc; }
:deep(.vditor-reset ul ul) { list-style: circle; }
:deep(.vditor-reset ul ul ul) { list-style: square; }
:deep(.vditor-reset ol) { list-style: decimal; }
:deep(.vditor-reset ul),
:deep(.vditor-reset ol) { margin: .6em 0; padding-left: 1.7em; }
:deep(.vditor-reset li) { margin: .3em 0; }
:deep(.vditor-reset li > ul),
:deep(.vditor-reset li > ol) { margin: .2em 0; }
:deep(.vditor-reset li > p) { margin: .2em 0; }
:deep(.vditor-reset input[type='checkbox']) { margin-right: .4em; }

/* 分割线与链接 */
:deep(.vditor-reset hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.6em 0;
}
:deep(.vditor-reset a) { color: var(--link) !important; text-decoration: none; }
:deep(.vditor-reset a:hover) { text-decoration: underline; }

/* 表格 */
:deep(.vditor-reset table) { border-collapse: collapse; margin: 1em 0; }
:deep(.vditor-reset th),
:deep(.vditor-reset td) {
  border: 1px solid var(--border);
  padding: 8px 12px;
}
:deep(.vditor-reset th) { background: var(--hover-bg); font-weight: 600; }
:deep(.vditor-reset img) { max-width: 100%; border-radius: var(--radius-sm); }
</style>

<!-- 全局样式：强制隐藏 vditor 代码块节点的 ::before/::after 伪元素（半椭圆 pill 装饰）
     不用 scoped :deep() 以确保最高优先级覆盖 vditor 默认 CSS -->
<style>
/* 全局样式：消除 vditor 代码块的多余装饰（半椭圆 pill、tooltip 伪元素）
   不用 scoped :deep() 以确保最高优先级覆盖 vditor 默认 CSS */
/* 隐藏代码块节点 ::before/::after 的 ``` 标记装饰 */
.vditor-ir__node::before,
.vditor-ir__node::after {
  display: none !important;
  content: none !important;
  background: transparent !important;
  border-radius: 0 !important;
  border: none !important;
  padding: 0 !important;
  box-shadow: none !important;
  outline: none !important;
}
/* 清除语言标签 marker--info 的多余 outline/背景，保持纯文本 */
.vditor-ir__marker--info {
  outline: none !important;
  background: transparent !important;
  border-radius: 0 !important;
  padding: 0 !important;
  border: none !important;
}
/* 隐藏 vditor-tooltipped tooltip 的 ::before/::after（灰色 pill + 三角箭头） */
.vditor-tooltipped::before,
.vditor-tooltipped::after,
.vditor-tooltipped:hover::before,
.vditor-tooltipped:hover::after,
.vditor-tooltipped:focus::before,
.vditor-tooltipped:focus::after,
.vditor-tooltipped:active::before,
.vditor-tooltipped:active::after {
  display: none !important;
  opacity: 0 !important;
}
</style>
