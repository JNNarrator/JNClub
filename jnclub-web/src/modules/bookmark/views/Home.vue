<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import {
  NButton, NIcon, NSpin, NBreadcrumb, NBreadcrumbItem,
  NModal, NForm, NFormItem, NInput, NSpace, NSelect, NAvatar,
  useMessage, useDialog,
} from 'naive-ui'
import { Plus, FolderOpen, Link, Globe, RefreshCw, UploadCloud } from 'lucide-vue-next'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { useDirectoryStore } from '../stores/directory'
import { useBookmarkStore } from '../stores/bookmark'
import { useNoteStore } from '../stores/note'
import { useCloudDiskStore } from '../stores/clouddisk'
import type { Note } from '../stores/note'
import FolderPanel from '../components/FolderPanel.vue'
import CollectionGrid from '../components/CollectionGrid.vue'
import CollectionList from '../components/CollectionList.vue'
import CollectionEmpty from '../components/CollectionEmpty.vue'
import NoteGrid from '../components/NoteGrid.vue'
import NoteList from '../components/NoteList.vue'
import NoteEmpty from '../components/NoteEmpty.vue'
import DiskView from '../components/DiskView.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import FloatingActions from '../components/FloatingActions.vue'
import type { ViewMode } from '../components/ViewSwitcher.vue'
import axios from 'axios'
import { useUserPreferences } from '../../../shared/composables/useUserPreferences'

const router = useRouter()
const prefs = useUserPreferences()
const directoryStore = useDirectoryStore()
const bookmarkStore = useBookmarkStore()
const noteStore = useNoteStore()
const cloudDiskStore = useCloudDiskStore()
const message = useMessage()
const dialog = useDialog()

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes' | 'files'
  isDark: boolean
}>()

const directoryType = computed(() => props.activeModule === 'bookmarks' ? 1 : props.activeModule === 'notes' ? 2 : 3)

const selectedDirectoryId = ref<number | null>(null)
const loading = ref(false)
/** 视图模式：按模块记忆（后端偏好），便签默认极简 list、收藏夹默认卡片 grid */
const viewMode = ref<ViewMode>(prefs.get(`view.${props.activeModule}`, props.activeModule === 'notes' ? 'list' : 'grid'))

// 目录创建

// 收藏创建/编辑表单
const showCreateModal = ref(false)
const creating = ref(false)
const editingBookmarkId = ref<number | null>(null)
const createBookmarkForm = ref({ title: '', url: '', directoryId: null as number | null })

// URL 预览
const previewIcon = ref('')
const previewTitle = ref('')
const previewLoading = ref(false)
let previewTimer: ReturnType<typeof setTimeout> | null = null

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

/** 选中目录按模块记忆：key dir.bookmarks / dir.notes */
const dirPrefKey = () => `dir.${props.activeModule}`

/** 目录加载后恢复上次选中的目录（偏好值无效或已删除则回退第一个） */
const applyRememberedDir = () => {
  const dirs = directoryStore.directories
  if (!dirs.length) return
  const remembered = prefs.get<any>(dirPrefKey(), null)
  // 兼容数字与字符串数字（后端基本类型存为字符串）
  let id: number | null = null
  if (typeof remembered === 'number') id = remembered
  else if (typeof remembered === 'string' && remembered !== '' && !Number.isNaN(Number(remembered))) id = Number(remembered)
  const validId = id !== null && findInTree(dirs, id)
  selectedDirectoryId.value = validId ? (id as number) : dirs[0].id
}

/** 递归查找目录 */
const findInTree = (dirs: any[], id: number | null): boolean => {
  for (const d of dirs) {
    if (d.id === id) return true
    if (d.children && findInTree(d.children, id)) return true
  }
  return false
}

onMounted(async () => {
  loading.value = true
  try {
    await loadDirectories()
    applyRememberedDir()
    if (selectedDirectoryId.value) await loadData()
  } finally { loading.value = false }
})

watch(() => props.activeModule, async () => {
  selectedDirectoryId.value = null
  await loadDirectories()
  applyRememberedDir()
  if (selectedDirectoryId.value) await loadData()
})

