<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useBreakpoint } from '../../../shared/composables/useBreakpoint'
import {
  NIcon,
  useMessage,
} from 'naive-ui'
import { FolderOpen, Tag } from 'lucide-vue-next'
import { useRouter, useRoute } from 'vue-router'
import { useDirectoryStore } from '../stores/directory'
import { useBookmarkStore } from '../stores/bookmark'
import { useNoteStore } from '../stores/note'
import { useCloudDiskStore } from '../stores/clouddisk'
import { useVaultStore } from '../stores/vault'
import FolderPanel from '../components/FolderPanel.vue'
import CollectionGrid from '../components/CollectionGrid.vue'
import CollectionList from '../components/CollectionList.vue'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import NoteGrid from '../components/NoteGrid.vue'
import NoteList from '../components/NoteList.vue'
import DiskView from '../components/DiskView.vue'
import VaultView from '../components/VaultView.vue'
import DeadLinkModal from '../components/DeadLinkModal.vue'
import ReadingModal from '../components/ReadingModal.vue'
import ModuleToolbar from '../components/ModuleToolbar.vue'
import BookmarkFormModal from '../components/BookmarkFormModal.vue'
import BatchActionModals from '../components/BatchActionModals.vue'
import DirectoryDrawer from '../components/DirectoryDrawer.vue'
import SearchDrawer from '../components/SearchDrawer.vue'
import JSkeletonGrid from '../../../shared/components/ui/JSkeletonGrid.vue'
import JSkeletonList from '../../../shared/components/ui/JSkeletonList.vue'
import JFilterBar from '../../../shared/components/ui/JFilterBar.vue'
import JBatchBar from '../../../shared/components/ui/JBatchBar.vue'
import ContextMenuHost from '../../../shared/components/ContextMenuHost.vue'
import AppHeader from '../../../shared/layout/AppHeader.vue'
import type { ViewMode } from '../../../shared/components/ViewSwitcher.vue'
import type { ModuleKey } from '../../../shared/types/nav'
import { useUserPreferences } from '../../../shared/composables/useUserPreferences'
import { fetchTags, type TagItem } from '../composables/tags'
import { useBatchActions } from '../composables/useBatchActions'
import { useNoteActions } from '../composables/useNoteActions'
import { useSearchActions } from '../composables/useSearchActions'

const router = useRouter()
const route = useRoute()
const prefs = useUserPreferences()
const directoryStore = useDirectoryStore()
const bookmarkStore = useBookmarkStore()
const noteStore = useNoteStore()
const cloudDiskStore = useCloudDiskStore()
const vaultStore = useVaultStore()
const message = useMessage()

const props = defineProps<{
  activeModule: ModuleKey
  isDark: boolean
}>()

const emit = defineEmits<{
  'module-change': [module: ModuleKey]
  'toggle-theme': []
}>()

const directoryType = computed(() => props.activeModule === 'bookmarks' ? 1 : props.activeModule === 'notes' ? 2 : props.activeModule === 'files' ? 3 : 5)

// ========== 全局搜索与命令面板 ==========
const search = useSearchActions({
  onModuleChange: (m) => emit('module-change', m),
  onToggleTheme: () => emit('toggle-theme'),
})
const { showSearch, pendingDirId, handleSearchJump, handleCommand } = search

// ========== 批量操作（收藏 / 便签） ==========
const batch = useBatchActions({
  activeModule: () => props.activeModule,
  currentList: () => props.activeModule === 'notes' ? noteStore.notes : bookmarkStore.bookmarks,
  loadData: () => loadData(),
  loadTags: () => loadTags(),
})
const {
  batchMode,
  selectedIds,
  allSelected,
  toggleBatchMode,
  toggleSelect,
  toggleAll,
  handleBatchDelete,
} = batch

const batchActionRef = ref<InstanceType<typeof BatchActionModals> | null>(null)
const openBatchMove = () => batchActionRef.value?.openMove()
const openBatchTags = () => batchActionRef.value?.openTags()

const selectedDirectoryId = ref<number | null>(null)
const loading = ref(false)
const loadError = ref(false)

const noteActions = useNoteActions({
  selectedDirectoryId: () => selectedDirectoryId.value,
  loadData: () => loadData(),
})
const { handleCreateNote, handleExportAllNotes, handleEditNote, handlePreviewNote, handleDeleteNote } = noteActions
/** 视图模式：按模块记忆（后端偏好），便签默认极简 list、收藏夹默认卡片 grid */
const viewMode = ref<ViewMode>(prefs.get(`view.${props.activeModule}`, props.activeModule === 'notes' ? 'list' : 'grid'))

