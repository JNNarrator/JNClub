<script setup lang="ts">
/**
 * NotePreview.vue — Markdown 只读预览
 * 复刻 md-editor-v3 的预览渲染效果
 */
import { computed } from 'vue'
import { NEmpty } from 'naive-ui'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import type { Note } from '../stores/note'

const props = defineProps<{
  note: Note
  isDark: boolean
}>()

const isEmpty = computed(() => !props.note.content || props.note.content.trim() === '')
</script>

<template>
  <div class="note-preview">
    <div v-if="isEmpty" class="preview-empty">
      <NEmpty description="预览将在此显示" />
    </div>
    <MdPreview
      v-else
      :model-value="note.content || ''"
      :theme="isDark ? 'dark' : 'light'"
      :preview-theme="isDark ? 'github' : 'github'"
      language="zh-CN"
    />
  </div>
</template>

<style scoped>
.note-preview {
  padding: 32px;
  max-width: 860px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* md-editor-v3 预览主题覆盖 */
:deep(.md-editor-preview) {
  background: var(--bg-card) !important;
}
:deep(.md-editor-preview .md-editor-preview-wrapper) {
  color: var(--text-1) !important;
  font-size: 15px;
  line-height: 1.8;
}
:deep(.md-editor-preview a) {
  color: var(--link) !important;
}
:deep(.md-editor-preview h1),
:deep(.md-editor-preview h2),
:deep(.md-editor-preview h3) {
  color: var(--text-1) !important;
  border-bottom-color: var(--border) !important;
}
:deep(.md-editor-preview code) {
  background: var(--hover-bg) !important;
  color: var(--brand) !important;
}
:deep(.md-editor-preview pre) {
  background: var(--hover-bg) !important;
  border-color: var(--border) !important;
}
:deep(.md-editor-preview blockquote) {
  border-left-color: var(--brand) !important;
  color: var(--text-2) !important;
  background: var(--brand-soft) !important;
}
:deep(.md-editor-preview table) {
  border-color: var(--border) !important;
}
:deep(.md-editor-preview th),
:deep(.md-editor-preview td) {
  border-color: var(--border) !important;
}
:deep(.md-editor-preview th) {
  background: var(--hover-bg) !important;
}
:deep(.md-editor-preview img) {
  max-width: 100%;
  border-radius: var(--radius-sm);
}
</style>
