/**
 * DiskView.vue — 云盘面板
 * 单文件分片上传（断点续传）+ 文件列表 + 下载 / 删除
 * P0 增强：重命名 / 移动到目录 / 多选批量（移动、删除）
 */
<script setup lang="ts">
import { ref, watch, onMounted, computed, h, nextTick } from 'vue'
import {
  NButton, NIcon, NSpin, NProgress, NTag, useMessage, useDialog,
  NDropdown, NCheckbox, NModal, NInput, NSelect, NForm, NFormItem,
} from 'naive-ui'
import EmptyState from './EmptyState.vue'
import { Pause, Play, Download, Trash2, FileText, Pencil, FolderInput, Ellipsis, X, Eye, Archive } from 'lucide-vue-next'
import { useCloudDiskStore, type DiskFile } from '../stores/clouddisk'
import { useChunkedUpload } from '../composables/useChunkedUpload'
import { useDraggableSort } from '../composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'
import { openMenu } from '../../../shared/composables/useContextMenu'
import axios from 'axios'

const props = defineProps<{
  directoryId: number | null
  /** 外部「上传文件」按钮触发计数，变化时打开文件选择框 */
  trigger?: number
}>()

const emit = defineEmits<{
  refresh: []
  sort: [orderedIds: number[]]
}>()

const message = useMessage()
const dialog = useDialog()
const diskStore = useCloudDiskStore()
const uploader = useChunkedUpload()
const { setDragging } = useItemDragContext()

const fileInputRef = ref<HTMLInputElement | null>(null)

/** 单文件选择触发（组件未挂载/ref 未就绪时 nextTick 重试，避免偶发不弹文件框） */
const triggerSelect = () => {
  if (!props.directoryId) {
    message.warning('请先选择一个云盘目录')
    return
  }
  if (uploader.state.value?.status === 'uploading') {
    message.warning('已有文件正在上传')
    return
  }
  const openPicker = () => fileInputRef.value?.click()
  if (fileInputRef.value) {
    openPicker()
  } else {
    // 组件可能刚挂载，ref 尚未绑定 DOM，延迟到渲染完成后触发
    nextTick(openPicker)
  }
}

/** 监听外部上传触发（顶栏/右下角按钮） */
watch(() => props.trigger, (v, old) => {
  if (old !== undefined && v !== old) triggerSelect()
})

/** 选择文件后开始分片上传 */
const onFileSelected = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!props.directoryId) return
  try {
    await uploader.startUpload(file, props.directoryId)
    if (uploader.state.value?.status === 'completed') {
      message.success('上传完成')
      emit('refresh')
      uploader.reset()
    }
  } catch (err: any) {
    // 停止/错误时错误状态由 composable 内部标记；此处兜底提示
    message.error(err?.message || '上传失败')
  }
}

const handlePause = () => uploader.pause()
const handleResume = async () => {
  try {
    await uploader.resume()
    if (uploader.state.value?.status === 'completed') {
      message.success('上传完成')
      emit('refresh')
      uploader.reset()
    }
  } catch { /* 错误状态已由内部设置 */ }
}

const handleDownload = (file: DiskFile) => {
  // 用浏览器打开下载接口（响应头含 Content-Disposition，恢复原始文件名）
  window.open(`/api/clouddisk/files/${file.id}/download`, '_blank')
}

/** 批量打包下载 zip */
const handleBatchDownload = () => {
  if (!selectedIds.value.length) return
  window.open(`/api/clouddisk/files/download-batch?ids=${selectedIds.value.join(',')}`, '_blank')
}

/* ─── 在线预览（图片 / 文本 / PDF，复用 /api/files 只读代理） ─── */
const IMAGE_RE = /\.(png|jpe?g|gif|webp|svg|avif|bmp)$/i
const TEXT_RE = /\.(txt|md|json|js|mjs|ts|css|html|htm|xml|log|csv|properties|yaml|yml|sql|java|py|sh|ini)$/i
const PDF_RE = /\.pdf$/i

const preview = ref<{ file: DiskFile; kind: 'image' | 'pdf' | 'text'; text?: string } | null>(null)
const previewTextLoading = ref(false)

