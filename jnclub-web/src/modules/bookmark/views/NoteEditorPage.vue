<script setup lang="ts">
/**
 * NoteEditorPage.vue — 便签独立查看/编辑页（新标签页打开）
 * 路由：/notes/new（新建，query.directoryId 指定目录）与 /notes/:id（查看，页内编辑/分栏/预览切换）
 * 复用 NoteEditor.vue（md-editor-v3 markdown 编辑器：分屏编辑预览/自动保存/图片上传/快捷键帮助）
 */
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NSpin, NEmpty, NButton, useMessage } from 'naive-ui'
import NoteEditor from '../components/NoteEditor.vue'
import { useNoteStore } from '../stores/note'
import type { Note } from '../stores/note'

const props = defineProps<{
  isDark: boolean
}>()

const route = useRoute()
const router = useRouter()
const message = useMessage()
const noteStore = useNoteStore()

const note = ref<Note | null>(null)
const loading = ref(false)

const isNew = computed(() => route.name === 'note-create')

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
    if (!directoryId) message.warning('未指定所属目录，保存可能失败')
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
  background: var(--bg-card);
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
</style>
