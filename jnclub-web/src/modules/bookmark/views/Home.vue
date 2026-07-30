<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import {
  NButton, NIcon, NSpin, NBreadcrumb, NBreadcrumbItem,
  NModal, NForm, NFormItem, NInput, NSpace, NSelect, NAvatar,
  useMessage, useDialog,
} from 'naive-ui'
import {
  AddOutline, FolderOutline, LinkOutline,
  DocumentTextOutline, GlobeOutline,
  RefreshOutline,
} from '@vicons/ionicons5'
import { useDirectoryStore } from '../stores/directory'
import { useBookmarkStore } from '../stores/bookmark'
import { useNoteStore } from '../stores/note'
import type { Note } from '../stores/note'
import FolderPanel from '../components/FolderPanel.vue'
import CollectionGrid from '../components/CollectionGrid.vue'
import CollectionList from '../components/CollectionList.vue'
import CollectionEmpty from '../components/CollectionEmpty.vue'
import NoteGrid from '../components/NoteGrid.vue'
import NoteList from '../components/NoteList.vue'
import NoteEmpty from '../components/NoteEmpty.vue'
import NoteEditor from '../components/NoteEditor.vue'
import NotePreview from '../components/NotePreview.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import FloatingActions from '../components/FloatingActions.vue'
import type { ViewMode } from '../components/ViewSwitcher.vue'
import axios from 'axios'

const directoryStore = useDirectoryStore()
const bookmarkStore = useBookmarkStore()
const noteStore = useNoteStore()
const message = useMessage()
const dialog = useDialog()

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes'
  isDark: boolean
}>()

const directoryType = computed(() => props.activeModule === 'bookmarks' ? 1 : 2)

const selectedDirectoryId = ref<number | null>(null)
const loading = ref(false)
const viewMode = ref<ViewMode>('grid')

// 收藏创建表单
const showCreateModal = ref(false)
const creating = ref(false)
const createBookmarkForm = ref({ title: '', url: '', directoryId: null as number | null })

// URL 预览
const previewIcon = ref('')
const previewTitle = ref('')
const previewLoading = ref(false)
let previewTimer: ReturnType<typeof setTimeout> | null = null

// 便签编辑/预览态
const editingNote = ref<Note | null>(null)
const previewingNote = ref<Note | null>(null)

const directoryOptions = computed(() =>
  directoryStore.directories.map(d => ({ label: d.name, value: d.id }))
)

const currentDirectory = computed(() => {
  if (!selectedDirectoryId.value) return null
  const findDir = (dirs: any[]): any => {
    for (const dir of dirs) {
      if (dir.id === selectedDirectoryId.value) return dir
      if (dir.children) {
        const found = findDir(dir.children)
        if (found) return found
      }
    }
    return null
  }
  return findDir(directoryStore.directories)
})

// ========== 目录加载 ==========

const loadDirectories = async () => {
  await directoryStore.fetchDirectories(directoryType.value)
}

onMounted(async () => {
  loading.value = true
  try {
    await loadDirectories()
    if (directoryStore.directories.length > 0) {
      selectedDirectoryId.value = directoryStore.directories[0].id
      await loadData()
    }
  } finally { loading.value = false }
})

watch(() => props.activeModule, async () => {
  selectedDirectoryId.value = null
  editingNote.value = null
  previewingNote.value = null
  await loadDirectories()
  if (directoryStore.directories.length > 0) {
    selectedDirectoryId.value = directoryStore.directories[0].id
    await loadData()
  }
})

watch(directoryType, () => { viewMode.value = 'grid' })

const handleDirectorySelect = async (id: number) => {
  selectedDirectoryId.value = id
  editingNote.value = null
  previewingNote.value = null
  await loadData()
}

const loadData = async () => {
  if (!selectedDirectoryId.value) return
  loading.value = true
  try {
    if (props.activeModule === 'bookmarks') {
      await bookmarkStore.fetchBookmarks(selectedDirectoryId.value)
    } else {
      await noteStore.fetchNotes(selectedDirectoryId.value)
    }
  } finally { loading.value = false }
}

