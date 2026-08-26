<script setup lang="ts">
/**
 * NoteEditor.vue — md-editor-v3 Markdown 编辑器封装
 * 编辑态：MdEditor 左右分栏（左源码 + 右实时预览，同步滚动可拖拽）+ 内置右侧悬浮目录
 * 只读态：MdPreview 纯渲染阅读
 * 能力：自动保存（3s）+ Ctrl/⌘+S + 保存状态指示 + 空态引导 + 快捷键帮助 + 图片上传
 * 扩展（highlight.js 等）走 unpkg CDN 按需加载
 */
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { NButton, NIcon, NInput, NModal, NSwitch, NSpin, useMessage, useDialog } from 'naive-ui'
import { Keyboard, ArrowLeft, CheckCircle2, CloudOff, LoaderCircle, Download, Upload, History, LayoutTemplate, Link2 } from 'lucide-vue-next'
import { MdEditor, MdPreview, type ExposeParam, type ToolbarNames, type HeadList } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import 'md-editor-v3/lib/preview.css'
import axios from 'axios'
import type { Note } from '../stores/note'
import TagPicker from './TagPicker.vue'
import NoteVersionsModal from './NoteVersionsModal.vue'
import { extractDataUris, dataUriToFile, uploadImage, downloadFile, exportMarkdown } from '../composables/markdownIO'

/** md-editor-v3 实例 id（MdEditor 与 MdCatalog 通过它联动） */
const EDITOR_ID = 'jnclub-note-editor'

const props = defineProps<{
  note: Note | null
  isDark: boolean
}>()

const emit = defineEmits<{
  close: []
  saved: [note: Note]
  deleted: [note: Note]
  'jump-note': [id: number]
}>()

const message = useMessage()
const dialog = useDialog()

const mdEditor = ref<ExposeParam | null>(null)
/** 只读模式：查看已有便签默认只读，新建默认可编辑 */
const readonlyMode = ref(props.note ? props.note.id !== 0 : true)
const isViewNote = computed(() => !!props.note && props.note.id !== 0)
const showVersions = ref(false)

/** 移动端检测：窄屏默认关闭分屏预览（md-editor-v3 preview 为内部状态，经 expose preview(false) 切换） */
const isMobile = ref(false)
const checkMobile = () => { isMobile.value = window.innerWidth < 768 }
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  document.addEventListener('click', handleDocClick)
  if (isMobile.value) {
    outlineVisible.value = false // 窄屏大纲默认收起，避免遮挡编辑区
    nextTick(() => mdEditor.value?.togglePreview(false))
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('click', handleDocClick)
})

const content = ref('')
const title = ref('')
const saving = ref(false)
const autoSaveTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const hasUnsavedChanges = ref(false)

/** 便签标签：TagPicker + saveTrigger 在保存成功后持久化（新建时 refId 为 null，保存后 props.note.id 回填真实值） */
const tagPickerRef = ref<InstanceType<typeof TagPicker> | null>(null)
const saveTick = ref(0)
const noteRefId = computed(() => (props.note && props.note.id !== 0 ? props.note.id : null))

/** 目录大纲数据（来自 MdEditor / MdPreview 的 onGetCatalog 回调） */
const catalogList = ref<HeadList[]>([])
const handleCatalog = (headings: HeadList[]) => { catalogList.value = headings || [] }

/** 悬浮大纲：收起/展开 + 可拖拽位置（记忆到 localStorage）。移动端默认收起，避免遮挡窄屏编辑区 */
const outlineVisible = ref(true)
const OUTLINE_POS_KEY = 'jnclub-outline-pos'
const outlinePos = ref({ x: 0, y: 0 })
const outlineBox = ref<HTMLDivElement | null>(null)
const outlineToggle = ref<HTMLButtonElement | null>(null)
let dragging = false
let dragOffset = { x: 0, y: 0 }

const initOutlinePos = () => {
  // 默认位置：视口右侧、垂直居中（首屏尽早计算，避免 initial 0,0 挡编辑区）
  const vw = typeof window !== 'undefined' ? window.innerWidth : 1200
  const outlineW = 200
  if (typeof window !== 'undefined') {
    outlinePos.value = { x: vw - outlineW - 24, y: 120 }
  }
  try {
    const saved = localStorage.getItem(OUTLINE_POS_KEY)
    if (saved) {
      const { x, y } = JSON.parse(saved)
      if (typeof x === 'number' && typeof y === 'number') outlinePos.value = { x, y }
    }
  } catch { /* 忽略损坏数据 */ }
  // 拖拽记忆位置可能超出当前视口（换设备/窗口缩放），clamp 防止大纲跑出屏幕
  const maxX = Math.max(0, vw - outlineW - 12)
  const vh = typeof window !== 'undefined' ? window.innerHeight : 800
  outlinePos.value = {
    x: Math.min(Math.max(outlinePos.value.x, 0), maxX),
    y: Math.min(Math.max(outlinePos.value.y, 60), Math.max(60, vh - 220)),
  }
}
initOutlinePos()

