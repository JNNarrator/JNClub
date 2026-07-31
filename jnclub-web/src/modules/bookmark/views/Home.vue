<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import {
  NButton, NIcon, NSpin, NBreadcrumb, NBreadcrumbItem,
  NModal, NForm, NFormItem, NInput, NSpace, NSelect, NAvatar,
  useMessage, useDialog,
} from 'naive-ui'
import { Plus, FolderOpen, Link, Globe, RefreshCw, Folder } from 'lucide-vue-next'
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

// 目录创建
const showCreateDirModal = ref(false)
const creatingDir = ref(false)
const createDirName = ref('')

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

// 当前目录对象
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

// 目录名
const currentDirName = computed(() => {
  if (!currentDirectory.value) return '全部'
  return currentDirectory.value.name
})

// 同级目录（顶层 chips）
const topLevelDirs = computed(() =>
  directoryStore.directories.filter((d: any) => !d.parentId || d.parentId === null)
)

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

// ========== 目录创建 ==========

const handleOpenCreateDir = () => {
  createDirName.value = ''
  showCreateDirModal.value = true
}

const handleCreateDirSubmit = async () => {
  if (!createDirName.value.trim()) { message.warning('请输入名称'); return }
  creatingDir.value = true
  try {
    await axios.post('/api/directories', { name: createDirName.value.trim(), parentId: null, type: directoryType.value })
    message.success('目录创建成功')
    showCreateDirModal.value = false
    createDirName.value = ''
    await handleRefresh()
  } catch (e: any) {
    message.error(e.response?.data?.message || '创建失败')
  } finally { creatingDir.value = false }
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
  createBookmarkForm.value = { title: '', url: '', directoryId: selectedDirectoryId.value }
  previewIcon.value = ''
  previewTitle.value = ''
  showCreateModal.value = true
}

const handleCreate = async () => {
  if (!createBookmarkForm.value.url.trim()) { message.warning('请输入网址'); return }
  if (!isValidUrl(createBookmarkForm.value.url.trim())) { message.warning('请输入正确的网址'); return }
  if (!createBookmarkForm.value.title.trim()) { message.warning('请输入标题'); return }
  if (!createBookmarkForm.value.directoryId && selectedDirectoryId.value) {
    createBookmarkForm.value.directoryId = selectedDirectoryId.value
  }
  if (!createBookmarkForm.value.directoryId) { message.warning('请选择目录'); return }

  creating.value = true
  try {
    await axios.post('/api/bookmarks', {
      title: createBookmarkForm.value.title.trim(),
      url: createBookmarkForm.value.url.trim(),
      directoryId: createBookmarkForm.value.directoryId,
    })
    message.success('收藏成功')
    showCreateModal.value = false
    await loadData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '收藏失败')
  } finally { creating.value = false }
}

// ========== 便签 ==========

const noteEditorRef = ref<InstanceType<typeof NoteEditor> | null>(null)

const handleCreateNote = () => {
  if (!selectedDirectoryId.value) return
  // 本地草稿，不立即入库 — 等用户真正写了内容才保存
  editingNote.value = {
    id: 0, // 新建标记，首次保存时 POST 创建
    title: '',
    content: '',
    directoryId: selectedDirectoryId.value,
    sortOrder: 0,
    createTime: '',
    updateTime: '',
  } as Note
}

const handleEditNote = (note: Note) => {
  editingNote.value = { ...note }
}

const handlePreviewNote = (note: Note) => {
  previewingNote.value = { ...note }
}

const handlePreviewToEdit = () => {
  if (previewingNote.value) {
    editingNote.value = { ...previewingNote.value }
    previewingNote.value = null
  }
}

const handleCloseEditor = () => {
  editingNote.value = null
}

const handleNoteSaved = (updated: Note) => {
  // 更新引用（新建便签需要拿到后端返回的 id），但不关闭编辑器
  editingNote.value = { ...updated }
  loadData()
}

const handleDeleteNote = async (note: Note) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除便签"${note.title}"吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete(`/api/notes/${note.id}`)
        message.success('删除成功')
        loadData()
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    },
  })
}

// ========== FAB 标签 ==========