const handleRefresh = async () => {
  await loadDirectories()
  await loadData()
}

// ========== URL 预览与收藏创建 ==========

const isValidUrl = (url: string) => {
  try { new URL(url); return true } catch { return false }
}

const onUrlInput = () => {
  if (previewTimer) clearTimeout(previewTimer)
  const url = createBookmarkForm.value.url.trim()
  if (!isValidUrl(url)) { previewIcon.value = ''; previewTitle.value = ''; return }
  previewTimer = setTimeout(async () => {
    previewLoading.value = true
    try {
      const res = await axios.get('/api/bookmarks/preview', { params: { url } })
      if (res.data.code === 200 && res.data.data) {
        previewTitle.value = res.data.data.title || ''
        previewIcon.value = res.data.data.icon || ''
        if (!createBookmarkForm.value.title.trim()) createBookmarkForm.value.title = previewTitle.value
      }
    } catch { /* 静默 */ }
    finally { previewLoading.value = false }
  }, 600)
}

const handleOpenCreate = () => {
  if (props.activeModule === 'bookmarks') {
    createBookmarkForm.value = { title: '', url: '', directoryId: selectedDirectoryId.value }
    previewIcon.value = ''; previewTitle.value = ''
    showCreateModal.value = true
  } else {
    // 便签：直接在当前目录创建空便签并进入编辑态
    handleCreateNoteAndEdit()
  }
}

const handleCreate = async () => {
  creating.value = true
  try {
    if (props.activeModule === 'bookmarks') {
      await axios.post('/api/bookmarks', {
        title: createBookmarkForm.value.title || '',
        url: createBookmarkForm.value.url,
        directoryId: createBookmarkForm.value.directoryId || selectedDirectoryId.value,
      })
      message.success('收藏成功')
      showCreateModal.value = false
      await loadData()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '添加失败')
  } finally { creating.value = false }
}

// ========== 便签操作 ==========

/** 新建便签并直接进入编辑态 */
const handleCreateNoteAndEdit = async () => {
  if (!selectedDirectoryId.value) {
    message.warning('请先选择一个目录')
    return
  }
  try {
    const note = await noteStore.createNote({
      title: '',
      directoryId: selectedDirectoryId.value,
    })
    editingNote.value = note
    await loadData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '创建失败')
  }
}

/** 空态"新建便签"按钮 */
const handleEmptyCreate = () => {
  handleCreateNoteAndEdit()
}

/** 进入编辑 */
const handleEditNote = async (note: Note) => {
  try {
    await noteStore.fetchNoteDetail(note.id)
    editingNote.value = noteStore.currentNote
  } catch (e: any) {
    message.error(e.message || '获取便签失败')
  }
}

/** 进入预览 */
const handlePreviewNote = async (note: Note) => {
  try {
    await noteStore.fetchNoteDetail(note.id)
    previewingNote.value = noteStore.currentNote
  } catch (e: any) {
    message.error(e.message || '获取便签失败')
  }
}

/** 删除便签 */
const handleDeleteNote = (note: Note) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除便签"${note.title}"吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await noteStore.deleteNote(note.id)
        message.success('删除成功')
        await loadData()
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    },
  })
}

/** 编辑保存回调 */
const handleNoteSaved = () => {
  loadData()
}

/** 预览中→编辑 */
const handlePreviewToEdit = () => {
  if (previewingNote.value) {
    editingNote.value = previewingNote.value
    previewingNote.value = null
  }
}

/** 关闭编辑器/预览，刷新列表 */
const handleCloseEditor = () => {
  editingNote.value = null
  previewingNote.value = null
  loadData()
}

// ========== 辅助 ==========