const saveOutlinePos = () => {
  try { localStorage.setItem(OUTLINE_POS_KEY, JSON.stringify(outlinePos.value)) } catch { /* 忽略 */ }
}

/** 开始拖拽（大纲框或展开按钮通用） */
const onOutlineDragStart = (e: MouseEvent) => {
  const movingEl = outlineVisible.value ? outlineBox.value : outlineToggle.value
  if (!movingEl) return
  const rect = movingEl.getBoundingClientRect()
  dragging = true
  dragOffset = { x: e.clientX - rect.left, y: e.clientY - rect.top }
  dragStart = { x: e.clientX, y: e.clientY }
  didDrag = false
  movingEl.style.transition = 'none'
  document.addEventListener('mousemove', onOutlineDragMove)
  document.addEventListener('mouseup', onOutlineDragEnd)
}
let dragStart = { x: 0, y: 0 }
let didDrag = false
const onOutlineDragMove = (e: MouseEvent) => {
  if (!dragging) return
  // 超过阈值才算真正拖拽（避免与 click 展开冲突）
  if (!didDrag && Math.abs(e.clientX - dragStart.x) + Math.abs(e.clientY - dragStart.y) > 4) {
    didDrag = true
    e.preventDefault()
  }
  if (didDrag) outlinePos.value = { x: e.clientX - dragOffset.x, y: e.clientY - dragOffset.y }
}
const onOutlineDragEnd = () => {
  if (!dragging) return
  dragging = false
  if (didDrag) saveOutlinePos()
  const movingEl = outlineVisible.value ? outlineBox.value : outlineToggle.value
  if (movingEl) movingEl.style.transition = ''
  document.removeEventListener('mousemove', onOutlineDragMove)
  document.removeEventListener('mouseup', onOutlineDragEnd)
}

/** 展开按钮点击：若刚拖拽过则不触发展开 */
const onOutlineToggleClick = () => {
  if (didDrag) { didDrag = false; return }
  outlineVisible.value = true
}

/** 点击目录项 → 滚动到对应标题 */
const scrollToHeading = (h: HeadList) => {
  const id = mdHeadingId(h)
  const target = document.getElementById(id)
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'start' })
    // 高亮当前项
    catalogList.value.forEach(i => { i.active = (i.text === h.text && i.level === h.level) })
  }
}

/** 标题 id 生成（与 md-editor-v3 默认一致：mdHeadingId 默认返回 text） */
const mdHeadingId = (h: { text: string; level: number; index?: number }) => h.text

/** 目录项缩进宽度（按标题层级） */
const headingPadding = (level: number) => `${(level - 1) * 12}px`

/** 保存状态：未保存 / 保存中 / 已保存（含自动保存） */
const saveState = ref<'idle' | 'dirty' | 'saving' | 'saved'>('idle')
const lastSavedAt = ref('')
const formatTime = (d: Date) => d.toTimeString().slice(0, 8)

/** 字数统计：去 Markdown 符号后，中文按字 + 英文/数字按词；阅读时长按 300 字/分钟 */
const wordStats = computed(() => {
  const raw = content.value
  const plain = raw
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/\[\[[^\]]+\]\]/g, ' ')
    .replace(/[#>*_~\-\[\]()!|]/g, ' ')
    .replace(/\s+/g, ' ')
  const cjk = (plain.match(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g) || []).length
  const latin = (plain.replace(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g, ' ').match(/[A-Za-z0-9]+/g) || []).length
  const words = cjk + latin
  return { words, minutes: Math.max(1, Math.ceil(words / 300)) }
})
const lineCount = computed(() => (content.value ? content.value.split('\n').length : 0))

/* ─── 双链 [[笔记名]] ─── */
/** md-editor-v3 sanitize 回调：把 [[标题]] 渲染为可点击链接（标题做 HTML 转义） */
const sanitizeNoteLinks = (html: string): string => {
  if (!html || !html.includes('[[')) return html
  return html.replace(/\[\[([^\]\n]+?)\]\]/g, (_m, t: string) => {
    const esc = escapeHtml(t.trim())
    return `<a href="#" class="note-link" data-note-title="${esc}">[[${esc}]]</a>`
  })
}
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/** 文档级点击委托：命中 a.note-link → 按标题解析便签并跳转 */
const handleDocClick = async (e: MouseEvent) => {
  const target = e.target as HTMLElement
  const link = target.closest?.('a.note-link') as HTMLAnchorElement | null
  if (!link) return
  e.preventDefault()
  const noteTitle = link.dataset.noteTitle || ''
  if (!noteTitle) return
  try {
    const res = await axios.get('/api/notes/resolve', { params: { title: noteTitle } })
    const note = res.data?.data
    if (res.data?.code === 200 && note?.id) {
      emit('jump-note', note.id)
    } else {
      message.info(`未找到便签「${noteTitle}」——新建同名便签后双链自动生效`)
    }
  } catch {
    message.error('双链解析失败')
  }
}