const fabLabel = computed(() => props.activeModule === 'bookmarks' ? '添加收藏' : '新建便签')

const handleHelp = () => {
  window.open('https://github.com/your-repo/jnclub', '_blank')
}

// ========== 空状态文案 ==========

const emptyMessage = computed(() => props.activeModule === 'bookmarks' ? '这个目录还没有收藏' : '这个目录还没有便签')
const emptyHint = computed(() => props.activeModule === 'bookmarks' ? '点击顶部按钮添加第一个收藏' : '点击右下角 + 创建你的第一篇便签')
</script>

<template>
  <div class="home">
    <!-- 点阵背景 -->
    <div class="ambient-texture"></div>

    <!-- 毛玻璃顶栏 -->
    <header class="home-header glass-header">
      <div class="header-left">
        <NBreadcrumb class="jnclub-breadcrumb">
          <NBreadcrumbItem @click="selectedDirectoryId = null">JNClub</NBreadcrumbItem>
          <NBreadcrumbItem @click="selectedDirectoryId = null">
            {{ props.activeModule === 'bookmarks' ? '收藏夹' : '便签' }}
          </NBreadcrumbItem>
          <NBreadcrumbItem v-if="currentDirName !== '全部'" class="breadcrumb-current">
            {{ currentDirName }}
          </NBreadcrumbItem>
        </NBreadcrumb>
      </div>

      <div class="header-right">
        <ViewSwitcher v-model="viewMode" />
        <NButton
          v-if="props.activeModule === 'bookmarks'"
          class="btn-new jnclub-bouncy-slow"
          @click="handleOpenCreate"
        >
          <template #icon><NIcon :component="Plus" /></template>
          新建
        </NButton>
        <NButton
          v-else
          class="btn-new jnclub-bouncy-slow"
          @click="handleCreateNote"
        >
          <template #icon><NIcon :component="Plus" /></template>
          新建便签
        </NButton>
        <NButton quaternary circle size="small" @click="handleRefresh" class="refresh-btn jnclub-bouncy">
          <template #icon><NIcon :component="RefreshCw" size="16" /></template>
        </NButton>
      </div>
    </header>

    <!-- 主体 -->
    <div class="content-area">
      <!-- 左侧目录树 -->
      <aside class="folder-column">
        <FolderPanel
          :directories="directoryStore.directories"
          :selected-id="selectedDirectoryId"
          :type="directoryType"
          @select="handleDirectorySelect"
          @refresh="handleRefresh"
        />
      </aside>

      <div class="collection-column">
        <!-- 目录 Chip 快速切换 -->
        <div class="chip-bar fade-in-up">
          <!-- 新建目录 chip -->
          <button type="button" class="chip chip-dashed jnclub-bouncy" @click="handleOpenCreateDir">
            <NIcon :component="Folder" size="16" />
            新建目录
          </button>

          <!-- 目录 chips -->
          <button
            v-for="dir in topLevelDirs"
            :key="dir.id"
            :class="['chip', 'jnclub-bouncy', { 'chip-active': dir.id === selectedDirectoryId }]"
            @click="handleDirectorySelect(dir.id)"
          >
            <NIcon :component="FolderOpen" size="16" />
            {{ dir.name }}
          </button>
        </div>

        <NSpin :show="loading" class="spin-area">
          <!-- 收藏卡片网格 -->
          <CollectionGrid
            v-if="props.activeModule === 'bookmarks' && viewMode === 'grid' && bookmarkStore.bookmarks.length > 0"
            :bookmarks="bookmarkStore.bookmarks"
            @refresh="loadData"
          />
          <!-- 收藏列表 -->
          <CollectionList
            v-else-if="props.activeModule === 'bookmarks' && viewMode === 'list' && bookmarkStore.bookmarks.length > 0"
            :bookmarks="bookmarkStore.bookmarks"
            @refresh="loadData"
          />
          <!-- 收藏空状态 -->
          <CollectionEmpty
            v-else-if="props.activeModule === 'bookmarks' && !loading"
            :message="emptyMessage"
          />

          <!-- 便签卡片网格 -->
          <NoteGrid
            v-else-if="props.activeModule === 'notes' && viewMode === 'grid' && noteStore.notes.length > 0"
            :notes="noteStore.notes" :loading="false"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData"
          />
          <!-- 便签列表 -->
          <NoteList
            v-else-if="props.activeModule === 'notes' && viewMode === 'list' && noteStore.notes.length > 0"
            :notes="noteStore.notes" :loading="false"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData"
          />
          <!-- 便签空状态 -->
          <NoteEmpty
            v-else-if="props.activeModule === 'notes' && !loading"
            :message="emptyMessage"
            :hint="emptyHint"
            @create="handleCreateNote"
          />
        </NSpin>
      </div>
    </div>

    <!-- 悬浮 FAB -->
    <FloatingActions
      :add-label="fabLabel"
      @add="props.activeModule === 'bookmarks' ? handleOpenCreate() : handleCreateNote()"
      @help="handleHelp"
    />

    <!-- 目录创建弹窗 -->
    <NModal v-model:show="showCreateDirModal" preset="dialog" title="新建目录">
      <NForm style="margin-top: 12px;">
        <NFormItem label="名称">
          <NInput v-model:value="createDirName" placeholder="请输入目录名称" clearable @keyup.enter="handleCreateDirSubmit" />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace>
          <NButton @click="showCreateDirModal = false">取消</NButton>
          <NButton type="primary" :loading="creatingDir" @click="handleCreateDirSubmit">确定</NButton>
        </NSpace>
      </template>
    </NModal>

    <!-- 收藏创建弹窗 -->
    <NModal v-model:show="showCreateModal" preset="dialog" title="添加收藏">
      <NForm :model="createBookmarkForm" style="margin-top: 12px;">
        <NFormItem label="网址" path="url">
          <NInput v-model:value="createBookmarkForm.url" placeholder="https://example.com" clearable @input="onUrlInput">
            <template #prefix><NIcon :component="Link" /></template>
          </NInput>
        </NFormItem>
        <div v-if="previewTitle || previewLoading" class="preview-bar">
          <NSpin :show="previewLoading" size="small">
            <NAvatar v-if="previewIcon" :src="previewIcon" size="small" round class="preview-avatar" />
            <NIcon v-else :component="Globe" size="20" style="color: var(--text-3); flex-shrink: 0;" />
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
      to="body"
      style="width: 95vw; height: 92vh; max-width: 1400px;"
      :bordered="false"
      :mask-closable="false"
      @update:show="(v: boolean) => { if (!v) handleCloseEditor() }"
    >
      <NoteEditor
        ref="noteEditorRef"
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
      to="body"
      title="预览便签"
      style="width: 90vw; max-width: 1000px; height: 85vh;"
      :bordered="false"
      @update:show="(v: boolean) => { if (!v) { previewingNote = null } }"
    >
      <template #header-extra>
        <NSpace>
          <NButton size="small" type="primary" @click="handlePreviewToEdit">
            <template #icon><NIcon :component="Plus" size="14" /></template>
            编辑
          </NButton>
          <NButton size="small" quaternary @click="previewingNote = null">关闭</NButton>
        </NSpace>
      </template>
      <NotePreview :note="previewingNote" :is-dark="props.isDark" />
    </NModal>
  </div>
</template>

<style scoped>
.home {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 1;
}

/* === 顶栏：毛玻璃 === */
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  gap: 16px;
}
.header-left {
  flex: 1;
  min-width: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.jnclub-breadcrumb :deep(.n-breadcrumb-item__link) {
  cursor: pointer;
  font-size: 13px;
}
.breadcrumb-current :deep(.n-breadcrumb-item__link) {
  font-weight: 600;
  color: var(--text-1);
}

.refresh-btn {
  color: var(--text-2);
}
.refresh-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}

/* === 主体 === */
.content-area {
  flex: 1;
  display: flex;
  gap: 20px;
  min-height: 0;
  padding: 20px 24px;
}

.folder-column {
  width: 220px;
  flex-shrink: 0;
}

.collection-column {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

/* === Chip 标签栏 === */
.chip-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
}

/* === Spin === */
.spin-area {
  min-height: 200px;
}

/* === 预览条 === */
.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--hover-bg);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-2);
}
.preview-avatar { flex-shrink: 0; }
.preview-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