const handleHelp = () => {
  if (props.activeModule === 'bookmarks') {
    message.info('收藏夹使用说明：点击右下角 + 添加网址收藏')
  } else {
    message.info('便签使用说明：支持 Markdown，可粘贴/拖拽/上传图片，Ctrl+S 保存')
  }
}

/** 是否为便签模式的空态 */
const isNotesEmpty = computed(() =>
  props.activeModule === 'notes' && !loading.value && noteStore.notes.length === 0
)

/** 是否为收藏模式的空态 */
const fabLabel = computed(() => props.activeModule === 'bookmarks' ? '添加收藏' : '新建便签')

const isBookmarkEmpty = computed(() =>
  props.activeModule === 'bookmarks' && !loading.value && bookmarkStore.bookmarks.length === 0
)
</script>

<template>
  <div class="home">
    <!-- 点纹背景 -->
    <div class="ambient-texture" />

    <!-- 顶栏 -->
    <div class="top-bar">
      <div class="bar-left">
        <NBreadcrumb>
          <NBreadcrumbItem>JNClub</NBreadcrumbItem>
          <NBreadcrumbItem>
            <NIcon :component="props.activeModule === 'bookmarks' ? FolderOutline : DocumentTextOutline" size="16" />
            {{ props.activeModule === 'bookmarks' ? '收藏夹' : '便签' }}
          </NBreadcrumbItem>
          <NBreadcrumbItem v-if="currentDirectory">{{ currentDirectory.name }}</NBreadcrumbItem>
        </NBreadcrumb>
      </div>
      <div class="bar-center">
        <ViewSwitcher v-model="viewMode" />
      </div>
      <div class="bar-right">
        <NButton :type="props.activeModule === 'notes' ? 'primary' : 'default'" size="small" @click="handleOpenCreate">
          <template #icon><NIcon :component="AddOutline" size="16" /></template>
          新建
        </NButton>
        <NButton size="small" quaternary @click="handleRefresh">
          <template #icon><NIcon :component="RefreshOutline" size="16" /></template>
        </NButton>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-area">
      <aside class="folder-column">
        <FolderPanel :key="directoryType"
          :directories="directoryStore.directories"
          :selected-id="selectedDirectoryId"
          :type="directoryType"
          @select="handleDirectorySelect"
          @refresh="handleRefresh"
        />
      </aside>

      <main class="collection-column">
        <NSpin :show="loading">
          <!-- 收藏空态 -->
          <CollectionEmpty v-if="isBookmarkEmpty" />
          <!-- 便签空态 -->
          <NoteEmpty
            v-else-if="isNotesEmpty"
            message="这个目录还没有便签"
            hint="点击右下角 + 创建你的第一篇便签"
            @create="handleEmptyCreate"
          />
          <!-- 收藏网格 -->
          <CollectionGrid v-else-if="props.activeModule === 'bookmarks' && viewMode === 'grid'"
            :bookmarks="bookmarkStore.bookmarks" :loading="false" @refresh="loadData" />
          <!-- 收藏列表 -->
          <CollectionList v-else-if="props.activeModule === 'bookmarks' && viewMode === 'list'"
            :bookmarks="bookmarkStore.bookmarks" :loading="false" @refresh="loadData" />
          <!-- 便签网格 -->
          <NoteGrid v-else-if="props.activeModule === 'notes' && viewMode === 'grid'"
            :notes="noteStore.notes" :loading="false"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData" />
          <!-- 便签列表 -->
          <NoteList v-else-if="props.activeModule === 'notes' && viewMode === 'list'"
            :notes="noteStore.notes" :loading="false"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData" />
        </NSpin>
      </main>
    </div>

    <!-- 悬浮操作 -->
    <FloatingActions
      :add-label="fabLabel"
      @add="handleOpenCreate"
      @refresh="handleRefresh"
      @help="handleHelp"
    />

    <!-- 收藏创建弹窗 -->
    <NModal v-model:show="showCreateModal" preset="dialog" title="添加收藏">
      <NForm :model="createBookmarkForm" style="margin-top: 12px;">
        <NFormItem label="网址" path="url">
          <NInput v-model:value="createBookmarkForm.url" placeholder="https://example.com" clearable @input="onUrlInput">
            <template #prefix><NIcon :component="LinkOutline" /></template>
          </NInput>
        </NFormItem>
        <div v-if="previewTitle || previewLoading" class="preview-bar">
          <NSpin :show="previewLoading" size="small">
            <NAvatar v-if="previewIcon" :src="previewIcon" size="small" round class="preview-avatar" />
            <NIcon v-else :component="GlobeOutline" size="20" style="color: var(--text-3); flex-shrink: 0;" />
          </NSpin>
          <span class="preview-title">{{ previewTitle || '正在获取网页信息…' }}</span>
        </div>
        <NFormItem label="标题" path="title">
          <NInput v-model:value="createBookmarkForm.title" placeholder="留空自动从网页获取" clearable />
        </NFormItem>
        <NFormItem label="所属目录" path="directoryId">
          <NSelect v-model:value="createBookmarkForm.directoryId" :options="directoryOptions" placeholder="选择目录" clearable />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace>
          <NButton @click="showCreateModal = false">取消</NButton>
          <NButton type="primary" :loading="creating" @click="handleCreate">确定</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 便签编辑器（全屏覆盖） -->
    <NModal
      v-if="editingNote"
      :show="editingNote !== null"
      preset="card"
      style="width: 95vw; height: 92vh; max-width: 1400px;"
      :bordered="false"
      :mask-closable="false"
      @update:show="(v: boolean) => { if (!v) handleCloseEditor() }"
    >
      <NoteEditor
        :note="editingNote"
        :is-dark="props.isDark"
        @close="handleCloseEditor"
        @saved="handleNoteSaved"
        @deleted="(n: Note) => handleDeleteNote(n)"
      />
    </NModal>

    <!-- 便签预览 -->
    <NModal
      v-if="previewingNote"
      :show="previewingNote !== null"
      preset="card"
      title="预览便签"
      style="width: 90vw; max-width: 1000px; height: 85vh;"
      :bordered="false"
      @update:show="(v: boolean) => { if (!v) { previewingNote = null } }"
    >
      <template #header-extra>
        <NSpace>
          <NButton size="small" type="primary" @click="handlePreviewToEdit">
            <template #icon><NIcon :component="AddOutline" size="14" /></template>
            编辑
          </NButton>
          <NButton size="small" quaternary @click="previewingNote = null">关闭</NButton>
        </NSpace>
      </template>
      <NotePreview
        :note="previewingNote"
        :is-dark="props.isDark"
      />
    </NModal>
  </div>
</template>

<style scoped>
.ambient-texture {
  position: fixed; inset: 0; pointer-events: none; z-index: 0; opacity: 0.03;
  background-image: radial-gradient(circle at 1px 1px, var(--brand) 1px, transparent 0);
  background-size: 20px 20px;
}
.home {
  position: relative; height: 100%; display: flex; flex-direction: column;
  padding: 24px 28px; z-index: 1;
}
.top-bar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px; padding-bottom: 16px;
  border-bottom: 1px solid var(--border); gap: 16px;
}
.bar-left { flex: 1; min-width: 0; }
.bar-center { flex-shrink: 0; }
.bar-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.content-area { flex: 1; display: flex; gap: 24px; min-height: 0; }
.folder-column { width: 260px; flex-shrink: 0; }
.collection-column { flex: 1; min-width: 0; overflow-y: auto; }
.preview-bar {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px;
  margin-bottom: 16px; background: var(--hover-bg); border-radius: var(--radius-sm);
  font-size: 13px; color: var(--text-2);
}
.preview-avatar { flex-shrink: 0; }
.preview-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:deep(.n-breadcrumb-item--active .n-breadcrumb-item__link) {
  font-weight: 600; color: var(--text-1);
}
</style>
