<script setup lang="ts">
/**
 * ExportModal.vue — 数据导出面板（概览页入口）
 * 支持：收藏 JSON / 便签 Markdown ZIP / 全量备份 ZIP
 */
import { ref } from 'vue'
import { NModal, NButton, NIcon, NList, NListItem, NThing, NTag, useMessage } from 'naive-ui'
import { Download, Bookmark, StickyNote, Database, FileArchive } from 'lucide-vue-next'
import axios from 'axios'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [v: boolean] }>()
const message = useMessage()

const exporting = ref<string | null>(null)

const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

const today = () => new Date().toISOString().slice(0, 10)

const doExport = async (type: 'bookmarks' | 'notes' | 'all') => {
  exporting.value = type
  try {
    const res = await axios.get(`/api/export/${type}`, { responseType: 'blob', timeout: 120000 })
    const filename = `${type === 'all' ? 'jnclub-backup' : type === 'notes' ? 'jnclub-notes' : 'jnclub-bookmarks'}-${today()}.${type === 'bookmarks' ? 'json' : 'zip'}`
    downloadBlob(res.data, filename)
    message.success('导出成功，已开始下载')
  } catch (e: any) {
    // blob 错误信息
    let msg = '导出失败'
    try {
      const text = await e.response?.data?.text?.()
      if (text) msg = text.includes('{') ? JSON.parse(text).message || msg : msg
    } catch { /* ignore */ }
    message.error(msg)
  } finally {
    exporting.value = null
  }
}

const items = [
  {
    key: 'bookmarks' as const,
    label: '收藏数据',
    desc: 'JSON 文件：目录结构 + 收藏列表（含标签）',
    icon: Bookmark,
    ext: '.json',
  },
  {
    key: 'notes' as const,
    label: '便签备份',
    desc: 'ZIP 包：每篇便签一个 Markdown 文件',
    icon: StickyNote,
    ext: '.zip',
  },
  {
    key: 'all' as const,
    label: '全量备份',
    desc: 'ZIP 包：收藏 JSON + 便签 Markdown + 云盘文件清单 + 统计',
    icon: FileArchive,
    ext: '.zip',
  },
]
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    preset="card"
    class="export-modal"
    :style="{ width: '520px', maxWidth: '92vw' }"
    title="数据导出"
  >
    <div class="export-tip">
      <NIcon :component="Database" size="14" />
      导出当前账号全部数据，用于本地备份或迁移
    </div>
    <NList hoverable clickable>
      <NListItem v-for="it in items" :key="it.key" @click="!exporting && doExport(it.key)">
        <NThing>
          <template #avatar>
            <div class="export-item-icon">
              <NIcon :component="it.icon" size="18" />
            </div>
          </template>
          <template #header>
            <div class="export-item-head">
              <span>{{ it.label }}</span>
              <NTag size="tiny" round :bordered="false" class="export-ext-tag">{{ it.ext }}</NTag>
            </div>
          </template>
          <template #description>
            <span class="export-item-desc">{{ it.desc }}</span>
          </template>
          <template #action>
            <NButton
              size="small" type="primary" tertiary
              :loading="exporting === it.key"
              :disabled="!!exporting"
              @click.stop="doExport(it.key)"
            >
              <template #icon>
                <NIcon v-if="exporting !== it.key" :component="Download" size="14" />
              </template>
              {{ exporting === it.key ? '导出中…' : '导出' }}
            </NButton>
          </template>
        </NThing>
      </NListItem>
    </NList>
    <template #footer>
      <NButton size="small" quaternary @click="emit('update:show', false)">关闭</NButton>
    </template>
  </NModal>
</template>

<style scoped>
.export-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
  color: var(--text-3);
  margin-bottom: 12px;
}
.export-item-icon {
  width: 36px; height: 36px;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex; align-items: center; justify-content: center;
}
.export-item-head {
  display: flex; align-items: center; gap: 8px;
}
.export-item-desc {
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.export-ext-tag {
  background: var(--glass-chip-bg) !important;
  color: var(--glass-chip-text) !important;
}
</style>
