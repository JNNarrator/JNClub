<script setup lang="ts">
/**
 * NoteEditor.vue — 封装 md-editor-v3 v5
 * 三态切换（编辑/预览/全屏）+ 图片上传 + 自动保存 + 离开提示
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { NButton, NIcon, useMessage } from 'naive-ui'
import {
  ArrowBackOutline, SaveOutline, CloseOutline,
} from '@vicons/ionicons5'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
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

const content = ref('')
const title = ref('')
const saving = ref(false)
const autoSaveTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const hasUnsavedChanges = ref(false)

watch(() => props.note, (note) => {
  if (note) {
    content.value = note.content || ''
    title.value = note.title || ''
    hasUnsavedChanges.value = false
  }
}, { immediate: true })

/** md-editor-v3 内容变化事件 */
const handleChange = (v: string) => {
  content.value = v
  hasUnsavedChanges.value = true
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  autoSaveTimer.value = setTimeout(() => handleSave(true), 3000)
}

const handleTitleChange = () => { hasUnsavedChanges.value = true }

/** 保存 */
const handleSave = async (silent = false) => {
  if (!props.note || saving.value) return
  if (!hasUnsavedChanges.value && silent) return
  saving.value = true
  try {
    await axios.put(`/api/notes/${props.note.id}`, {
      title: title.value || '',
      content: content.value,
    })
    hasUnsavedChanges.value = false
    if (!silent) message.success('已保存')
    emit('saved', { ...props.note, title: title.value, content: content.value } as Note)
  } catch (e: any) {
    message.error(e.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

/** 图片上传：粘贴/拖拽/工具栏统一走此钩子 */
const handleUploadImg = async (
  files: Array<File>,
  callback: (urls: Array<{ url: string; alt: string; title: string }>) => void
) => {
  const urls: Array<{ url: string; alt: string; title: string }> = []
  for (const file of files) {
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await axios.post('/api/upload/image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (res.data.code === 200) {
        urls.push({ url: res.data.data.url, alt: '', title: '' })
      } else {
        message.error(res.data.message || '上传失败')
      }
    } catch (e: any) {
      message.error(e.response?.status === 401 ? '请先登录' : (e.response?.data?.message || '图片上传失败'))
    }
  }
  callback(urls)
}

/** Ctrl/Cmd+S 快捷保存 */
const handleKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') { e.preventDefault(); handleSave() }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const toolbars: any[] = [
  'bold', 'underline', 'italic', '-',
  'strikeThrough', 'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList',
  'task', '-', 'codeRow', 'code', 'link', 'image', 'table',
  '-', 'revoke', 'next', 'save', '=', 'pageFullscreen', 'fullscreen', 'preview',
  'previewOnly', 'htmlPreview', 'catalog',
]

onBeforeUnmount(() => { if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value) })

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
</script>

<template>
  <div class="note-editor">
    <div class="editor-topbar">
      <NButton quaternary size="small" @click="emit('close')">
        <template #icon><NIcon :component="ArrowBackOutline" size="18" /></template>
        返回
      </NButton>
      <div class="editor-title-wrap">
        <input v-model="title" class="editor-title-input" placeholder="无标题" @input="handleTitleChange" />
      </div>
      <NButton type="primary" size="small" :loading="saving" @click="handleSave()">
        <template #icon><NIcon :component="SaveOutline" size="16" /></template>
        保存
      </NButton>
      <NButton quaternary size="small" @click="emit('close')">
        <template #icon><NIcon :component="CloseOutline" size="16" /></template>
      </NButton>
    </div>

    <div class="editor-body">
      <MdEditor
        v-if="props.note !== null"
        v-model="content"
        :theme="props.isDark ? 'dark' : 'light'"
        :preview-theme="props.isDark ? 'github' : 'github'"
        :toolbars="toolbars"
        language="zh-CN"
        style="height: 100%;"
        :on-change="handleChange"
        :on-save="() => { handleSave() }"
        :on-upload-img="handleUploadImg"
      />
    </div>
  </div>
</template>

<style scoped>
.note-editor {
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
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
  outline: none;
  padding: 4px 0;
}
.editor-title-input::placeholder { color: var(--text-4); }
.editor-body { flex: 1; min-height: 0; overflow: hidden; }

/* md-editor-v3 亮/暗主题覆盖 */
:deep(.md-editor) { border: none !important; border-radius: 0 !important; height: 100% !important; }
:deep(.md-editor-toolbar) { border-color: var(--border) !important; background: var(--bg-card) !important; }
:deep(.md-editor-toolbar button) { color: var(--text-2) !important; }
:deep(.md-editor-toolbar button:hover) { color: var(--text-1) !important; background: var(--hover-bg) !important; }
:deep(.md-editor-toolbar .active) { color: var(--brand) !important; background: var(--brand-soft) !important; }
:deep(.cm-editor) { background: var(--bg-card) !important; }
:deep(.cm-editor .cm-content) { color: var(--text-1) !important; }
:deep(.cm-editor .cm-cursor) { border-left-color: var(--brand) !important; }
:deep(.md-editor-preview) { background: var(--bg-card) !important; }
:deep(.md-editor-preview .md-editor-preview-wrapper) { color: var(--text-1) !important; }
:deep(.md-editor-preview a) { color: var(--link) !important; }
:deep(.md-editor-preview code) { background: var(--hover-bg) !important; color: var(--brand) !important; }
:deep(.md-editor-preview pre) { background: var(--hover-bg) !important; }
:deep(.md-editor-footer) { background: var(--bg-card) !important; border-color: var(--border) !important; color: var(--text-3) !important; }
</style>