/* ─── 反向链接面板 ─── */
const backlinks = ref<Array<{ id: number; title: string; directoryId: number; snippet: string }>>([])
const backlinksLoading = ref(false)
const backlinksOpen = ref(false)
let backlinkReqSeq = 0
const fetchBacklinks = async () => {
  if (!isViewNote.value || !props.note) { backlinks.value = []; return }
  const seq = ++backlinkReqSeq
  backlinksLoading.value = true
  try {
    const res = await axios.get(`/api/notes/${props.note.id}/backlinks`)
    if (seq === backlinkReqSeq) backlinks.value = res.data?.data || []
  } catch {
    if (seq === backlinkReqSeq) backlinks.value = []
  } finally {
    if (seq === backlinkReqSeq) backlinksLoading.value = false
  }
}
watch(() => props.note?.id, () => {
  nextTick(fetchBacklinks)
}, { immediate: true })

/* ─── 便签模板 ─── */
const toDateStr = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const todayStr = toDateStr(new Date())
interface NoteTemplate { key: string; label: string; icon: string; content: string }
const NOTE_TEMPLATES: NoteTemplate[] = [
  {
    key: 'meeting', label: '会议记录', icon: '📋',
    content: `# 会议记录\n\n**时间**：\n**参会人**：\n**主题**：\n\n## 议程\n\n- \n\n## 决议\n\n- \n\n## 待办\n\n- [ ] `,
  },
  {
    key: 'diary', label: '日记', icon: '📖',
    content: `# ${todayStr} 日记\n\n## 今天做了什么\n\n\n## 感受与思考\n\n\n## 明天计划\n\n- `,
  },
  {
    key: 'weekly', label: '周报', icon: '📊',
    content: `# 周报\n\n**周期**：\n\n## 本周完成\n\n- \n\n## 数据与产出\n\n- \n\n## 下周计划\n\n- \n\n## 风险与求助\n\n- `,
  },
  {
    key: 'book', label: '读书笔记', icon: '📚',
    content: `# 读书笔记\n\n**书名**：\n**作者**：\n**读完日期**：\n\n## 内容概要\n\n\n## 金句摘录\n\n> \n\n## 我的思考\n\n`,
  },
  {
    key: 'checklist', label: '清单', icon: '✅',
    content: `## 清单\n\n- [ ] \n- [ ] \n- [ ] `,
  },
]
const showTemplatePicker = ref(false)
const applyTemplate = (t: NoteTemplate) => {
  if (content.value.trim()) {
    dialog.warning({
      title: '应用模板',
      content: '当前内容将被模板覆盖，确定继续？',
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: () => {
        content.value = t.content
        if (!title.value.trim()) title.value = t.label
        showTemplatePicker.value = false
        saveState.value = 'dirty'
        hasUnsavedChanges.value = true
      },
    })
    return
  }
  content.value = t.content
  if (!title.value.trim()) title.value = t.label
  showTemplatePicker.value = false
  saveState.value = 'dirty'
  hasUnsavedChanges.value = true
}

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

/** md-editor-v3 工具栏（对齐原 vditor 精简工具栏） */
const toolbars: ToolbarNames[] = [
  'revoke', 'next', '-',
  'title', 'bold', 'italic', 'strikeThrough', 'link', '-',
  'unorderedList', 'orderedList', 'task', '-',
  'quote', 'code', 'codeRow', 'table', '-',
  'image', '-', 'fullscreen', 'pageFullscreen', 'preview', 'previewOnly', '-', 'save',
]

/** 外部 note 变化（切换便签/加载）→ 同步内容（v-model 双向绑定，直接赋值即可） */
watch(() => props.note?.id, () => {
  if (!props.note) return
  content.value = props.note.content || ''
  title.value = props.note.title || ''
  hasUnsavedChanges.value = false
  saveState.value = 'idle'
  lastSavedAt.value = ''
}, { immediate: true })

/** 图片上传：md-editor-v3 回调式——上传成功回调 urls，由编辑器自动插入 */
const handleUploadImg = async (files: File[], callback: (urls: string[]) => void) => {
  if (!files.length) return
  try {
    const formData = new FormData()
    formData.append('file', files[0])
    const res = await axios.post('/api/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (res.data.code === 200) {
      callback([res.data.data.url])
      return
    }
    message.error(res.data.message || '上传失败')
  } catch (e: any) {
    message.error(e.response?.status === 401 ? '请先登录' : (e.response?.data?.message || '图片上传失败'))
  }
}

/** 编辑器输入 → 触发自动保存 */
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

