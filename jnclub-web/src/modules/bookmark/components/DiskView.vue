/**
 * DiskView.vue — 云盘面板
 * 单文件分片上传（断点续传）+ 文件列表 + 下载 / 删除
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  NButton, NIcon, NSpin, NEmpty, NProgress, NTag, useMessage, useDialog,
} from 'naive-ui'
import { Pause, Play, Download, Trash2, FileText } from 'lucide-vue-next'
import { useCloudDiskStore, type DiskFile } from '../stores/clouddisk'
import { useChunkedUpload } from '../composables/useChunkedUpload'

const props = defineProps<{
  directoryId: number | null
  /** 外部「上传文件」按钮触发计数，变化时打开文件选择框 */
  trigger?: number
}>()

const emit = defineEmits<{
  refresh: []
}>()

const message = useMessage()
const dialog = useDialog()
const diskStore = useCloudDiskStore()
const uploader = useChunkedUpload()

const fileInputRef = ref<HTMLInputElement | null>(null)

/** 单文件选择触发 */
const triggerSelect = () => {
  if (!props.directoryId) {
    message.warning('请先选择一个云盘目录')
    return
  }
  if (uploader.state.value?.status === 'uploading') {
    message.warning('已有文件正在上传')
    return
  }
  fileInputRef.value?.click()
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

const handleDelete = (file: DiskFile) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除"${file.originalName}"吗？此操作不可恢复。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await diskStore.deleteFile(file.id)
        message.success('删除成功')
        emit('refresh')
      } catch (err: any) {
        message.error(err.response?.data?.message || '删除失败')
      }
    },
  })
}

const st = uploader.state
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
        <NEmpty v-if="!diskStore.loading && !diskStore.files.length" description="这个目录还没有文件" class="disk-empty" />
        <div v-else class="file-list">
          <div v-for="file in diskStore.files" :key="file.id" class="file-item jnclub-bouncy">
            <div class="file-icon"><NIcon :component="FileText" size="20" /></div>
            <div class="file-main">
              <div class="file-name" :title="file.originalName">{{ file.originalName }}</div>
              <div class="file-meta">{{ diskStore.formatSize(file.size) }}</div>
            </div>
            <div class="file-actions">
              <NButton quaternary circle size="small" title="下载" @click="handleDownload(file)">
                <template #icon><NIcon :component="Download" size="16" /></template>
              </NButton>
              <NButton quaternary circle size="small" type="error" title="删除" @click="handleDelete(file)">
                <template #icon><NIcon :component="Trash2" size="16" /></template>
              </NButton>
            </div>
          </div>
        </div>
      </NSpin>
    </div>
  </div>
</template>

<style scoped>
.hidden-file-input { display: none; }

.upload-progress-card {
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
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
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
}
.upload-progress-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chunk-info {
  font-size: 12px;
  color: var(--text-3);
}

.file-area { min-height: 200px; }
.disk-empty { padding: 40px 0; }

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
}
.file-item:hover { border-color: var(--brand); }
.file-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--hover-bg);
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
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-meta {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
}
.file-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
</style>