const openPreview = async (file: DiskFile) => {
  const name = file.originalName || ''
  if (PDF_RE.test(name)) {
    preview.value = { file, kind: 'pdf' }
    return
  }
  if (IMAGE_RE.test(name)) {
    preview.value = { file, kind: 'image' }
    return
  }
  if (TEXT_RE.test(name)) {
    preview.value = { file, kind: 'text' }
    previewTextLoading.value = true
    try {
      const res = await axios.get(file.url, { responseType: 'text' })
      preview.value = { file, kind: 'text', text: typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2) }
    } catch {
      preview.value = { file, kind: 'text', text: '（预览内容加载失败，请下载查看）' }
    } finally { previewTextLoading.value = false }
    return
  }
  message.info('该类型暂不支持预览，请下载查看')
}

const closePreview = () => { preview.value = null }

const handleDelete = (file: DiskFile) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除"${file.originalName}"吗？删除后进入回收站，可在回收站恢复。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await diskStore.deleteFile(file.id)
        message.success('已移入回收站')
        emit('refresh')
      } catch (err: any) {
        message.error(err.response?.data?.message || '删除失败')
      }
    },
  })
}

const st = uploader.state

/** 文件列表拖拽排序 */
const fileListRef = ref<HTMLElement | null>(null)
const { init: initSort } = useDraggableSort(fileListRef, (ids) => {
  emit('sort', ids.map(Number))
})
onMounted(() => { initSort() })

// ============================================================
// P0 增强：重命名 / 移动 / 多选批量
// ============================================================

/** 多选状态 */
const selectedIds = ref<number[]>([])
const batchMode = ref(false)

const toggleBatchMode = (on: boolean) => {
  batchMode.value = on
  if (!on) selectedIds.value = []
}
const toggleSelect = (id: number) => {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}
const isSelected = (id: number) => selectedIds.value.includes(id)
const allSelected = computed(() =>
  diskStore.files.length > 0 && selectedIds.value.length === diskStore.files.length)
const toggleAll = () => {
  if (allSelected.value) selectedIds.value = []
  else selectedIds.value = diskStore.files.map(f => f.id)
}

/** 重命名 */
const showRenameModal = ref(false)
const renameTarget = ref<DiskFile | null>(null)
const renameForm = ref({ name: '' })

const openRename = (file: DiskFile) => {
  renameTarget.value = file
  renameForm.value = { name: file.originalName }
  showRenameModal.value = true
}

const submitRename = async () => {
  if (!renameTarget.value || !renameForm.value.name.trim()) {
    message.warning('请输入文件名')
    return
  }
  try {
    await axios.put(`/api/clouddisk/files/${renameTarget.value.id}/rename`, { name: renameForm.value.name.trim() })
    message.success('重命名成功')
    showRenameModal.value = false
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '重命名失败')
  }
}

/** 移动到：目标目录（云盘 type=3 目录树） */
const diskDirOptions = ref<{ label: string; value: number }[]>([])
const loadDiskDirs = async () => {
  try {
    const res = await axios.get('/api/directories', { params: { type: 3 } })
    if (res.data.code === 200) {
      const flat: { label: string; value: number }[] = []
      const walk = (dirs: any[], prefix: string) => {
        for (const d of dirs) {
          flat.push({ label: prefix + d.name, value: d.id })
          if (d.children?.length) walk(d.children, prefix + d.name + ' / ')
        }
      }
      walk(res.data.data || [], '')
      diskDirOptions.value = flat
    }
  } catch { /* 静默 */ }
}

const showMoveModal = ref(false)
const moveTargets = ref<DiskFile[]>([])
const moveForm = ref({ directoryId: null as number | null })

const openMove = (file: DiskFile) => {
  moveTargets.value = [file]
  moveForm.value = { directoryId: null }
  showMoveModal.value = true
}

/** 批量移动到 */
const openBatchMove = () => {
  if (!selectedIds.value.length) return
  moveTargets.value = diskStore.files.filter(f => selectedIds.value.includes(f.id))
  moveForm.value = { directoryId: null }
  showMoveModal.value = true
}