/** 保存（Ctrl/⌘+S 或自动保存触发；md-editor-v3 onSave 第二参为 html Promise，忽略） */
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
        if (!silent) message.success('已保存')
        emit('saved', res.data.data as Note)
        // 新建：emit 同步回填 props.note.id 后，触发标签持久化
        saveTick.value++
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
      if (!silent) message.success('已保存')
      emit('saved', { ...props.note, title: title.value, content: content.value } as Note)
      saveTick.value++
    }
  } catch (e: any) {
    saveState.value = 'dirty'
    message.error(e.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

/** md-editor-v3 onSave 适配：签名 (v, h) => void，转发到 handleSave() */
const handleEditorSave = () => {
  handleSave()
}

/** ===== Markdown 导入 / 导出 ===== */
const mdFileInput = ref<HTMLInputElement | null>(null)

/** 导出当前便签：本地图嵌 base64，外链保留 */
const handleExportMd = async () => {
  if (!content.value.trim()) { message.warning('内容为空，无可导出'); return }
  const loadingMsg = message.loading('正在导出，处理图片…', { duration: 0 })
  try {
    const md = await exportMarkdown(content.value)
    downloadFile(`${title.value.trim() || '未命名'}.md`, md, 'text/markdown')
    message.success('已导出')
  } catch (e: any) {
    message.error(e.message || '导出失败')
  } finally {
    loadingMsg.destroy()
  }
}

/** 触发隐藏文件选择 */
const handleImportClick = () => {
  mdFileInput.value?.click()
}

/** 导入 .md：data URI 图片逐个落地为 /api/files/...，替换正文 */
const handleImportFile = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许重复选同一文件
  if (!file) return
  try {
    const text = await file.text()
    const uris = extractDataUris(text)
    if (!uris.length) {
      content.value = text
      hasUnsavedChanges.value = true
      saveState.value = 'dirty'
      message.success('已导入（无内嵌图片）')
      return
    }
    const loadingMsg = message.loading(`正在处理 ${uris.length} 张图片…`, { duration: 0 })
    let md = text
    let failed = 0
    for (const uri of uris) {
      const imgFile = dataUriToFile(uri)
      if (!imgFile) { failed++; continue }
      try {
        const url = await uploadImage(imgFile)
        md = md.split(uri).join(url)
      } catch { failed++ }
    }
    loadingMsg.destroy()
    content.value = md
    hasUnsavedChanges.value = true
    saveState.value = 'dirty'
    message.success(failed ? `已导入（${failed} 张图片处理失败）` : `已导入 ${uris.length} 张图片`)
  } catch (e: any) {
    message.error(e.message || '导入失败')
  }
}

/** 关闭前检查未保存 */
const handleRequestClose = () => {
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

const toggleReadonly = () => {
  readonlyMode.value = !readonlyMode.value
  // 进入编辑模式时触发一次编辑框高亮动画
  if (!readonlyMode.value) {
    editorFlash.value = false
    requestAnimationFrame(() => { editorFlash.value = true })
  }
  if (!readonlyMode.value) {
    mdEditor.value?.focus()
  }
}

/** Ctrl/Cmd+S 快捷保存（md-editor-v3 内置 onSave 已绑定，此为页面级兜底） */
const handleKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') { e.preventDefault(); handleSave() }
}

onMounted(() => {
  // MdEditor 挂载完成后触发一次 onGetCatalog，确保目录初始有数据
  setTimeout(() => {
    if (!readonlyMode.value) mdEditor.value?.rerender?.()
  }, 0)
})

onBeforeUnmount(() => {
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  document.removeEventListener('mousemove', onOutlineDragMove)
  document.removeEventListener('mouseup', onOutlineDragEnd)
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
  <div class="note-editor-shell" :class="{ 'readonly-mode': readonlyMode }">
    <div class="note-editor" :class="{ 'readonly-mode': readonlyMode }">
    <div class="editor-topbar">
      <NButton quaternary circle size="small" @click="handleRequestClose" title="返回列表（有未保存修改时二次确认）">
        <template #icon><NIcon :component="ArrowLeft" size="17" /></template>
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
      <div class="template-wrap">
        <NButton quaternary circle size="small" title="使用模板" @click="showTemplatePicker = !showTemplatePicker">
          <template #icon><NIcon :component="LayoutTemplate" size="16" /></template>
        </NButton>
        <!-- 模板下拉菜单 -->
        <div v-if="showTemplatePicker" class="template-menu glass-card--modal">
          <div class="template-menu-title">选择模板</div>
          <button
            v-for="t in NOTE_TEMPLATES" :key="t.key"
            type="button" class="template-item jnclub-bouncy" @click="applyTemplate(t)"
          >
            <span class="template-icon">{{ t.icon }}</span>
            <span class="template-label">{{ t.label }}</span>
          </button>
        </div>
      </div>
      <NButton quaternary circle size="small" title="快捷键帮助" @click="showHelp = true">
        <template #icon><NIcon :component="Keyboard" size="16" /></template>
      </NButton>
      <NButton quaternary circle size="small" class="io-btn" title="导出 Markdown" @click="handleExportMd">
        <template #icon><NIcon :component="Download" size="16" /></template>
      </NButton>
      <NButton quaternary circle size="small" class="io-btn" title="导入 Markdown" @click="handleImportClick">
        <template #icon><NIcon :component="Upload" size="16" /></template>
      </NButton>
      <NButton
        v-if="isViewNote"
        quaternary circle size="small" class="io-btn" title="历史版本" @click="showVersions = true"
      >
        <template #icon><NIcon :component="History" size="16" /></template>
      </NButton>
      <input
        ref="mdFileInput"
        type="file"
        accept=".md,.markdown,text/markdown"
        style="display: none"
        @change="handleImportFile"
      />
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

    <!-- 标签行（编辑态）：保存时随便签一并持久化 -->
    <div v-if="!readonlyMode" class="editor-tags">
      <span class="tags-label">标签</span>
      <TagPicker
        ref="tagPickerRef"
        ref-type="note"
        :ref-id="noteRefId"
        :save-trigger="saveTick"
      />
    </div>

    <div class="editor-body" :class="{ flash: editorFlash }">
      <!-- md-editor-v3 编辑器（编辑态：左右分栏实时预览） / 渲染预览（只读态）
           两态都通过 onGetCatalog 输出目录数据给统一的悬浮大纲；切换带淡入淡出过渡 -->
      <Transition name="editor-mode" mode="out-in">
        <MdEditor
          v-if="!readonlyMode"
          ref="mdEditor"
          :id="EDITOR_ID"
          v-model="content"
          :theme="isDark ? 'dark' : 'light'"
          language="zh-CN"
          :toolbars="toolbars"
          :placeholder="'支持 Markdown 语法，试试输入 # 标题，或 ** 加粗；输入 [[笔记名]] 创建双链'"
          :no-upload-img="false"
          :auto-detect-code="true"
          :sanitize="sanitizeNoteLinks"
          :on-upload-img="handleUploadImg"
          :on-save="handleEditorSave"
          :on-change="handleEditorInput"
          :on-get-catalog="handleCatalog"
          class="md-editor-wrap"
        />
        <MdPreview
          v-else
          :id="EDITOR_ID + '-preview'"
          :model-value="content"
          :theme="isDark ? 'dark' : 'light'"
          :sanitize="sanitizeNoteLinks"
          :on-get-catalog="handleCatalog"
          class="md-preview-wrap"
        />
      </Transition>

      <!-- 空态引导：首次打开不是白板 -->
      <div v-if="showGuide" class="editor-guide">
        <div class="guide-card">
          <div class="guide-title">直接开始写作…</div>
          <div class="guide-tip">
            支持 Markdown 语法 · 试试输入 <code># 标题</code> 或 <code>**加粗**</code>；输入 <code>[[笔记名]]</code> 创建双链
          </div>
          <button type="button" class="guide-help" @click="showHelp = true">查看快捷键帮助 →</button>
        </div>
      </div>
    </div>

    <!-- 反向链接：引用当前便签标题的其他便签 -->
    <div v-if="isViewNote" class="backlinks-panel">
      <button type="button" class="backlinks-head jnclub-bouncy" @click="backlinksOpen = !backlinksOpen">
        <NIcon :component="Link2" size="13" />
        <span>反向链接</span>
        <span class="backlinks-count">{{ backlinks.length }}</span>
        <span class="backlinks-toggle">{{ backlinksOpen ? '收起' : '展开' }}</span>
      </button>
      <div v-if="backlinksOpen" class="backlinks-body">
        <NSpin :show="backlinksLoading" size="small">
          <div v-if="!backlinks.length && !backlinksLoading" class="backlinks-empty">
            还没有便签引用「{{ props.note?.title }}」。在任意便签中输入
            <code>[[{{ props.note?.title }}]]</code> 即可建立双链。
          </div>
          <button
            v-for="b in backlinks" :key="b.id"
            type="button" class="backlink-item jnclub-bouncy" @click="emit('jump-note', b.id)"
          >
            <span class="backlink-title">{{ b.title }}</span>
            <span class="backlink-snippet">{{ b.snippet }}</span>
          </button>
        </NSpin>
      </div>
    </div>

    <!-- 底部状态栏：字数 / 时间 / 自动保存圆环（只读时提示解锁） -->
    <div class="editor-statusbar">
      <div class="statusbar-left">
        <span v-if="readonlyMode" class="statusbar-hint">只读模式 · 打开右侧「编辑」开关解锁</span>
        <template v-else>
          <span class="statusbar-stats">{{ wordStats.words }} 字 · 约 {{ wordStats.minutes }} 分钟 · {{ lineCount }} 行</span>
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

  <!-- 统一可拖拽、可收起的悬浮大纲（置于 .note-editor 之外，避免被其 backdrop-filter 建立的 containing block 约束，fixed 才相对视口） -->
  <div
    v-show="outlineVisible && catalogList.length"
    ref="outlineBox"
    class="outline-float"
    :style="{ left: outlinePos.x + 'px', top: outlinePos.y + 'px' }"
  >
    <div class="outline-float__head" @mousedown="onOutlineDragStart" title="拖动调整位置">
      <span class="outline-float__title">大纲</span>
      <div class="outline-float__actions">
        <button class="outline-float__btn" title="收起" @click="outlineVisible = false">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="18 15 12 9 6 15"/></svg>
        </button>
      </div>
    </div>
    <div class="outline-float__body">
      <button
        v-for="(h, i) in catalogList"
        :key="i"
        class="outline-float__item"
        :class="{ 'outline-float__item--active': h.active }"
        :style="{ paddingLeft: headingPadding(h.level) }"
        @click="scrollToHeading(h)"
      >{{ h.text }}</button>
    </div>
  </div>

  <!-- 悬浮大纲展开按钮（收起后显示，可拖动调整位置） -->
  <button
    v-if="catalogList.length && !outlineVisible"
    ref="outlineToggle"
    class="outline-float__reopen"
    title="点击展开大纲 · 拖动调整位置"
    @mousedown="onOutlineDragStart"
    @click="onOutlineToggleClick"
    :style="{ left: outlinePos.x + 'px', top: outlinePos.y + 'px' }"
  >
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h16M4 18h10"/></svg>
    <span>大纲</span>
  </button>

  <!-- 历史版本弹窗 -->
  <NoteVersionsModal
    v-model:show="showVersions"
    :note-id="isViewNote ? props.note!.id : null"
    :note-title="title || props.note?.title || ''"
    @restored="handleRequestClose"
  />
</div>
</template>

<style scoped>
.note-editor-shell {
  height: 100%;
  max-width: 100vw; /* 编辑态全屏 */
  margin: 0;
  transition: max-width 0.3s var(--ease), padding 0.3s var(--ease);
}
/* 只读预览态：居中窄栏，适度放宽到 1040px */
.note-editor-shell.readonly-mode {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 16px;
}
.note-editor {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.editor-topbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
  min-height: 38px;
}
.editor-topbar :deep(.n-button) {
  height: 26px;
  min-height: 26px;
  font-size: 12px;
  padding: 0 6px;
}
.editor-topbar :deep(.n-button.n-button--circle) {
  width: 26px;
  padding: 0;
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

/* 模板下拉菜单 */
.template-wrap { position: relative; display: inline-flex; }
.template-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  width: 200px;
  padding: 10px;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-2), var(--glass-shadow);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.template-menu-title {
  font-size: var(--fs-xs);
  color: var(--text-3);
  letter-spacing: 0.05em;
  padding: 2px 6px 6px;
}
.template-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  font-size: var(--fs-md);
  color: var(--text-1);
  text-align: left;
  transition: background var(--dur) var(--ease);
}
.template-item:hover { background: var(--glass-chip-bg); }
.template-icon { font-size: 16px; }
.template-label { font-weight: 500; }

/* 反向链接面板：悬浮在右下角，不再挤占编辑区 */
.backlinks-panel {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: 300px;
  max-width: calc(100% - 24px);
  max-height: 40vh;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  background: var(--glass-bg-trans);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: var(--glass-shadow);
}
.backlinks-panel:not(:has(.backlinks-body)) {
  width: auto;
  min-width: 120px;
}
.backlinks-head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 7px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  font-size: var(--fs-sm);
  color: var(--glass-text-secondary);
  text-align: left;
  flex-shrink: 0;
}
.backlinks-head:hover { color: var(--text-1); }
.backlinks-count {
  min-width: 18px;
  text-align: center;
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--glass-chip-text);
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-pill);
  padding: 0 6px;
}
.backlinks-toggle { margin-left: auto; font-size: var(--fs-xs); }
.backlinks-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 10px 10px;
  overflow-y: auto;
}
/* 只读态底部保留提示栏，悬浮面板上移一点避免遮挡 */
.readonly-mode .backlinks-panel {
  bottom: 38px;
}
.backlinks-empty {
  font-size: var(--fs-sm);
  color: var(--text-3);
  padding: 8px 4px;
  line-height: 1.6;
}
.backlinks-empty code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.9em;
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: 4px;
  padding: 1px 4px;
}
.backlink-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-sm);
  background: var(--glass-chip-bg);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color var(--dur) var(--ease);
}
.backlink-item:hover { border-color: var(--brand); }
.backlink-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}
.backlink-snippet {
  font-size: var(--fs-xs);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 双链链接样式（md 预览内 a.note-link） */
:deep(.note-link) {
  color: var(--brand);
  text-decoration: none;
  border-bottom: 1px dashed var(--brand);
  cursor: pointer;
}
:deep(.note-link:hover) {
  background: var(--brand-soft);
}

/* 标签行 */
.editor-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 12px;
  min-height: 28px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.tags-label {
  font-size: 11px;
  color: var(--text-3);
  flex-shrink: 0;
}
.editor-tags .tag-picker {
  flex: 1;
  min-width: 0;
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

/* 导入/导出按钮（玻璃） */
.io-btn {
  background: var(--glass-bg-trans) !important;
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border) !important;
  color: var(--text-2) !important;
}
.io-btn:hover {
  color: var(--brand) !important;
  border-color: var(--brand) !important;
}

/* 保存同步状态图标 */
.sync-indicator :deep(.n-icon) { color: var(--text-3); }
.sync-dirty :deep(.n-icon) { color: var(--state-warning); }
.sync-saved :deep(.n-icon) { color: var(--state-success); }
.sync-saving :deep(.n-icon) {
  color: var(--brand);
  animation: sync-spin .9s linear infinite;
}
@keyframes sync-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 编辑区：md-editor-v3 撑满 */
.editor-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
}
/* 编辑态 ↔ 预览态切换过渡：淡入 + 轻微位移 */
.editor-mode-enter-active,
.editor-mode-leave-active {
  transition: opacity 0.22s var(--ease), transform 0.22s var(--ease);
}
.editor-mode-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.editor-mode-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
/* 只读→编辑切换：编辑框高亮动画（inset 描边，不影响布局） */
.editor-body.flash {
  animation: editor-enter .6s ease;
}
@keyframes editor-enter {
  0% { box-shadow: inset 0 0 0 2px var(--brand), 0 0 0 1px var(--brand-soft); }
  100% { box-shadow: inset 0 0 0 0 transparent; }
}

