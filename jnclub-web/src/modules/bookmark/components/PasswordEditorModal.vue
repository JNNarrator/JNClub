<script setup lang="ts">
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NInput, NSlider, useMessage } from 'naive-ui'
import { Lock, Eye, EyeOff, RefreshCw, X } from 'lucide-vue-next'
import { useVaultStore } from '../stores/vault'

const props = defineProps<{
  show: boolean
  editingId: number | null
  directoryId: number | null
  initial?: { name: string; username: string; url: string; notes: string } | null
}>()

const emit = defineEmits<{
  'update:show': [v: boolean]
  saved: []
}>()

const vaultStore = useVaultStore()
const message = useMessage()

const form = ref({ name: '', username: '', password: '', url: '', notes: '' })
const showPwd = ref(false)
const pwdLength = ref(16)
const saving = ref(false)

// 每次打开时重置表单（编辑回填非密码字段，密码留空=保持不变）
watch(() => props.show, (v) => {
  if (!v) return
  const init = props.initial || { name: '', username: '', url: '', notes: '' }
  form.value = {
    name: init.name || '',
    username: init.username || '',
    password: '',
    url: init.url || '',
    notes: init.notes || '',
  }
  showPwd.value = false
  pwdLength.value = 16
})

const generatePwd = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*()_+-='
  const arr = new Uint32Array(pwdLength.value)
  crypto.getRandomValues(arr)
  let pwd = ''
  for (let i = 0; i < pwdLength.value; i++) pwd += chars[arr[i] % chars.length]
  form.value.password = pwd
  showPwd.value = true
}

const submit = async () => {
  if (!form.value.name.trim()) { message.warning('请输入条目名称'); return }
  if (!props.directoryId) { message.warning('请先选择目录'); return }
  saving.value = true
  try {
    const payload = {
      directoryId: props.directoryId,
      name: form.value.name.trim(),
      username: form.value.username.trim(),
      password: form.value.password,
      url: form.value.url.trim(),
      notes: form.value.notes,
    }
    if (props.editingId === null) {
      await vaultStore.createItem(payload)
      message.success('已保存')
    } else {
      await vaultStore.updateItem(props.editingId, payload)
      message.success('已更新')
    }
    emit('saved')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const onShowChange = (v: boolean) => emit('update:show', v)
</script>

<template>
  <NModal
    :show="props.show"
    :mask-closable="false"
    @update:show="onShowChange"
  >
    <div class="pwd-modal glass-card--modal">
      <div class="pwd-modal-head">
        <div class="pwd-modal-title">
          <span class="lock-chip glass-chip"><NIcon :component="Lock" size="15" /></span>
          <span>{{ editingId === null ? '新建密码条目' : '编辑密码条目' }}</span>
        </div>
        <NButton quaternary circle size="small" @click="onShowChange(false)">
          <template #icon><NIcon :component="X" size="16" /></template>
        </NButton>
      </div>

      <div class="pwd-modal-body">
        <div class="form-grid">
          <div class="field">
            <label class="field-label">名称</label>
            <NInput v-model:value="form.name" placeholder="如：GitHub / 邮箱 / 银行卡" clearable />
          </div>
          <div class="field">
            <label class="field-label">密码</label>
            <div class="pwd-input-wrap">
              <NInput
                v-model:value="form.password"
                :type="showPwd ? 'text' : 'password'"
                placeholder="留空保持不变（编辑时）"
                clearable
              />
              <NButton quaternary circle size="small" @click="showPwd = !showPwd">
                <template #icon><NIcon :component="showPwd ? EyeOff : Eye" size="16" /></template>
              </NButton>
            </div>
          </div>
          <div class="field">
            <label class="field-label">账号</label>
            <NInput v-model:value="form.username" placeholder="用户名 / 邮箱 / 卡号" clearable />
          </div>
          <div class="field">
            <label class="field-label">密码长度</label>
            <div class="slider-row">
              <NSlider v-model:value="pwdLength" :min="6" :max="64" :step="1" class="pwd-slider" />
              <span class="length-badge">{{ pwdLength }}</span>
            </div>
          </div>
          <div class="field">
            <label class="field-label">站点地址</label>
            <NInput v-model:value="form.url" placeholder="https://…（可选）" clearable />
          </div>
          <div class="field">
            <label class="field-label">&nbsp;</label>
            <NButton class="gen-btn glass-pill-btn" @click="generatePwd">
              <template #icon><NIcon :component="RefreshCw" size="14" /></template>
              随机生成
            </NButton>
          </div>
        </div>
        <div class="field">
          <label class="field-label">备注</label>
          <NInput v-model:value="form.notes" type="textarea" :rows="3" placeholder="备注（可选）" clearable />
        </div>
      </div>

      <div class="pwd-modal-foot">
        <NButton class="ghost-btn glass-ghost-btn" @click="onShowChange(false)">取消</NButton>
        <NButton class="confirm-btn glass-primary-btn" :loading="saving" @click="submit">确认</NButton>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.pwd-modal {
  width: 640px;
  max-width: calc(100vw - 32px);
  border-radius: 16px;
  padding: 24px;
  color: var(--text-1);
}
.pwd-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.pwd-modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.lock-chip {
  width: 30px;
  height: 30px;
  border-radius: 9px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}
.field { min-width: 0; }
.field-label {
  display: block;
  font-size: 12px;
  color: var(--glass-text-secondary);
  margin-bottom: 6px;
}
.pwd-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 6px;
}
.pwd-slider { flex: 1; }
.length-badge {
  min-width: 34px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--glass-chip-text);
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: 999px;
  padding: 2px 8px;
}
.pwd-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
/* 玻璃输入框：8px 圆角 + 聚焦辉光 */
.pwd-modal :deep(.n-input) {
  border-radius: 8px;
  --n-color: var(--glass-input-bg) !important;
  --n-color-focus: var(--glass-input-bg) !important;
  --n-color-hover: var(--glass-input-bg) !important;
  --n-border: var(--glass-input-border) !important;
  --n-border-hover: var(--glass-chip-border) !important;
  --n-border-focus: var(--brand) !important;
  --n-box-shadow-focus: 0 0 0 3px var(--focus-ring) !important;
  --n-text-color: var(--text-1) !important;
  --n-placeholder-color: var(--glass-text-placeholder) !important;
  --n-caret-color: var(--brand) !important;
}
/* 滑块轨道适配 */
.pwd-modal :deep(.n-slider-rail) {
  background: var(--glass-input-border) !important;
}
/* 绿色 pill 生成按钮（保持"生成"语义） */
.gen-btn {
  background: linear-gradient(135deg, var(--state-success), #059669) !important;
  box-shadow: 0 4px 14px -4px rgba(16, 185, 129, 0.5);
}
.gen-btn:hover { filter: brightness(1.1); }
/* 渐变确认按钮 */
.confirm-btn {
  border-radius: 10px;
}
/* ghost 取消按钮 */
.ghost-btn {
  border-radius: 10px;
}
</style>