// ========== 移动端：目录抽屉 ==========

/** 移动端目录抽屉开关（<768px 时替代左侧目录树） */
const showDirDrawer = ref(false)
const { isMobile } = useBreakpoint()

// 切回桌面时关闭移动端目录抽屉
watch(isMobile, (m) => {
  if (!m) showDirDrawer.value = false
})

// 收藏创建/编辑表单
const showCreateModal = ref(false)
const editingBookmark = ref<{ id: number; title: string; url: string; directoryId: number | null } | null>(null)

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
/** 便签归档视图开关 */
const notesArchived = ref(false)
/** 收藏失效检测弹窗 */
const showDeadLink = ref(false)

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

/**
 * 是否处于「首屏加载」：当前模块还没有任何数据。
 *
 * 这个区分是 M2-4 的核心 —— 之前只要 loading 为真就整块换成骨架屏，
 * 于是点「刷新」、排序、增删后的重新拉取都会把已有内容闪成骨架，
 * 视觉上像数据丢了。现在：首屏（无数据）用骨架，增量刷新（有数据）
 * 交给列表组件自己的 NSpin 遮罩，内容始终在。
 *
 * files / vault 的数据在各自组件内部，Home 的 loading 对它们本就是
 * 「首次进入该模块」语义，无需再分。
 */
const isFirstScreenLoad = computed(() => {
  const m = props.activeModule
  if (m === 'bookmarks') return loading.value && bookmarkStore.bookmarks.length === 0
  if (m === 'notes') return loading.value && noteStore.notes.length === 0
  if (m === 'files' || m === 'vault') return loading.value
  return false
})

