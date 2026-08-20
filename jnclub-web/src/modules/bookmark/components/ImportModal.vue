<script setup lang="ts">
/**
 * ImportModal.vue — 数据导入面板（概览页入口）
 * 支持：收藏 JSON（JNClub 导出）/ 浏览器书签 HTML / 便签 Markdown ZIP
 * 模式：merge=合并（目录按名复用+条目去重）/ replace=清空后导入
 */
import { ref } from 'vue'
import {
  NModal, NButton, NIcon, NRadioGroup, NRadio, NAlert, useMessage, NTag,
} from 'naive-ui'
import { Upload, Bookmark, StickyNote, FileCode2, ShieldCheck } from 'lucide-vue-next'
import axios from 'axios'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [v: boolean]; imported: [] }>()
const message = useMessage()

type ImportKind = 'bookmarks-json' | 'bookmarks-html' | 'notes-zip'
const mode = ref<'merge' | 'replace'>('merge')
const busy = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)

const onFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
}

const doImport = async (kind: ImportKind) => {
  if (!selectedFile.value) { message.warning('请先选择文件'); return }
  busy.value = true
  try {
    if (kind === 'notes-zip') {
      // ZIP：multipart 上传
      const fd = new FormData()
      fd.append('file', selectedFile.value)
      fd.append('mode', mode.value)
      const res = await axios.post('/api/import/notes', fd, { timeout: 120000 })
      if (res.data.code === 200) {
        const d = res.data.data || {}
        message.success(`导入完成：新增 ${d.imported ?? 0} 篇，跳过 ${d.skipped ?? 0} 篇`)
        emit('imported')
      } else message.error(res.data.message || '导入失败')
    } else {
      // JSON / HTML：读取文本后 POST
      const text = await selectedFile.value.text()
      const url = kind === 'bookmarks-json' ? '/api/import/bookmarks' : '/api/import/bookmarks/html'
      const res = await axios.post(url, { content: text, mode: mode.value }, { timeout: 120000 })
      if (res.data.code === 200) {
        const d = res.data.data || {}
        message.success(`导入完成：新增 ${d.imported ?? 0} 条，跳过 ${d.skipped ?? 0} 条`)
        emit('imported')
      } else message.error(res.data.message || '导入失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '导入失败')
  } finally {
    busy.value = false
  }
}

const sections: { key: ImportKind; label: string; desc: string; icon: any; accept: string; tag: string }[] = [
  { key: 'bookmarks-json', label: '收藏 JSON', desc: '导入 JNClub 导出的收藏 JSON（含目录/标签）', icon: Bookmark, accept: '.json,application/json', tag: 'JNClub 导出' },
  { key: 'bookmarks-html', label: '浏览器书签', desc: '导入 Chrome/Edge 导出的书签 HTML（自动重建目录树）', icon: FileCode2, accept: '.html,.htm,text/html', tag: 'Chrome/Edge' },
  { key: 'notes-zip', label: '便签 ZIP', desc: '导入便签 Markdown 压缩包（识别 frontmatter 标题/目录）', icon: StickyNote, accept: '.zip,application/zip', tag: 'Markdown' },
]
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    preset="card"
    :style="{ width: '560px', maxWidth: '92vw' }"
    title="数据导入"
  >
    <NAlert type="info" :show-icon="true" class="import-alert">
      <template #icon><NIcon :component="ShieldCheck" size="16" /></template>
      支持合并导入（目录按名复用、条目自动去重）或替换导入（清空当前模块数据后导入）。
    </NAlert>

    <div class="import-sections">
      <div v-for="s in sections" :key="s.key" class="import-section">
        <div class="section-head">
          <div class="section-icon"><NIcon :component="s.icon" size="16" /></div>
          <div class="section-info">
            <div class="section-title">
              {{ s.label }}
              <NTag size="tiny" round :bordered="false" class="section-tag">{{ s.tag }}</NTag>
            </div>
            <div class="section-desc">{{ s.desc }}</div>
          </div>
        </div>
        <div class="section-actions">
          <input
            ref="fileInput"
            type="file"
            class="file-input"
            :accept="s.accept"
            :disabled="busy"
            @change="onFileChange"
          />
          <NButton
            size="small" type="primary" secondary
            :loading="busy" :disabled="busy || !selectedFile"
            @click="doImport(s.key)"
          >
            <template #icon><NIcon :component="Upload" size="14" /></template>
            导入
          </NButton>
        </div>
      </div>
    </div>

    <div class="mode-row">
      <span class="mode-label">导入模式</span>
      <NRadioGroup v-model:value="mode" size="small" :disabled="busy">
        <NRadio value="merge">合并导入（推荐）</NRadio>
        <NRadio value="replace">替换导入（清空现有）</NRadio>
      </NRadioGroup>
    </div>

    <template #footer>
      <NButton size="small" quaternary @click="emit('update:show', false)">关闭</NButton>
    </template>
  </NModal>
</template>

<style scoped>
.import-alert {
  margin-bottom: 14px;
}
.import-sections {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 14px;
}
.import-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
}
.section-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.section-icon {
  width: 32px; height: 32px;
  border-radius: 9px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.section-info {
  flex: 1;
  min-width: 0;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
}
.section-tag {
  background: var(--glass-chip-bg) !important;
  color: var(--glass-chip-text) !important;
}
.section-desc {
  font-size: var(--fs-sm);
  color: var(--text-3);
  margin-top: 2px;
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.file-input {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--text-2);
}
.mode-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}
.mode-label {
  font-size: var(--fs-sm);
  color: var(--text-3);
  flex-shrink: 0;
}
</style>
