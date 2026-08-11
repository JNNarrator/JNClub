<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick, onBeforeUnmount } from 'vue'
import {
  NButton, NIcon, NSpin, NBreadcrumb, NBreadcrumbItem,
  NModal, NForm, NFormItem, NInput, NSpace, NSelect, NAvatar,
  NDrawer, NDrawerContent,
  useMessage, useDialog,
} from 'naive-ui'
import { Plus, FolderOpen, Link, Globe, RefreshCw, UploadCloud, Tag, Search, KeyRound, Download } from 'lucide-vue-next'
import { useRouter, useRoute, type RouteLocationRaw } from 'vue-router'
import { useDirectoryStore } from '../stores/directory'
import { useBookmarkStore } from '../stores/bookmark'
import { useNoteStore } from '../stores/note'
import { useCloudDiskStore } from '../stores/clouddisk'
import { useVaultStore } from '../stores/vault'
import type { Note } from '../stores/note'
import FolderPanel from '../components/FolderPanel.vue'
import CollectionGrid from '../components/CollectionGrid.vue'
import CollectionList from '../components/CollectionList.vue'
import EmptyState from '../components/EmptyState.vue'
import NoteGrid from '../components/NoteGrid.vue'
import NoteList from '../components/NoteList.vue'
import DiskView from '../components/DiskView.vue'
import VaultView from '../components/VaultView.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import FloatingActions from '../components/FloatingActions.vue'
import TagPicker from '../components/TagPicker.vue'
import SearchDrawer from '../components/SearchDrawer.vue'
import type { ViewMode } from '../components/ViewSwitcher.vue'
import axios from 'axios'
import { useUserPreferences } from '../../../shared/composables/useUserPreferences'
import { fetchTags, setRefTags, type TagItem } from '../composables/tags'
import { exportMarkdown, downloadFile } from '../composables/markdownIO'

const router = useRouter()
const route = useRoute()
const prefs = useUserPreferences()
const directoryStore = useDirectoryStore()
const bookmarkStore = useBookmarkStore()
const noteStore = useNoteStore()
const cloudDiskStore = useCloudDiskStore()
const vaultStore = useVaultStore()
const message = useMessage()
const dialog = useDialog()

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes' | 'files' | 'vault'
  isDark: boolean
}>()

const emit = defineEmits<{
  'module-change': [module: 'bookmarks' | 'notes' | 'files' | 'vault']
}>()

const directoryType = computed(() => props.activeModule === 'bookmarks' ? 1 : props.activeModule === 'notes' ? 2 : props.activeModule === 'files' ? 3 : 5)

// ========== 全局搜索 ==========

const showSearch = ref(false)
/** 搜索结果跳转：切模块后待选中的目录 */
const pendingDirId = ref<number | null>(null)

const onSearchKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    showSearch.value = true
  }
}
onMounted(() => window.addEventListener('keydown', onSearchKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onSearchKeydown))

const handleSearchJump = (module: 'bookmarks' | 'notes' | 'files', directoryId: number | null) => {
  pendingDirId.value = directoryId
  emit('module-change', module)
}

const selectedDirectoryId = ref<number | null>(null)
const loading = ref(false)
/** 视图模式：按模块记忆（后端偏好），便签默认极简 list、收藏夹默认卡片 grid */
const viewMode = ref<ViewMode>(prefs.get(`view.${props.activeModule}`, props.activeModule === 'notes' ? 'list' : 'grid'))

// ========== 移动端：目录抽屉 ==========

/** 移动端目录抽屉开关（<768px 时替代左侧目录树） */
const showDirDrawer = ref(false)
const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) showDirDrawer.value = false
}
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onBeforeUnmount(() => window.removeEventListener('resize', checkMobile))

/** 抽屉内选中目录：复用桌面端逻辑 + 自动收起抽屉 */
const handleDrawerSelect = async (id: number) => {
  showDirDrawer.value = false
  await handleDirectorySelect(id)
}

// 目录创建