/** 内容切换动画 key：模块 + 视图 + 空态 任一变化触发 out-in 过渡 */
const contentKey = computed(() => {
  const m = props.activeModule
  const isEmpty = m === 'bookmarks' ? bookmarkStore.bookmarks.length === 0
    : m === 'notes' ? noteStore.notes.length === 0
    : false
  return `${m}-${viewMode.value}-${isEmpty ? 'empty' : 'data'}`
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

/** 顶栏面包屑点击 JNClub/模块名：回到根目录并退出多选 */
const handleBreadcrumbRoot = () => {
  selectedDirectoryId.value = null
  toggleBatchMode(false)
}

const handleDirectorySelect = async (id: number) => {
  selectedDirectoryId.value = id
  prefs.set(dirPrefKey(), id)
  router.replace({ query: { ...route.query, module: props.activeModule, dir: id } }) // 目录同步到 URL，刷新可定位
  await loadData()
}

const loadData = async () => {
  if (!selectedDirectoryId.value && !notesArchived.value) return
  loading.value = true
  loadError.value = false
  try {
    if (props.activeModule === 'bookmarks') {
      await bookmarkStore.fetchBookmarks(selectedDirectoryId.value!, activeTagId.value)
    } else if (props.activeModule === 'notes') {
      await noteStore.fetchNotes(selectedDirectoryId.value ?? 0, activeTagId.value, notesArchived.value)
    } else if (props.activeModule === 'files') {
      await cloudDiskStore.fetchFiles(selectedDirectoryId.value!)
    } else {
      await vaultStore.fetchItems(selectedDirectoryId.value!)
    }
  } catch {
    loadError.value = true
  } finally { loading.value = false }
}

const handleTagFilter = (tagId: string | number | null) => {
  activeTagId.value = tagId === null ? null : Number(tagId)
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

// ========== 收藏创建/编辑 ==========

const handleOpenCreate = () => {
  editingBookmark.value = null
  showCreateModal.value = true
}

const handleEditBookmark = (bookmark: any) => {
  editingBookmark.value = {
    id: bookmark.id,
    title: bookmark.title || '',
    url: bookmark.url || '',
    directoryId: bookmark.directoryId ?? selectedDirectoryId.value,
  }
  showCreateModal.value = true
}

const handleBookmarkSaved = () => {
  loadData()
  loadTags()
}

/** 收藏阅读模式 */
const readingUrl = ref('')
const readingId = ref<number | null>(null)
const showReading = ref(false)
const handleReadBookmark = (bookmark: any) => {
  readingUrl.value = bookmark?.url || ''
  readingId.value = bookmark?.id ?? null
  showReading.value = true
}

// ========== 便签 ==========
// 便签动作（新建/导出/编辑/预览/删除）由 useNoteActions 统一管理

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

// ========== 空状态文案 ==========

const emptyMessage = computed(() => props.activeModule === 'bookmarks' ? '这个目录还没有收藏' : props.activeModule === 'notes' ? '这个目录还没有便签' : props.activeModule === 'files' ? '这个目录还没有文件' : '这个目录还没有密码条目')
const emptyHint = computed(() => props.activeModule === 'bookmarks' ? '点击顶部按钮添加第一个收藏' : props.activeModule === 'notes' ? '点击右下角 + 创建你的第一篇便签' : props.activeModule === 'files' ? '点击上传按钮上传第一个文件' : '点击右下角 + 添加第一条密码')
</script>

<template>
  <div class="home">
    <!-- 点阵背景 -->
    <div class="ambient-texture"></div>

    <!-- 毛玻璃顶栏 -->
    <AppHeader
      :is-dark="props.isDark"
      :active-module="props.activeModule"
      :is-mobile="isMobile"
      :batch-mode="batchMode"
      :view-mode="viewMode"
      :breadcrumb-current="currentDirName"
      @toggle-theme="emit('toggle-theme')"
      @open-search="showSearch = true"
      @update:batch-mode="toggleBatchMode"
      @update:view-mode="(v: ViewMode) => viewMode = v"
      @refresh="handleRefresh"
      @open-dir-drawer="showDirDrawer = true"
      @breadcrumb-root="handleBreadcrumbRoot"
    />

    <!-- 主体 -->
    <div class="content-area">
      <!-- 左侧目录树 -->
      <aside class="folder-column">
        <div class="folder-panel-title">
          <NIcon :component="FolderOpen" size="15" />
          目录
        </div>
        <FolderPanel
          :directories="directoryStore.directories"
          :selected-id="selectedDirectoryId"
          :type="directoryType"
          @select="handleDirectorySelect"
          @refresh="handleRefresh"
        />
      </aside>

      <div class="collection-column">
        <!-- 模块内部工具栏（模块特定操作归这里，顶栏只留通用能力） -->
        <ModuleToolbar
          :active-module="props.activeModule"
          :bookmark-count="bookmarkStore.bookmarks.length"
          :note-count="noteStore.notes.length"
          :notes-archived="notesArchived"
          :can-upload="!!selectedDirectoryId"
          @create-bookmark="handleOpenCreate"
          @check-dead="showDeadLink = true"
          @create-note="handleCreateNote"
          @export-notes="handleExportAllNotes"
          @toggle-archived="notesArchived = !notesArchived; loadData()"
          @upload-file="handleFilesUpload"
        />

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
        <JFilterBar
          v-if="props.activeModule !== 'files' && availableTags.length"
          class="tag-bar"
          :items="availableTags.map(t => ({ label: t.name, value: t.id, count: t.count, icon: Tag }))"
          :model-value="activeTagId"
          all-label="全部"
          @select="handleTagFilter"
        />

        <div class="spin-area">
          <JSkeletonGrid
            v-if="isFirstScreenLoad && (props.activeModule === 'bookmarks' || props.activeModule === 'notes') && viewMode === 'grid'"
          />
          <JSkeletonList
            v-else-if="isFirstScreenLoad && ((props.activeModule === 'bookmarks' || props.activeModule === 'notes') && viewMode === 'list' || props.activeModule === 'files' || props.activeModule === 'vault')"
          />
          <!-- 错误态走 JEmptyState 的 variant="error"：它自带 error 图标与 ghost 重试按钮。
               此前这里用默认 empty 变体凑了个「重试」，结果是错误态显示书签图标 +
               带「+」号的品牌主按钮，语义与视觉都对不上（P2-3）。 -->
          <JEmptyState
            v-else-if="loadError"
            variant="error"
            message="加载失败"
            hint="请检查网络后重试"
            retry-label="重试"
            @retry="handleRefresh"
          />
          <Transition v-else name="module-fade" mode="out-in">
            <div :key="contentKey" class="module-content">
          <!-- 收藏卡片网格 -->
          <CollectionGrid
            v-if="props.activeModule === 'bookmarks' && viewMode === 'grid' && bookmarkStore.bookmarks.length > 0"
            :bookmarks="bookmarkStore.bookmarks" :loading="loading"
            :batch-mode="batchMode" :selected-ids="selectedIds" @toggle-select="toggleSelect"
            @refresh="loadData" @edit="handleEditBookmark" @read="handleReadBookmark" @sort="handleSort"
          />
          <!-- 收藏列表 -->
          <CollectionList
            v-else-if="props.activeModule === 'bookmarks' && viewMode === 'list' && bookmarkStore.bookmarks.length > 0"
            :bookmarks="bookmarkStore.bookmarks" :loading="loading"
            :batch-mode="batchMode" :selected-ids="selectedIds" @toggle-select="toggleSelect"
            @refresh="loadData" @edit="handleEditBookmark" @read="handleReadBookmark" @sort="handleSort"
          />
          <!-- 收藏空状态 -->
          <JEmptyState
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
            :notes="noteStore.notes" :loading="loading"
            :batch-mode="batchMode" :selected-ids="selectedIds" @toggle-select="toggleSelect"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData" @sort="handleSort"
          />
          <!-- 便签列表 -->
          <NoteList
            v-else-if="props.activeModule === 'notes' && viewMode === 'list' && noteStore.notes.length > 0"
            :notes="noteStore.notes" :loading="loading"
            :batch-mode="batchMode" :selected-ids="selectedIds" @toggle-select="toggleSelect"
            @preview="handlePreviewNote" @edit="handleEditNote" @delete="handleDeleteNote"
            @refresh="loadData" @sort="handleSort"
          />
          <!-- 便签空状态 -->
          <JEmptyState
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
            :directory-id="selectedDirectoryId"
            @refresh="loadData"
            @sort="handleSort"
          />
            </div>
          </Transition>
        </div>

        <!-- 批量操作条（收藏/便签） -->
        <JBatchBar
          v-if="batchMode && selectedIds.length"
          :selected-count="selectedIds.length"
          :all-selected="allSelected"
          :show-tag="props.activeModule === 'bookmarks'"
          @toggle-all="toggleAll"
          @tag="openBatchTags"
          @move="openBatchMove"
          @delete="handleBatchDelete"
          @cancel="toggleBatchMode(false)"
        />
      </div>
    </div>

    <!-- 全局搜索抽屉 -->
    <SearchDrawer :show="showSearch" @close="showSearch = false" @jump="handleSearchJump" @action="handleCommand" />

    <!-- 批量移动/打标签弹窗 -->
    <BatchActionModals
      ref="batchActionRef"
      :active-module="props.activeModule === 'notes' ? 'notes' : 'bookmarks'"
      :selected-ids="selectedIds"
      @done="batch.finishBatch"
    />

    <!-- 移动端目录抽屉（<768px）：复用 FolderPanel，选中后自动收起） -->
    <DirectoryDrawer
      :show="showDirDrawer"
      :directories="directoryStore.directories"
      :selected-id="selectedDirectoryId"
      :type="directoryType"
      @update:show="(v: boolean) => showDirDrawer = v"
      @select="handleDirectorySelect"
      @refresh="handleRefresh"
    />

    <!-- 收藏创建/编辑弹窗 -->
    <BookmarkFormModal
      v-model:show="showCreateModal"
      :editing-bookmark="editingBookmark"
      :default-directory-id="selectedDirectoryId"
      :directory-options="directoryOptions"
      @saved="handleBookmarkSaved"
    />

    <!-- 全局右键菜单宿主 -->
    <ContextMenuHost />

    <!-- 收藏失效检测弹窗 -->
    <DeadLinkModal v-model:show="showDeadLink" @cleaned="handleRefresh" />
    <ReadingModal v-model:show="showReading" :url="readingUrl" :bookmark-id="readingId" />
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

/* === 主体 === */
.content-area {
  flex: 1;
  display: flex;
  gap: 20px;
  min-height: 0;
  width: 100%;
  max-width: var(--layout-content-max, 1560px);
  margin: 0 auto;
  padding: 20px var(--layout-page-gutter, 24px);
  /* 品牌色氛围光晕（背景层次第 0 层） */
  background:
    radial-gradient(700px 280px at 4% 0%, var(--glass-glow-top), transparent 55%),
    radial-gradient(700px 280px at 96% 100%, var(--glass-glow-bottom), transparent 55%);
}

/* 目录面板标题（与移动端抽屉标题呼应） */
.folder-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 12px;
  flex-shrink: 0;
}

.folder-column {
  display: flex;
  flex-direction: column;
  /* 目录栏宽度走令牌（P2-6），与侧栏 --layout-sider-width 同属「布局宽度」体系 */
  flex: 0 0 var(--layout-folder-width, 220px);
  min-height: 0;
  overflow: hidden;
  background: var(--glass-bg-trans);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 14px 12px;
  box-shadow: var(--shadow-1);
  /* 隔离渲染边界，避免 backdrop-filter 影响兄弟元素合成 */
  contain: paint layout style;
}

/* 面板固定于视口、列表在面板内部滚动。滚动条留在面板边框内侧，
   与概览/回收站的 .j-page-body（JPageShell）一致 —— 故这里保留 overflow-y: auto。 */
.collection-column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background: var(--glass-bg-trans);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: var(--layout-panel-padding);
  box-shadow: var(--shadow-1);
  contain: paint layout style;
}

/* 面板内的固定 chrome 不参与 flex 压缩。
   .collection-column 改为 flex 列后，子项默认 flex-shrink:1，窄屏高度不足时
   工具栏/目录条/标签条会被压扁（实测 390px 下 .chip-bar 被压到 1.6px，目录名不可见），
   故显式钉住；只有 .spin-area 允许让出空间（它是 flex:1 的滚动区）。 */
.collection-column > .module-toolbar,
.collection-column > .chip-bar,
.collection-column > .tag-bar {
  flex-shrink: 0;
}

/* === Chip 标签栏 === */
/* 桌面端（≥768px）隐藏：左侧 .folder-column 的目录树已完整呈现同一份顶级目录
   （两者同源：chip-bar 渲染 topLevelDirs = directories.filter(d => !d.parentId)，
   目录树首层即同一集合），同屏出现两遍是纯冗余。
   移动端 .folder-column 为 display:none，chip-bar 是选目录的唯一入口，故仅在移动端显示。 */
.chip-bar {
  display: none;
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
  font-size: var(--fs-sm);
  color: var(--text-2);
}
.tag-chip:hover {
  background: var(--hover-bg);
  color: var(--text-1);
}
.tag-chip-active {
  background: var(--brand-soft) !important;
  color: var(--brand-text) !important;
  font-weight: 600;
}
.tag-count {
  font-size: var(--fs-xs);
  opacity: 0.7;
}

/* === Spin === */
/* 高度传递链的第二环（P1-11）。滚动容器是外层的 .collection-column，不是这里：
   本元素只负责把确定高度继续往下传给 .module-content。
   不写 overflow —— 一旦在这里设 overflow-y:auto，滚动条就会内缩到内容区右边缘，
   与概览/回收站的 .j-page-body（滚动条贴玻璃面板边框内侧）不一致；
   云盘/密码库无虚拟滚动，内容超出时本元素被 flex 压缩、内容自然溢出，仍由 .collection-column 滚。
   改前这里是 overflow:visible + min-height:200px，且虚拟列表用 calc(100vh - 282px) 猜高度，
   在移动端（chrome 253.5px）和标签换行时失配，制造出第二个滚动条。 */
.spin-area {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 模块内容切换过渡（out-in） */
/* 与 .spin-area 同为 flex 链条的一环，负责把确定高度继续传给下面的列表/网格组件。 */
.module-content {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 列表/网格组件填满 .module-content，把高度继续传给 .virtual-scroll。
   这四条与下方 NSpin 两条共同构成一条「高度自上而下传递」的链路：
     .collection-column(flex列) → .spin-area(flex:1) → .module-content(flex:1)
       → .collection-list/.collection-grid/.note-list/.note-grid(flex:1) → .virtual-scroll(flex:1)
   任一环断掉（未设 flex:1 或未设 min-height:0），虚拟列表就会失去确定高度而撑开，
   退化成「外层滚 + 列表不虚拟」——实测正是改前双滚动条的成因。
   注意子组件样式在各自 scoped 块里，这里必须用 :deep() 才够得着。
   云盘 / 密码库（.disk-view / .vault-view）无虚拟滚动，内容超出时 .spin-area 被 flex
   压缩、内容溢出后由 .collection-column 滚，故不在此列——给它们加 flex:1 只会无谓改变其内部布局。 */
.spin-area :deep(.collection-list),
.spin-area :deep(.collection-grid),
.spin-area :deep(.note-list),
.spin-area :deep(.note-grid) {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* NSpin 是 CollectionList/NoteList/NoteGrid 等的直接子元素，同样要传递高度。 */
.spin-area :deep(.n-spin-container),
.spin-area :deep(.n-spin-content) {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.module-fade-enter-active,
.module-fade-leave-active {
  transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}
.module-fade-enter-from { opacity: 0; transform: translateY(8px); }
.module-fade-leave-to { opacity: 0; transform: translateY(-6px); }

/* === 预览条 === */
.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--glass-chip-bg);
  border-radius: var(--radius-sm);
  font-size: var(--fs-md);
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
  /* 侧栏目录树在窄屏隐藏，靠顶栏目录按钮唤起抽屉 */
  .folder-column {
    display: none;
  }
  .content-area {
    padding: 12px;
    gap: 12px;
  }
  .chip-bar {
    /* 桌面端隐藏（见 .chip-bar 基础规则注释），移动端恢复显示 */
    display: flex;
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