/* ==== 统一可拖拽、可收起的悬浮大纲 ==== */
.outline-float {
  position: fixed;
  z-index: 10200;
  width: 200px;
  max-height: 60vh;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  box-shadow: var(--glass-shadow);
  overflow: hidden;
  font-size: 13px;
}
.outline-float__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  cursor: grab;
  user-select: none;
  background: var(--hover-bg);
}
.outline-float__head:active { cursor: grabbing; }
.outline-float__title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--text-3);
}
.outline-float__actions { display: flex; align-items: center; }
.outline-float__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--text-3);
  border-radius: 4px;
  cursor: pointer;
}
.outline-float__btn:hover { background: var(--brand-soft); color: var(--brand); }
.outline-float__body {
  overflow-y: auto;
  padding: 6px 8px;
  max-height: calc(60vh - 37px);
}
.outline-float__item {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.6;
  padding: 3px 8px;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: all .15s ease;
}
.outline-float__item:hover { color: var(--brand); background: var(--hover-bg); }
.outline-float__item--active {
  color: var(--brand) !important;
  background: var(--brand-soft) !important;
  font-weight: 600;
}
/* 展开按钮：精致胶囊 + 图标，可拖动 */
.outline-float__reopen {
  position: fixed;
  z-index: 10200;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand);
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1.5px solid var(--glass-chip-border);
  border-radius: 999px;
  cursor: grab;
  box-shadow: var(--glass-shadow);
  transition: all .18s ease;
  user-select: none;
}
.outline-float__reopen:hover { background: var(--brand-soft); border-color: var(--brand); }
.outline-float__reopen:active { cursor: grabbing; }

