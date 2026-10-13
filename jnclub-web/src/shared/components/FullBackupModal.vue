<script setup lang="ts">
/**
 * FullBackupModal.vue — 一键全量备份 / 恢复
 * 导出：输入备份密码 → 后端打包（收藏+便签+密码库+偏好+云盘清单）并用备份密码 AES 加密 → 下载 .jncb 文件
 * 导入：选择备份文件 + 备份密码 + 模式(合并/替换) → 解密恢复各分区
 */
import { ref } from 'vue'
import {
  NModal, NButton, NIcon, NInput, NRadioGroup, NRadio, NAlert,
  NDivider, useMessage, NTag, NSpin,
} from 'naive-ui'
import { Download, Upload, ShieldCheck } from 'lucide-vue-next'
import axios from 'axios'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [v: boolean]; imported: [] }>()
const message = useMessage()

const exportPassword = ref('')
const importPassword = ref('')
const importMode = ref<'merge' | 'replace'>('merge')
const importFile = ref<File | null>(null)
const busy = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)

const reset = () => {
  exportPassword.value = ''
  importPassword.value = ''
  importMode.value = 'merge'
  importFile.value = null
}

const handleExport = async () => {
  if (exportPassword.value.length < 8) { message.warning('备份密码至少 8 位'); return }
  busy.value = true
  try {
    const res = await axios.post('/api/backup/export', { password: exportPassword.value })
    if (res.data.code === 200 && res.data.data?.content) {
      const filename = res.data.data.fileName || `jnclub-full-backup-${new Date().toISOString().slice(0, 10)}.jncb`
      const blob = new Blob([res.data.data.content], { type: 'application/octet-stream' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
      message.success('全量加密备份已导出（请妥善保管备份密码）')
    } else {
      message.error(res.data.message || '导出失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '导出失败')
  } finally {
    busy.value = false
  }
}

const handleFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  importFile.value = input.files?.[0] || null
}

const handleImport = async () => {
  if (!importFile.value) { message.warning('请选择备份文件'); return }
  if (importPassword.value.length < 8) { message.warning('请输入备份密码（至少 8 位）'); return }
  busy.value = true
  try {
    const content = await importFile.value.text()
    const res = await axios.post('/api/backup/import', {
      content,
      password: importPassword.value,
      mode: importMode.value,
    })
    if (res.data.code === 200) {
      const d = res.data.data || {}
      const bookmarks = d.bookmarks || {}
      const notes = d.notes || {}
      const vault = d.vault || {}
      const parts: string[] = []
      if (bookmarks.imported != null) parts.push(`收藏 +${bookmarks.imported} 跳过${bookmarks.skipped ?? 0}`)
      if (notes.imported != null) parts.push(`便签 +${notes.imported} 跳过${notes.skipped ?? 0}`)
      if (vault.imported != null) parts.push(`密码库 +${vault.imported} 跳过${vault.skipped ?? 0}`)
      if (d.preferences != null) parts.push(`偏好 ${d.preferences} 项`)
      if (d.files != null) parts.push(`云盘清单 ${d.files} 个（二进制未包含）`)
      message.success(parts.length ? parts.join('，') : '恢复完成')
      emit('imported')
      emit('update:show', false)
      reset()
    } else {
      message.error(res.data.message || '导入失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '导入失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    preset="card"
    :style="{ width: '600px', maxWidth: '92vw' }"
    title="一键全量备份 / 恢复"
  >
    <NAlert type="warning" :show-icon="true" class="backup-alert">
      <template #icon><NIcon :component="ShieldCheck" size="16" /></template>
      全量备份包含 <b>收藏、便签、密码库（明文密码）、用户偏好</b>，使用独立备份密码 AES 加密。
      请牢记备份密码——密码丢失将无法恢复备份。云盘文件仅含清单，不包含文件二进制。
    </NAlert>

    <NSpin :show="busy">
      <!-- 导出 -->
      <div class="backup-section">
        <div class="section-title">
          <NIcon :component="Download" size="15" />
          <span>导出全量备份</span>
          <NTag size="tiny" round :bordered="false" class="section-tag">.jncb</NTag>
        </div>
        <div class="section-row">
          <NInput
            v-model:value="exportPassword"
            type="password"
            show-password-on="click"
            placeholder="设置备份密码（至少 8 位）"
            size="small"
            :disabled="busy"
            @keyup.enter="handleExport"
          />
          <NButton size="small" type="primary" secondary :loading="busy" :disabled="busy" @click="handleExport">
            <template #icon><NIcon :component="Download" size="14" /></template>
            导出备份
          </NButton>
        </div>
      </div>

      <NDivider style="margin: 16px 0" />

      <!-- 导入 -->
      <div class="backup-section">
        <div class="section-title">
          <NIcon :component="Upload" size="15" />
          <span>导入恢复备份</span>
        </div>
        <div class="section-row">
          <input ref="fileInput" type="file" accept=".jncb,.jnbackup,text/plain" class="file-input" @change="handleFileChange" :disabled="busy" />
        </div>
        <div class="section-row">
          <NInput
            v-model:value="importPassword"
            type="password"
            show-password-on="click"
            placeholder="备份密码"
            size="small"
            :disabled="busy"
            @keyup.enter="handleImport"
          />
        </div>
        <div class="section-row">
          <NRadioGroup v-model:value="importMode" size="small" :disabled="busy">
            <NRadio value="merge">合并恢复（同名跳过）</NRadio>
            <NRadio value="replace">替换恢复（清空现有数据）</NRadio>
          </NRadioGroup>
        </div>
        <div class="section-row">
          <NButton size="small" type="primary" :loading="busy" :disabled="busy || !importFile" @click="handleImport">
            <template #icon><NIcon :component="Upload" size="14" /></template>
            开始恢复
          </NButton>
        </div>
      </div>
    </NSpin>

    <template #footer>
      <NButton size="small" quaternary @click="emit('update:show', false)">关闭</NButton>
    </template>
  </NModal>
</template>

<style scoped>
.backup-alert {
  margin-bottom: 16px;
}
.backup-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
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
.section-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.file-input {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--text-2);
  padding: 6px 0;
}
</style>
