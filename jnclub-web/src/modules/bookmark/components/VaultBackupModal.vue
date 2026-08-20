<script setup lang="ts">
/**
 * VaultBackupModal.vue — 密码库加密备份
 * 导出：输入备份密码 → 后端解密全部条目并用备份密码重加密 → 下载 .jnbackup 文件
 * 导入：选择备份文件 + 备份密码 + 模式(合并/替换) → 解密导入
 */
import { ref } from 'vue'
import {
  NModal, NButton, NIcon, NInput, NRadioGroup, NRadio, NAlert,
  NDivider, useMessage, NTag,
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
    const res = await axios.post('/api/vault/backup/export', { password: exportPassword.value })
    if (res.data.code === 200 && res.data.data?.content) {
      const filename = `jnclub-vault-backup-${new Date().toISOString().slice(0, 10)}.jnbackup`
      const blob = new Blob([res.data.data.content], { type: 'application/octet-stream' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
      message.success('加密备份已导出（请妥善保管备份密码）')
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
    const res = await axios.post('/api/vault/backup/import', {
      content,
      password: importPassword.value,
      mode: importMode.value,
    })
    if (res.data.code === 200) {
      const d = res.data.data || {}
      message.success(`导入完成：新增 ${d.imported ?? 0} 条，跳过 ${d.skipped ?? 0} 条`)
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
    :style="{ width: '560px', maxWidth: '92vw' }"
    title="密码库加密备份"
  >
    <NAlert type="warning" :show-icon="true" class="backup-alert">
      <template #icon><NIcon :component="ShieldCheck" size="16" /></template>
      备份文件使用独立备份密码 AES 加密，请牢记备份密码——密码丢失将无法恢复备份。
    </NAlert>

    <!-- 导出 -->
    <div class="backup-section">
      <div class="section-title">
        <NIcon :component="Download" size="15" />
        <span>导出加密备份</span>
        <NTag size="tiny" round :bordered="false" class="section-tag">.jnbackup</NTag>
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
        <input ref="fileInput" type="file" accept=".jnbackup,text/plain" class="file-input" @change="handleFileChange" :disabled="busy" />
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
          <NRadio value="merge">合并导入（同名跳过）</NRadio>
          <NRadio value="replace">替换导入（清空现有）</NRadio>
        </NRadioGroup>
      </div>
      <div class="section-row">
        <NButton size="small" type="primary" :loading="busy" :disabled="busy || !importFile" @click="handleImport">
          <template #icon><NIcon :component="Upload" size="14" /></template>
          开始导入
        </NButton>
      </div>
    </div>

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