watch(directoryType, () => {
  // 模块切换时读取该模块的视图偏好（默认：便签极简 / 收藏卡片）
  viewMode.value = prefs.get(`view.${props.activeModule}`, props.activeModule === 'notes' ? 'list' : 'grid')
})

// 视图切换即持久化到后端偏好
watch(viewMode, (mode) => {
  if (mode) prefs.set(`view.${props.activeModule}`, mode)
})

const handleDirectorySelect = async (id: number) => {
  selectedDirectoryId.value = id
  prefs.set(dirPrefKey(), id)
  await loadData()
}

const loadData = async () => {
  if (!selectedDirectoryId.value) return
  loading.value = true
  try {
    if (props.activeModule === 'bookmarks') {
      await bookmarkStore.fetchBookmarks(selectedDirectoryId.value)
    } else if (props.activeModule === 'notes') {
      await noteStore.fetchNotes(selectedDirectoryId.value)
    } else {
      await cloudDiskStore.fetchFiles(selectedDirectoryId.value)
    }
  } finally { loading.value = false }
}

const handleRefresh = async () => {
  await loadDirectories()
  await loadData()
}

/** 拖拽排序：按新 id 顺序生成 sortList → 调后端 → 刷新 */
const handleSort = async (orderedIds: number[]) => {
  if (!orderedIds.length) return
  const sortList = orderedIds.map((id, idx) => ({ id, sortOrder: idx }))
  try {
    if (props.activeModule === 'bookmarks') {
      await bookmarkStore.updateSortOrder(sortList)
    } else if (props.activeModule === 'notes') {
      await noteStore.updateSortOrder(sortList)
    } else {
      await cloudDiskStore.updateSortOrder(sortList)
    }
    message.success('排序已保存')
    await loadData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '排序失败')
    await loadData() // 失败时恢复原顺序
  }
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
  editingBookmarkId.value = null
  createBookmarkForm.value = { title: '', url: '', directoryId: selectedDirectoryId.value }
  previewIcon.value = ''
  previewTitle.value = ''
  showCreateModal.value = true
}

const handleEditBookmark = (bookmark: any) => {
  editingBookmarkId.value = bookmark.id
  createBookmarkForm.value = {
    title: bookmark.title || '',
    url: bookmark.url || '',
    directoryId: bookmark.directoryId ?? selectedDirectoryId.value,
  }
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
    if (editingBookmarkId.value !== null) {
      await axios.put(`/api/bookmarks/${editingBookmarkId.value}`, {
        title: createBookmarkForm.value.title.trim(),
        url: createBookmarkForm.value.url.trim(),
        directoryId: createBookmarkForm.value.directoryId,
      })
      message.success('保存成功')
    } else {
      await axios.post('/api/bookmarks', {
        title: createBookmarkForm.value.title.trim(),
        url: createBookmarkForm.value.url.trim(),
        directoryId: createBookmarkForm.value.directoryId,
      })
      message.success('收藏成功')
    }
    showCreateModal.value = false
    await loadData()
  } catch (e: any) {
    message.error(e.response?.data?.message || '保存失败')
  } finally { creating.value = false }
}

// ========== 便签 ==========

/** 在新标签页打开便签页面（新建/编辑/预览） */
const openNoteInNewTab = (location: RouteLocationRaw) => {
  window.open(router.resolve(location).href, '_blank')
}

const handleCreateNote = () => {
  if (!selectedDirectoryId.value) {
    message.warning('请先选择一个目录')
    return
  }
  openNoteInNewTab({
    name: 'note-create',
    query: { directoryId: String(selectedDirectoryId.value) },
  })
}

const handleEditNote = (note: Note) => {
  openNoteInNewTab({ name: 'note-view', params: { id: note.id } })
}