/* md-editor 主体 */
.md-editor-wrap { height: 100%; }
/* 只读态预览：纯阅读背景（玻璃面板内，保持可读性用高透底） */
.md-preview-wrap { height: 100%; overflow-y: auto; background: transparent; }
.md-preview-wrap :deep(.md-editor-preview) { padding: 16px 22px 40px; height: 100%; background: transparent !important; }
:deep(.md-editor) { height: 100% !important; }
:deep(.md-editor-toolbar) {
  background: transparent !important;
  border-bottom: 1px solid var(--glass-border) !important;
  padding: 2px 6px !important;
  height: 36px;
  flex-shrink: 0;
}
:deep(.md-editor-toolbar-item) {
  color: var(--text-2) !important;
  height: 26px !important;
  min-width: 26px !important;
}
:deep(.md-editor-toolbar-item:hover) { color: var(--brand) !important; background: var(--glass-chip-bg) !important; }
:deep(.md-editor-toolbar-item.active) { color: var(--brand) !important; }
:deep(.md-editor-content) { background: transparent !important; }
:deep(.md-editor-input) { background: transparent !important; }
/* 预览区与编辑区同色，用一条更明显的分割线（有道云风格，全屏下也清晰） */
:deep(.md-editor-preview-wrapper) {
  background: transparent !important;
  border-left: 2px solid var(--split) !important;
}
:deep(.md-editor-preview) {
  background: transparent !important;
  padding: 16px 22px 40px !important;
  min-height: 100%;
}