const submitMove = async () => {
  if (!moveForm.value.directoryId) {
    message.warning('请选择目标目录')
    return
  }
  const targetDirId = moveForm.value.directoryId
  try {
    if (moveTargets.value.length === 1) {
      await axios.put(`/api/clouddisk/files/${moveTargets.value[0].id}/move`, { directoryId: targetDirId })
    } else {
      await axios.put('/api/clouddisk/files/move-batch', {
        ids: moveTargets.value.map(f => f.id),
        directoryId: targetDirId,
      })
    }
    message.success(`已移动 ${moveTargets.value.length} 个文件`)
    showMoveModal.value = false
    if (batchMode.value) toggleBatchMode(false)
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '移动失败')
  }
}

/** 批量删除 */
const handleBatchDelete = () => {
  if (!selectedIds.value.length) return
  dialog.warning({
    title: '批量删除',
    content: `确定删除选中的 ${selectedIds.value.length} 个文件吗？删除后进入回收站。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete('/api/clouddisk/files/batch', { data: { ids: selectedIds.value } })
        message.success(`已移入回收站 ${selectedIds.value.length} 个文件`)
        toggleBatchMode(false)
        emit('refresh')
      } catch (e: any) {
        message.error(e.response?.data?.message || '批量删除失败')
      }
    },
  })
}

/** 行下拉菜单 */
const rowMenu = () => [
  { label: '预览', key: 'preview', icon: () => h(NIcon, null, { default: () => h(Eye) }) },
  { label: '下载', key: 'download', icon: () => h(NIcon, null, { default: () => h(Download) }) },
  { label: '重命名', key: 'rename', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '移动到', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleRowMenu = (key: string, file: DiskFile) => {
  if (key === 'preview') openPreview(file)
  else if (key === 'download') handleDownload(file)
  else if (key === 'rename') openRename(file)
  else if (key === 'move') openMove(file)
  else if (key === 'delete') handleDelete(file)
}

/** 拖拽到目录树：写入跨容器上下文 */
const handleDragStart = (e: DragEvent, file: DiskFile) => {
  setDragging({
    itemId: file.id,
    module: 'files',
    currentDirectoryId: file.directoryId ?? null,
  })
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    try { e.dataTransfer.setData('text/plain', String(file.id)) } catch { /* 忽略 */ }
  }
}

const handleDragEnd = () => setDragging(null)

watch(() => props.directoryId, () => { loadDiskDirs() })
onMounted(loadDiskDirs)

/** 文件类型彩色图标映射（图片/文档/压缩包/音频，其余用中性色） */
const FILE_KINDS = [
  { re: /\.(png|jpe?g|gif|webp|svg|avif)$/i, color: '#7EB8E8' },
  { re: /\.(docx?|pdf|txt|md|pptx?|xlsx?)$/i, color: '#F472B6' },
  { re: /\.(zip|rar|7z|tar|gz)$/i, color: '#F3C470' },
  { re: /\.(mp3|wav|flac|aac|ogg|m4a)$/i, color: '#7AC686' },
] as const
const fileKindColor = (name: string) => FILE_KINDS.find(k => k.re.test(name))?.color ?? 'var(--text-3)'
</script>

<template>
  <div class="disk-view">
    <!-- 文件选择 input 由顶栏/右下角「上传文件」按钮触发，隐藏于此 -->
    <input
      ref="fileInputRef"
      type="file"
      class="hidden-file-input"
      @change="onFileSelected"
    />

    <!-- 上传进度 -->
    <div v-if="st && st.status !== 'completed'" class="upload-progress-card">
      <div class="upload-progress-name">
        <NIcon :component="FileText" size="16" />
        <span class="name">{{ st.file.name }}</span>
        <NTag size="small" :type="st.status === 'error' ? 'error' : 'info'">
          {{ st.status === 'uploading' ? '上传中' : st.status === 'paused' ? '已暂停' : st.status === 'error' ? '失败' : '等待中' }}
        </NTag>
      </div>
      <NProgress
        :percentage="uploader.progressPercent.value"
        :show-indicator="true"
        processing
      />
      <div class="upload-progress-actions">
        <span class="chunk-info">
          分片 {{ st.uploadedChunks.size }}/{{ st.totalChunks }}
          <template v-if="st.status === 'error'"> · {{ st.error }}</template>
        </span>
        <NButton
          v-if="st.status === 'uploading'"
          size="small" quaternary
          @click="handlePause"
        >
          <template #icon><NIcon :component="Pause" /></template>
          暂停
        </NButton>
        <NButton
          v-else-if="st.status === 'paused'"
          size="small" type="primary" secondary
          @click="handleResume"
        >
          <template #icon><NIcon :component="Play" /></template>
          继续
        </NButton>
      </div>
    </div>

    <!-- 文件列表 -->
    <div class="file-area">
      <NSpin :show="diskStore.loading">
        <EmptyState
          v-if="!diskStore.loading && !diskStore.files.length"
          icon="file"
          message="这个目录还没有文件"
          hint="上传第一个文件，开始你的云盘整理"
          cta-label="上传文件"
          @create="triggerSelect"
        />
        <template v-else>
          <!-- 工具栏：多选模式切换 / 全选 -->
          <div class="disk-toolbar">
            <NButton size="tiny" quaternary :type="batchMode ? 'primary' : 'default'" @click="toggleBatchMode(!batchMode)">
              {{ batchMode ? '退出多选' : '多选' }}
            </NButton>
            <NButton v-if="batchMode" size="tiny" quaternary @click="toggleAll">
              {{ allSelected ? '取消全选' : '全选' }}
            </NButton>
            <span v-if="batchMode && selectedIds.length" class="batch-count">已选 {{ selectedIds.length }} 项</span>
          </div>

          <div ref="fileListRef" class="file-list">
            <div
              v-for="file in diskStore.files" :key="file.id" :data-id="file.id"
              :class="['file-item', 'jnclub-bouncy', { 'file-item-selected': isSelected(file.id) }]"
              draggable="true"
              @dragstart="handleDragStart($event, file)"
              @dragend="handleDragEnd"
              @contextmenu.prevent="openMenu($event, rowMenu(), (key: string) => handleRowMenu(key, file))"
            >
              <NCheckbox
                v-if="batchMode"
                :checked="isSelected(file.id)"
                @update:checked="() => toggleSelect(file.id)"
                class="file-check"
              />
              <div class="file-icon"><NIcon :component="FileText" size="20" :style="{ color: fileKindColor(file.originalName) }" /></div>
              <div class="file-main">
                <div class="file-name previewable" :title="file.originalName" @click="openPreview(file)">{{ file.originalName }}</div>
                <div class="file-meta">{{ diskStore.formatSize(file.size) }}</div>
              </div>
              <div class="file-actions">
                <NButton quaternary circle size="small" title="下载" @click="handleDownload(file)">
                  <template #icon><NIcon :component="Download" size="16" /></template>
                </NButton>
                <NDropdown :options="rowMenu()" @select="(k: string) => handleRowMenu(k, file)" placement="bottom-end">
                  <NButton quaternary circle size="small" title="更多">
                    <template #icon><NIcon :component="Ellipsis" size="16" /></template>
                  </NButton>
                </NDropdown>
              </div>
            </div>
          </div>

          <!-- 批量操作条 -->
          <Transition name="batch-up">
            <div v-if="batchMode && selectedIds.length" class="batch-bar">
              <span class="batch-info">已选 {{ selectedIds.length }} 个文件</span>
              <NButton size="small" type="primary" secondary @click="openBatchMove">
                <template #icon><NIcon :component="FolderInput" size="14" /></template>
                移动到
              </NButton>
              <NButton size="small" type="primary" @click="handleBatchDownload">
                <template #icon><NIcon :component="Archive" size="14" /></template>
                打包下载
              </NButton>
              <NButton size="small" type="error" secondary @click="handleBatchDelete">
                <template #icon><NIcon :component="Trash2" size="14" /></template>
                删除
              </NButton>
              <NButton size="small" quaternary @click="toggleBatchMode(false)">
                <template #icon><NIcon :component="X" size="14" /></template>
                取消
              </NButton>
            </div>
          </Transition>
        </template>
      </NSpin>
    </div>

    <!-- 重命名弹窗 -->
    <NModal v-model:show="showRenameModal" preset="dialog" title="重命名">
      <NForm style="margin-top: 12px;">
        <NFormItem label="文件名">
          <NInput v-model:value="renameForm.name" placeholder="请输入新文件名" @keyup.enter="submitRename" />
        </NFormItem>
      </NForm>
      <template #action>
        <NButton @click="showRenameModal = false">取消</NButton>
        <NButton type="primary" @click="submitRename">确定</NButton>
      </template>
    </NModal>

    <!-- 在线预览弹窗 -->
    <NModal
      :show="preview !== null"
      :style="{ width: preview?.kind === 'pdf' ? 'min(900px, 92vw)' : 'min(560px, 92vw)' }"
      :bordered="false"
      @update:show="(v: boolean) => { if (!v) closePreview() }"
    >
      <div v-if="preview" class="preview-card">
        <div class="preview-head">
          <span class="preview-name" :title="preview.file.originalName">{{ preview.file.originalName }}</span>
          <NButton quaternary circle size="small" @click="closePreview">
            <template #icon><NIcon :component="X" size="15" /></template>
          </NButton>
        </div>
        <div v-if="preview.kind === 'image'" class="preview-body preview-image">
          <img :src="preview.file.url" :alt="preview.file.originalName" />
        </div>
        <div v-else-if="preview.kind === 'pdf'" class="preview-body preview-pdf">
          <iframe :src="preview.file.url" />
        </div>
        <div v-else class="preview-body preview-text">
          <NSpin :show="previewTextLoading">
            <pre>{{ preview.text || '' }}</pre>
          </NSpin>
        </div>
      </div>
    </NModal>

    <!-- 移动到弹窗 -->
    <NModal v-model:show="showMoveModal" preset="dialog" :title="`移动到${moveTargets.length > 1 ? `（${moveTargets.length} 个文件）` : ''}`">
      <NForm style="margin-top: 12px;">
        <NFormItem label="目标目录">
          <NSelect v-model:value="moveForm.directoryId" :options="diskDirOptions" placeholder="选择云盘目录" filterable clearable />
        </NFormItem>
      </NForm>
      <template #action>
        <NButton @click="showMoveModal = false">取消</NButton>
        <NButton type="primary" @click="submitMove">确定</NButton>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.hidden-file-input { display: none; }

.upload-progress-card {
  padding: 14px 16px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  margin-bottom: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.upload-progress-name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.upload-progress-name .name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--text-1);
}
.upload-progress-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chunk-info {
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.file-area { min-height: 200px; }
.disk-empty { padding: 40px 0; }

/* 工具栏 */
.disk-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.batch-count {
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease);
}
.file-item:hover { border-color: var(--brand); box-shadow: var(--shadow-card-hover); }
.file-item-selected {
  border-color: var(--brand);
  background: var(--brand-soft);
}
.file-check { flex-shrink: 0; }
.file-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-xs);
  background: var(--glass-chip-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  flex-shrink: 0;
}
.file-main {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-meta {
  font-size: var(--fs-sm);
  color: var(--text-3);
  margin-top: 2px;
}
.file-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

/* 文件名可预览 */
.file-name.previewable { cursor: pointer; }
.file-name.previewable:hover { color: var(--brand); }

/* 预览弹窗 */
.preview-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
}
.preview-name {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-body {
  max-height: 70vh;
  overflow: auto;
  background: #111;
}
.preview-image {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
}
.preview-image img { max-width: 100%; max-height: 65vh; border-radius: var(--radius-xs); }
.preview-pdf iframe { width: 100%; height: 70vh; border: none; display: block; }
.preview-text pre {
  margin: 0;
  padding: 14px 16px;
  font-family: var(--font-mono);
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-all;
}

/* 批量操作条 */
.batch-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 16px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  box-shadow: var(--glass-shadow);
}
.batch-info {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--text-2);
}

/* 批量条入场过渡 */
.batch-up-enter-active {
  transition: opacity 0.22s var(--ease), transform 0.22s var(--ease-bouncy);
}
.batch-up-enter-from {
  opacity: 0;
  transform: translateY(14px);
}
.batch-up-leave-active {
  transition: opacity 0.15s var(--ease);
}
.batch-up-leave-to {
  opacity: 0;
}
</style>