const handlePreviewNote = (note: Note) => {
  openNoteInNewTab({ name: 'note-view', params: { id: note.id } })
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

// ========== 云盘 ==========

/** 触发 DiskView 的文件选择（顶栏/右下角上传按钮） */
const diskUploadTriggered = ref(0)
const handleFilesUpload = () => {
  if (!selectedDirectoryId.value) {
    message.warning('请先选择一个云盘目录')
    return
  }
  diskUploadTriggered.value++
}
const handleFilesRefresh = () => {
  loadData()
}

// ========== FAB 标签 ==========

const fabLabel = computed(() => props.activeModule === 'bookmarks' ? '添加收藏' : props.activeModule === 'notes' ? '新建便签' : '上传文件')

const handleHelp = () => {
  window.open('https://github.com/your-repo/jnclub', '_blank')
}

// ========== 空状态文案 ==========

const emptyMessage = computed(() => props.activeModule === 'bookmarks' ? '这个目录还没有收藏' : props.activeModule === 'notes' ? '这个目录还没有便签' : '这个目录还没有文件')
const emptyHint = computed(() => props.activeModule === 'bookmarks' ? '点击顶部按钮添加第一个收藏' : props.activeModule === 'notes' ? '点击右下角 + 创建你的第一篇便签' : '点击上传按钮上传第一个文件')
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
            {{ props.activeModule === 'bookmarks' ? '收藏夹' : props.activeModule === 'notes' ? '便签' : '云盘' }}
          </NBreadcrumbItem>
          <NBreadcrumbItem v-if="currentDirName !== '全部'" class="breadcrumb-current">
            {{ currentDirName }}
          </NBreadcrumbItem>
        </NBreadcrumb>
      </div>

      <div class="header-right">
        <ViewSwitcher v-if="props.activeModule !== 'files'" v-model="viewMode" />
        <NButton
          v-if="props.activeModule === 'bookmarks'"
          class="btn-new jnclub-bouncy-slow"
          @click="handleOpenCreate"
        >
          <template #icon><NIcon :component="Plus" /></template>
          新建
        </NButton>
        <NButton
          v-else-if="props.activeModule === 'notes'"
          class="btn-new jnclub-bouncy-slow"
          @click="handleCreateNote"
        >
          <template #icon><NIcon :component="Plus" /></template>
          新建便签
        </NButton>
        <NButton
          v-else
          class="btn-new jnclub-bouncy-slow"
          :disabled="!selectedDirectoryId"
          @click="handleFilesUpload"
        >
          <template #icon><NIcon :component="UploadCloud" /></template>
          上传文件
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
          <!-- 目录 chips（创建入口在左侧目录树） -->

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
            @refresh="loadData" @edit="handleEditBookmark" @sort="handleSort"
          />
          <!-- 收藏列表 -->
          <CollectionList
            v-else-if="props.activeModule === 'bookmarks' && viewMode === 'list' && bookmarkStore.bookmarks.length > 0"
            :bookmarks="bookmarkStore.bookmarks"
            @refresh="loadData" @edit="handleEditBookmark" @sort="handleSort"
          />
          <!-- 收藏空状态 -->
          <CollectionEmpty
            v-else-if="props.activeModule === 'bookmarks' && !loading"
            :message="emptyMessage"
            @create="handleOpenCreate"
          />

          <!-- 便签卡片网格 -->
          <NoteGrid
            v-else-if="props.activeModule === 'notes' && viewMode === 'grid' && noteStore.notes.length > 0"
            :notes="noteStore.notes" :loading="false"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData" @sort="handleSort"
          />
          <!-- 便签列表 -->
          <NoteList
            v-else-if="props.activeModule === 'notes' && viewMode === 'list' && noteStore.notes.length > 0"
            :notes="noteStore.notes" :loading="false"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData" @sort="handleSort"
          />
          <!-- 便签空状态 -->
          <NoteEmpty
            v-else-if="props.activeModule === 'notes' && !loading"
            :message="emptyMessage"
            :hint="emptyHint"
            @create="handleCreateNote"
          />

          <!-- 云盘 -->
          <DiskView
            v-else-if="props.activeModule === 'files'"
            :directory-id="selectedDirectoryId"
            :trigger="diskUploadTriggered"
            @refresh="handleFilesRefresh"
            @sort="handleSort"
          />
        </NSpin>
      </div>
    </div>

    <!-- 悬浮 FAB -->
    <FloatingActions
      :add-label="fabLabel"
      @add="props.activeModule === 'bookmarks' ? handleOpenCreate() : props.activeModule === 'notes' ? handleCreateNote() : handleFilesUpload()"
      @help="handleHelp"
    />

    <!-- 收藏创建/编辑弹窗 -->
    <NModal v-model:show="showCreateModal" preset="dialog" :title="editingBookmarkId !== null ? '编辑收藏' : '添加收藏'">
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