// 收藏创建/编辑表单
const showCreateModal = ref(false)
const creating = ref(false)
const editingBookmarkId = ref<number | null>(null)
const createBookmarkForm = ref({ title: '', url: '', directoryId: null as number | null })
const editTagPickerRef = ref<InstanceType<typeof TagPicker> | null>(null)
const createTagPickerRef = ref<InstanceType<typeof TagPicker> | null>(null)

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

// ========== 标签筛选 ==========

/** 当前模块可用标签（bookmark/note） */
const availableTags = ref<TagItem[]>([])
/** 当前选中标签 id（null=全部） */
const activeTagId = ref<number | null>(null)
/** TagPicker 保存触发计数 */
const tagSaveTrigger = ref(0)

const loadTags = async () => {
  if (props.activeModule === 'files' || props.activeModule === 'vault') {
    availableTags.value = []
    return
  }
  const type = props.activeModule === 'bookmarks' ? 'bookmark' : 'note'
  availableTags.value = await fetchTags(type)
}

// ========== 目录加载 ==========

const loadDirectories = async () => {
  await directoryStore.fetchDirectories(directoryType.value)
}

/** 选中目录按模块记忆：key dir.bookmarks / dir.notes */
const dirPrefKey = () => `dir.${props.activeModule}`

/** 目录加载后恢复选中目录：URL query.dir 优先（刷新定位），无效则回退偏好，再回退第一个 */
const applyRememberedDir = () => {
  const dirs = directoryStore.directories
  if (!dirs.length) return
  // 1) URL query.dir（刷新直接定位）
  const urlDir = route.query.dir
  if (urlDir && findInTree(dirs, Number(urlDir))) {
    selectedDirectoryId.value = Number(urlDir)
    return
  }
  // 2) 偏好记忆（向后兼容）
  const remembered = prefs.get<any>(dirPrefKey(), null)
  let id: number | null = null
  if (typeof remembered === 'number') id = remembered
  else if (typeof remembered === 'string' && remembered !== '' && !Number.isNaN(Number(remembered))) id = Number(remembered)
  const validId = id !== null && findInTree(dirs, id)
  selectedDirectoryId.value = validId ? (id as number) : dirs[0].id
  // 把恢复的目录同步到 URL，保证刷新可直接定位
  if (selectedDirectoryId.value) {
    router.replace({ query: { ...route.query, module: props.activeModule, dir: selectedDirectoryId.value } })
  }
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
    await loadTags()
    applyRememberedDir()
    if (selectedDirectoryId.value) await loadData()
  } finally { loading.value = false }
})

