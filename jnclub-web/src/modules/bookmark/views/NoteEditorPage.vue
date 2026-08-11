<script setup lang="ts">
/**
 * NoteEditorPage.vue — 便签独立查看/编辑页（新标签页打开）
 * 路由：/notes/new（新建，query.directoryId 指定目录）与 /notes/:id（查看，页内编辑/分栏/预览切换）
 * 复用 NoteEditor.vue（md-editor-v3 markdown 编辑器：分屏编辑预览/自动保存/图片上传/快捷键帮助）
 */
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSpin, NEmpty, NButton, NSelect, useMessage } from 'naive-ui'
import NoteEditor from '../components/NoteEditor.vue'
import { useNoteStore } from '../stores/note'
import { useDirectoryStore, type Directory } from '../stores/directory'
import type { Note } from '../stores/note'

const props = defineProps<{
  isDark: boolean
}>()

const route = useRoute()
const router = useRouter()
const message = useMessage()
const noteStore = useNoteStore()
const directoryStore = useDirectoryStore()

const note = ref<Note | null>(null)
const loading = ref(false)

const isNew = computed(() => route.name === 'note-create')

/** 新建便签缺目录时的选择器：拉取便签目录（type=2）并展平为下拉选项 */
const dirOptions = ref<{ label: string; value: number }[]>([])
const flattenDirs = (list: Directory[], depth = 0): { label: string; value: number }[] => {
  const out: { label: string; value: number }[] = []
  for (const d of list) {
    out.push({ label: `${'　'.repeat(depth)}${d.name}`, value: d.id })
    if (d.children?.length) out.push(...flattenDirs(d.children, depth + 1))
  }
  return out
}
const loadDirOptions = async () => {
  await directoryStore.fetchDirectories(2)
  dirOptions.value = flattenDirs(directoryStore.directories)
}

/** 选择目录 → 回填 note.directoryId（保存时 POST 携带） */
const onPickDirectory = (id: number) => {
  if (note.value) note.value.directoryId = id
}

onMounted(async () => {
  if (isNew.value) {
    // 新建：本地草稿，首次保存时 POST 创建
    const directoryId = Number(route.query.directoryId) || 0
    note.value = {
      id: 0,
      title: '',
      content: '',
      directoryId,
      sortOrder: 0,
      createTime: '',
      updateTime: '',
    } as Note
    if (!directoryId) {
      message.warning('未指定所属目录，请选择后保存')
      await loadDirOptions()
    }
    return
  }
  // 编辑：拉取最新内容
  loading.value = true
  try {
    const id = Number(route.params.id as string)
    if (!id) throw new Error('便签 ID 无效')
    await noteStore.fetchNoteDetail(id)
    note.value = noteStore.currentNote ? { ...noteStore.currentNote } : null
    if (!note.value) message.warning('便签不存在或已删除')
  } catch (e: any) {
    message.error(e?.message || '获取便签失败')
  } finally {
    loading.value = false
  }
})

/** 保存成功：更新引用；新建便签保存后把 URL 纠正为查看态，避免刷新丢失 */
const handleSaved = (updated: Note) => {
  note.value = { ...updated }
  if (isNew.value && updated.id !== 0) {
    router.replace({ name: 'note-view', params: { id: updated.id } })
  }
}

/** 关闭/离开：打开者页面（opener）仍在则关闭当前标签页回到原页面；否则跳回首页 */
const handleClose = () => {
  if (window.opener && !window.opener.closed) {
    window.close()
    return
  }
  router.push('/')
}

const handleDeleted = () => {
  message.success('删除成功')
  handleClose()
}
</script>

<template>
  <div class="note-editor-page">
    <div v-if="isNew && note && !note.directoryId" class="dir-picker-bar">
      <span class="dir-picker-label">保存到目录</span>
      <NSelect
        :value="note.directoryId || null"
        :options="dirOptions"
        placeholder="选择所属目录"
        clearable
        size="small"
        class="dir-picker-select"
        @update:value="(v: number | null) => { if (v) onPickDirectory(v) }"
      />
    </div>
    <NSpin :show="loading" class="page-spin">
      <NoteEditor
        v-if="note"
        :note="note"
        :is-dark="props.isDark"
        @close="handleClose"
        @saved="handleSaved"
        @deleted="handleDeleted"
      />
      <div v-else-if="!loading" class="page-error">
        <NEmpty description="便签不存在或已删除" />
        <NButton type="primary" size="small" @click="handleClose">返回</NButton>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.note-editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--bg-page);
}
.page-spin {
  flex: 1;
  min-height: 0;
}
/* NSpin 容器链撑满，保证 md-editor-v3 高度 100% 生效 */
.page-spin :deep(.n-spin-container),
.page-spin :deep(.n-spin-content) {
  height: 100%;
}
.page-error {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
/* 新建便签缺目录时的目录选择条 */
.dir-picker-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.dir-picker-label {
  font-size: var(--fs-sm);
  color: var(--text-3);
  flex-shrink: 0;
}
.dir-picker-select {
  max-width: 280px;
}
</style>