/* 只读（MdPreview）模式下不显示工具栏 */
.readonly-mode :deep(.md-editor-toolbar) { display: none !important; }

/* 底部状态条：编辑态隐藏，保留只读态提示 */
.note-editor:not(.readonly-mode) > .editor-statusbar {
  display: none;
}
.editor-statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 16px;
  font-size: 12px;
  color: var(--text-3);
  border-top: 1px solid var(--glass-border);
  background: transparent;
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
.time-saved { color: var(--state-success); }
.time-dirty { color: var(--state-warning); }
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
  stroke: var(--state-success);
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
  padding: 14px 24px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px dashed var(--glass-chip-border);
  border-radius: 10px;
  box-shadow: var(--glass-shadow);
  animation: fade-in-up .3s ease;
}
.guide-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 4px;
}
.guide-tip {
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 8px;
}
.guide-tip code {
  background: var(--hover-bg);
  color: var(--brand);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 11px;
}
.guide-help {
  pointer-events: auto;
  border: none;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
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
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
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

/* ==== Markdown 排版覆盖（.md-editor-preview 容器内，编辑与预览共用）==== */
:deep(.md-editor-preview) {
  font-size: 15px;
  line-height: 1.85;
  word-break: break-word;
}
:deep(.md-editor-preview h1),
:deep(.md-editor-preview h2),
:deep(.md-editor-preview h3),
:deep(.md-editor-preview h4),
:deep(.md-editor-preview h5),
:deep(.md-editor-preview h6) {
  color: var(--text-1) !important;
  font-weight: 700;
  margin: 1.6em 0 .6em;
  line-height: 1.3;
}
:deep(.md-editor-preview h1) { font-size: 2em; padding-bottom: .4em; border-bottom: 1px solid var(--border); }
:deep(.md-editor-preview h2) { font-size: 1.6em; padding-bottom: .3em; border-bottom: 1px solid var(--border); }
:deep(.md-editor-preview h3) { font-size: 1.3em; }
:deep(.md-editor-preview h4) { font-size: 1.15em; }
:deep(.md-editor-preview h5) { font-size: 1em; }
:deep(.md-editor-preview h6) { font-size: .9em; color: var(--text-2) !important; }

:deep(.md-editor-preview p) { margin: .6em 0; }
:deep(.md-editor-preview strong) { font-weight: 700; color: var(--text-1); }
:deep(.md-editor-preview em) { font-style: italic; }

:deep(.md-editor-preview blockquote) {
  margin: 1em 0;
  padding: .5em 1em;
  border-left: 4px solid var(--border);
  background: var(--hover-bg);
  color: var(--text-2) !important;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
:deep(.md-editor-preview blockquote p) { margin: .4em 0; }

/* 代码：行内浅底 + 代码块深灰底等宽字体 */
:deep(.md-editor-preview code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: .88em;
  background: var(--hover-bg) !important;
  color: var(--brand) !important;
  padding: .15em .4em;
  border-radius: 6px;
}
:deep(.md-editor-preview pre) {
  background: var(--hover-bg) !important;
  border: 1px solid var(--border) !important;
  border-radius: 6px;
  padding: 14px 16px !important;
  overflow-x: auto;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace !important;
}
:deep(.md-editor-preview pre code) {
  background: transparent !important;
  color: var(--text-1) !important;
  padding: 0;
  font-size: .9em;
  font-family: inherit !important;
}

/* 列表：圆点 / 数字缩进 */
:deep(.md-editor-preview ul) { list-style: disc; }
:deep(.md-editor-preview ul ul) { list-style: circle; }
:deep(.md-editor-preview ul ul ul) { list-style: square; }
:deep(.md-editor-preview ol) { list-style: decimal; }
:deep(.md-editor-preview ul),
:deep(.md-editor-preview ol) { margin: .6em 0; padding-left: 1.7em; }
:deep(.md-editor-preview li) { margin: .3em 0; }
:deep(.md-editor-preview li > ul),
:deep(.md-editor-preview li > ol) { margin: .2em 0; }
:deep(.md-editor-preview li > p) { margin: .2em 0; }
:deep(.md-editor-preview input[type='checkbox']) { margin-right: .4em; }

/* 分割线与链接 */
:deep(.md-editor-preview hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.6em 0;
}
:deep(.md-editor-preview a) { color: var(--link) !important; text-decoration: none; }
:deep(.md-editor-preview a:hover) { text-decoration: underline; }

/* 表格 */
:deep(.md-editor-preview table) { border-collapse: collapse; margin: 1em 0; }
:deep(.md-editor-preview th),
:deep(.md-editor-preview td) {
  border: 1px solid var(--border);
  padding: 8px 12px;
}
:deep(.md-editor-preview th) { background: var(--hover-bg); font-weight: 600; }
:deep(.md-editor-preview img) { max-width: 100%; border-radius: var(--radius-sm); }

/* === 移动端（<768px）：顶栏收窄、大纲防溢出、隐藏次要信息 === */
@media (max-width: 767px) {
  .note-editor-shell,
  .note-editor-shell.readonly-mode {
    max-width: none;
    margin: 0;
    padding: 0 !important;
  }
  .editor-topbar {
    padding: 5px 8px;
    gap: 4px;
  }
  .editor-title-input :deep(.n-input__input-el) {
    font-size: 15px;
  }
  .mode-toggle-label {
    display: none;
  }
  .statusbar-keys {
    display: none;
  }
  .outline-float {
    width: 170px;
  }
  .editor-statusbar {
    padding: 6px 12px;
  }
  .editor-tags {
    padding: 2px 8px;
  }
  .backlinks-panel {
    right: 8px;
    bottom: 8px;
    width: min(280px, calc(100% - 16px));
  }
  .readonly-mode .backlinks-panel {
    bottom: 34px;
  }
}
</style>