watch(() => props.activeModule, async () => {
  selectedDirectoryId.value = null
  activeTagId.value = null
  // 模块切换时清理旧目录并用当前模块重建 query，避免旧 module 覆盖新模块
  router.replace({ query: { ...route.query, module: props.activeModule, dir: undefined } })
  await loadDirectories()
  await loadTags()
  // 搜索跳转：优先选中目标目录；否则恢复记忆
  if (pendingDirId.value != null && findInTree(directoryStore.directories, pendingDirId.value)) {
    selectedDirectoryId.value = pendingDirId.value
    pendingDirId.value = null
  } else {
    pendingDirId.value = null
    applyRememberedDir()
  }
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

/** 监听 URL dir 变化（浏览器前进/后退/手动改 URL）→ 同步选中目录 */
watch(() => route.query.dir, async (dir) => {
  if (dir == null) return // 模块切换时主动清理，交给模块 watch 处理
  const id = Number(dir)
  if (Number.isNaN(id) || id === selectedDirectoryId.value) return
  if (selectedDirectoryId.value !== null && findInTree(directoryStore.directories, id)) {
    selectedDirectoryId.value = id
    prefs.set(dirPrefKey(), id)
    await loadData()
  }
})

const handleDirectorySelect = async (id: number) => {
  selectedDirectoryId.value = id
  prefs.set(dirPrefKey(), id)
  router.replace({ query: { ...route.query, module: props.activeModule, dir: id } }) // 目录同步到 URL，刷新可定位
  await loadData()
}

const loadData = async () => {
  if (!selectedDirectoryId.value) return
  loading.value = true
  try {
    if (props.activeModule === 'bookmarks') {
      await bookmarkStore.fetchBookmarks(selectedDirectoryId.value, activeTagId.value)
    } else if (props.activeModule === 'notes') {
      await noteStore.fetchNotes(selectedDirectoryId.value, activeTagId.value)
    } else if (props.activeModule === 'files') {
      await cloudDiskStore.fetchFiles(selectedDirectoryId.value)
    } else {
      await vaultStore.fetchItems(selectedDirectoryId.value)
    }
  } finally { loading.value = false }
}

const handleTagFilter = (tagId: number | null) => {
  activeTagId.value = tagId
  loadData()
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
    } else if (props.activeModule === 'files') {
      await cloudDiskStore.updateSortOrder(sortList)
    } else {
      await vaultStore.updateSortOrder(sortList)
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
      const created = await axios.post('/api/bookmarks', {
        title: createBookmarkForm.value.title.trim(),
        url: createBookmarkForm.value.url.trim(),
        directoryId: createBookmarkForm.value.directoryId,
      })
      message.success('收藏成功')
      // 创建态标签持久化（refId 为空时由 TagPicker 收集选中名，创建成功后按新 id 写入）
      if (created.data?.code === 200 && created.data?.data?.id) {
        const names = createTagPickerRef.value?.getSelectedNames() ?? []
        if (names.length) {
          await setRefTags('bookmark', created.data.data.id, names)
          await loadTags()
        }
      }
    }
    // 编辑态保存标签
    if (editingBookmarkId.value !== null) {
      tagSaveTrigger.value++
      await nextTick()
      editTagPickerRef.value?.save()
      await loadTags()
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

/** 导出当前目录全部便签为 .md（逐个下载，图片内嵌 base64） */
const handleExportAllNotes = async () => {
  const notes = noteStore.notes
  if (!notes.length) { message.warning('当前目录没有便签'); return }
  const loadingMsg = message.loading(`正在导出 ${notes.length} 篇便签…`, { duration: 0 })
  let ok = 0
  for (const n of notes) {
    try {
      const md = await exportMarkdown(n.content || '')
      downloadFile(`${(n.title || '未命名').replace(/[\\/:*?"<>|]/g, '_')}.md`, md, 'text/markdown')
      ok++
    } catch {
      /* 单篇失败跳过，继续导出其余 */
    }
  }
  loadingMsg.destroy()
  message.success(ok === notes.length ? `已导出 ${ok} 篇便签` : `已导出 ${ok}/${notes.length} 篇（部分失败）`)
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

/** 密码库新建（由 VaultView 内部弹窗承接） */
const vaultViewRef = ref<InstanceType<typeof VaultView> | null>(null)
const handleCreateVault = () => {
  if (!selectedDirectoryId.value) {
    message.warning('请先选择密码库目录')
    return
  }
  vaultViewRef.value?.openCreate()
}

// ========== FAB 标签 ==========

const fabLabel = computed(() => props.activeModule === 'bookmarks' ? '添加收藏' : props.activeModule === 'notes' ? '新建便签' : props.activeModule === 'files' ? '上传文件' : '新建密码')

// ========== 空状态文案 ==========

const emptyMessage = computed(() => props.activeModule === 'bookmarks' ? '这个目录还没有收藏' : props.activeModule === 'notes' ? '这个目录还没有便签' : props.activeModule === 'files' ? '这个目录还没有文件' : '这个目录还没有密码条目')
const emptyHint = computed(() => props.activeModule === 'bookmarks' ? '点击顶部按钮添加第一个收藏' : props.activeModule === 'notes' ? '点击右下角 + 创建你的第一篇便签' : props.activeModule === 'files' ? '点击上传按钮上传第一个文件' : '点击右下角 + 添加第一条密码')
</script>

<template>
  <div class="home">
    <!-- 点阵背景 -->
    <div class="ambient-texture"></div>

    <!-- 毛玻璃顶栏 -->
    <header class="home-header glass-header">
      <div class="header-left">
        <!-- 移动端：目录抽屉入口 -->
        <NButton
          v-if="isMobile"
          quaternary circle size="small"
          class="mobile-dir-btn jnclub-bouncy"
          title="目录"
          @click="showDirDrawer = true"
        >
          <template #icon><NIcon :component="FolderOpen" size="18" /></template>
        </NButton>
        <NBreadcrumb class="jnclub-breadcrumb">
          <NBreadcrumbItem @click="selectedDirectoryId = null">JNClub</NBreadcrumbItem>
          <NBreadcrumbItem @click="selectedDirectoryId = null">
            {{ props.activeModule === 'bookmarks' ? '收藏夹' : props.activeModule === 'notes' ? '便签' : props.activeModule === 'files' ? '云盘' : '密码库' }}
          </NBreadcrumbItem>
          <NBreadcrumbItem v-if="currentDirName !== '全部'" class="breadcrumb-current">
            {{ currentDirName }}
          </NBreadcrumbItem>
        </NBreadcrumb>
      </div>

      <div class="header-right">
        <NButton quaternary circle size="small" class="refresh-btn" @click="showSearch = true" title="搜索 (Ctrl/⌘+K)">
          <template #icon><NIcon :component="Search" size="16" /></template>
        </NButton>
        <ViewSwitcher v-if="props.activeModule === 'bookmarks' || props.activeModule === 'notes'" v-model="viewMode" />
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
          v-if="props.activeModule === 'notes'"
          size="small"
          class="io-export-btn jnclub-bouncy"
          :disabled="!noteStore.notes.length"
          title="导出当前目录全部便签为 .md（图片内嵌 base64）"
          @click="handleExportAllNotes"
        >
          <template #icon><NIcon :component="Download" size="15" /></template>
          导出全部
        </NButton>
        <NButton
          v-else-if="props.activeModule === 'files'"
          class="btn-new jnclub-bouncy-slow"
          :disabled="!selectedDirectoryId"
          @click="handleFilesUpload"
        >
          <template #icon><NIcon :component="UploadCloud" /></template>
          上传文件
        </NButton>
        <NButton
          v-else
          class="btn-new jnclub-bouncy-slow"
          :disabled="!selectedDirectoryId"
          @click="handleCreateVault"
        >
          <template #icon><NIcon :component="KeyRound" /></template>
          新建密码
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

        <!-- 标签筛选条（收藏/便签模块） -->
        <div v-if="props.activeModule !== 'files' && availableTags.length" class="tag-bar">
          <button
            :class="['tag-chip', 'jnclub-bouncy', { 'tag-chip-active': activeTagId === null }]"
            @click="handleTagFilter(null)"
          >全部</button>
          <button
            v-for="t in availableTags"
            :key="t.id"
            :class="['tag-chip', 'jnclub-bouncy', { 'tag-chip-active': activeTagId === t.id }]"
            @click="handleTagFilter(t.id)"
          >
            <NIcon :component="Tag" size="13" />
            {{ t.name }}
            <span v-if="t.count" class="tag-count">{{ t.count }}</span>
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
          <EmptyState
            v-else-if="props.activeModule === 'bookmarks' && !loading"
            icon="bookmark"
            :message="emptyMessage"
            :hint="emptyHint"
            cta-label="添加收藏"
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
          <EmptyState
            v-else-if="props.activeModule === 'notes' && !loading"
            icon="note"
            :message="emptyMessage"
            :hint="emptyHint"
            cta-label="写第一篇便签"
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

          <!-- 密码库 -->
          <VaultView
            v-else-if="props.activeModule === 'vault'"
            ref="vaultViewRef"
            :directory-id="selectedDirectoryId"
            @refresh="loadData"
            @sort="handleSort"
          />
        </NSpin>
      </div>
    </div>

    <!-- 悬浮 FAB -->
    <FloatingActions
      :add-label="fabLabel"
      @add="props.activeModule === 'bookmarks' ? handleOpenCreate() : props.activeModule === 'notes' ? handleCreateNote() : props.activeModule === 'files' ? handleFilesUpload() : handleCreateVault()"
    />

    <!-- 全局搜索抽屉 -->
    <SearchDrawer :show="showSearch" @close="showSearch = false" @jump="handleSearchJump" />

    <!-- 移动端目录抽屉（<768px）：复用 FolderPanel，选中后自动收起） -->
    <NDrawer
      v-model:show="showDirDrawer"
      :width="280"
      placement="left"
      :mask-closable="true"
      class="mobile-dir-drawer"
    >
      <NDrawerContent :native-scrollbar="false">
        <template #header>
          <span class="drawer-title">
            <NIcon :component="FolderOpen" size="16" style="margin-right: 6px; vertical-align: -2px;" />
            目录
          </span>
        </template>
        <FolderPanel
          :directories="directoryStore.directories"
          :selected-id="selectedDirectoryId"
          :type="directoryType"
          @select="handleDrawerSelect"
          @refresh="handleRefresh"
        />
      </NDrawerContent>
    </NDrawer>

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
        <NFormItem v-if="editingBookmarkId !== null" label="标签" path="tags">
          <TagPicker ref="editTagPickerRef" ref-type="bookmark" :ref-id="editingBookmarkId" :save-trigger="tagSaveTrigger" />
        </NFormItem>
        <NFormItem v-else label="标签" path="tags">
          <TagPicker ref="createTagPickerRef" ref-type="bookmark" :ref-id="null" />
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

/* 导出全部（玻璃小按钮） */
.io-export-btn {
  border-radius: var(--radius-pill) !important;
  background: var(--glass-bg-trans) !important;
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border) !important;
  color: var(--text-2) !important;
}
.io-export-btn:hover {
  color: var(--brand) !important;
  border-color: var(--brand) !important;
}
.io-export-btn[disabled] {
  opacity: 0.45;
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
  background:
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 12px;
  box-shadow: var(--glass-shadow);
}

.collection-column {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 18px 20px;
  box-shadow: var(--glass-shadow);
}

/* === Chip 标签栏 === */
.chip-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
}

/* === 标签筛选条 === */
.tag-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 8px 12px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
}
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: none;
  background: transparent;
  border-radius: var(--radius-pill);
  cursor: pointer;
  font-size: 12px;
  color: var(--text-2);
}
.tag-chip:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.tag-chip-active {
  background: var(--brand-soft) !important;
  color: var(--brand) !important;
  font-weight: 600;
}
.tag-count {
  font-size: 11px;
  opacity: 0.7;
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
  background: var(--glass-chip-bg);
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

/* 移动端目录抽屉 */
.mobile-dir-drawer :deep(.n-drawer-body-content-wrapper) {
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}
.drawer-title {
  display: inline-flex;
  align-items: center;
  font-weight: 600;
  color: var(--text-1);
}

/* === 移动端适配（<768px） === */
@media (max-width: 767px) {
  .home-header {
    padding: 0 12px;
    gap: 8px;
    height: 52px;
  }
  /* 侧栏目录树在窄屏隐藏，靠顶栏目录按钮唤起抽屉 */
  .folder-column {
    display: none;
  }
  .content-area {
    padding: 12px;
    gap: 12px;
  }
  .header-right {
    gap: 6px;
  }
  /* 顶栏按钮加大触控目标（≥44px），新建按钮窄屏只留图标（隐藏文字） */
  .header-right :deep(.n-button) {
    min-width: 40px;
    height: 40px;
  }
  .header-right :deep(.n-button) span {
    display: none;
  }
  .mobile-dir-btn {
    margin-right: 2px;
    color: var(--text-2);
  }
  .mobile-dir-btn:hover {
    color: var(--text-1);
    background: var(--hover-bg);
  }
  /* 面包屑收窄 */
  .jnclub-breadcrumb :deep(.n-breadcrumb-item__link) {
    font-size: 12px;
    max-width: 90px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .chip-bar {
    margin-bottom: 12px;
  }
  /* 目录 chips 可横滑，避免换行堆叠 */
  .chip-bar {
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .chip-bar::-webkit-scrollbar {
    display: none;
  }
  /* 收藏卡片网格：窄屏单列 */
  .collection-grid {
    grid-template-columns: 1fr !important;
  }
  /* 底部留白，避免被移动端 TabBar 遮挡 */
  .collection-column {
    padding-bottom: 12px;
  }
}
</style>
