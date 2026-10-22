<script setup lang="ts">
/**
 * ModuleToolbar.vue — 模块内部工具栏
 * 从 Home.vue 拆出，按模块显示新建/检测失效/导出/归档/上传等操作。
 */
import { NButton, NIcon } from 'naive-ui'
import { Plus, ShieldAlert, Download, Archive, UploadCloud } from 'lucide-vue-next'
import type { ModuleKey } from '../../../shared/types/nav'

defineProps<{
  activeModule: ModuleKey
  bookmarkCount: number
  noteCount: number
  notesArchived: boolean
  canUpload: boolean
}>()

const emit = defineEmits<{
  'create-bookmark': []
  'check-dead': []
  'create-note': []
  'export-notes': []
  'toggle-archived': []
  'upload-file': []
}>()
</script>

<template>
  <div class="module-toolbar fade-in-up">
    <NButton
      v-if="activeModule === 'bookmarks'"
      size="small"
      class="btn-new jnclub-bouncy-slow"
      @click="emit('create-bookmark')"
    >
      <template #icon><NIcon :component="Plus" /></template>
      新建收藏
    </NButton>
    <NButton
      v-if="activeModule === 'bookmarks'"
      size="small"
      class="io-export-btn jnclub-bouncy"
      :disabled="!bookmarkCount"
      title="检测全部收藏链接是否失效（死链检测）"
      @click="emit('check-dead')"
    >
      <template #icon><NIcon :component="ShieldAlert" size="15" /></template>
      检测失效
    </NButton>
    <NButton
      v-if="activeModule === 'notes'"
      size="small"
      class="btn-new jnclub-bouncy-slow"
      @click="emit('create-note')"
    >
      <template #icon><NIcon :component="Plus" /></template>
      新建便签
    </NButton>
    <NButton
      v-if="activeModule === 'notes'"
      size="small"
      class="io-export-btn jnclub-bouncy"
      :disabled="!noteCount"
      title="导出当前目录全部便签为 .md（列表瘦身后按需拉取正文）"
      @click="emit('export-notes')"
    >
      <template #icon><NIcon :component="Download" size="15" /></template>
      导出全部
    </NButton>
    <NButton
      v-if="activeModule === 'notes'"
      size="small"
      :class="['archived-toggle', notesArchived ? 'archived-active' : '']"
      :title="notesArchived ? '返回正常便签' : '查看已归档便签'"
      @click="emit('toggle-archived')"
    >
      <template #icon><NIcon :component="Archive" size="15" /></template>
      {{ notesArchived ? '返回' : '归档' }}
    </NButton>
    <NButton
      v-if="activeModule === 'files'"
      size="small"
      class="btn-new jnclub-bouncy-slow"
      :disabled="!canUpload"
      @click="emit('upload-file')"
    >
      <template #icon><NIcon :component="UploadCloud" /></template>
      上传文件
    </NButton>
  </div>
</template>

<style scoped>
.module-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.io-export-btn {
  border-radius: var(--radius-pill) !important;
  /* 内联控件（判据 N4）：控件面走 --bg-card，描边走 --surface-border；删 blur */
  background: var(--bg-card) !important;
  border: 1px solid var(--surface-border) !important;
  color: var(--text-2) !important;
}
.io-export-btn:hover {
  color: var(--brand-text) !important;
  border-color: var(--brand-text) !important;
}
.io-export-btn[disabled] {
  opacity: 0.45;
}

.archived-toggle {
  border-radius: var(--radius-pill) !important;
  background: var(--bg-card) !important;
  border: 1px solid var(--surface-border) !important;
  color: var(--text-2) !important;
}
.archived-toggle:hover {
  color: var(--brand-text) !important;
  border-color: var(--brand-text) !important;
}
.archived-toggle.archived-active {
  color: var(--brand-text) !important;
  border-color: var(--brand-text) !important;
  background: var(--brand-soft) !important;
}
</style>
