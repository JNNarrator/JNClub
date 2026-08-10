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
    <div class="pwd-modal">
      <div class="pwd-modal-head">
        <div class="pwd-modal-title">
          <span class="lock-chip"><NIcon :component="Lock" size="15" /></span>
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
            <NButton class="gen-btn" @click="generatePwd">
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
        <NButton class="ghost-btn" @click="onShowChange(false)">取消</NButton>
        <NButton class="confirm-btn" :loading="saving" @click="submit">确认</NButton>
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
  color: #fff;
  background:
    radial-gradient(1200px 500px at 10% -10%, rgba(124, 58, 237, 0.35), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, rgba(37, 99, 235, 0.3), transparent 60%),
    linear-gradient(160deg, #1e1b4b 0%, #172554 55%, #0f172a 100%);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow:
    0 24px 64px -12px rgba(2, 6, 23, 0.7),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
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
  color: #fff;
}
.lock-chip {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.22);
  border: 1px solid rgba(167, 139, 250, 0.35);
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
  color: rgba(255, 255, 255, 0.6);
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
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.18);
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 999px;
  padding: 2px 8px;
}
.pwd-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
/* 玻璃输入框：8px 圆角 + 内阴影 + 轻边框 + 聚焦辉光 */
.pwd-modal :deep(.n-input) {
  border-radius: 8px;
  --n-color: rgba(255, 255, 255, 0.06) !important;
  --n-color-focus: rgba(255, 255, 255, 0.09) !important;
  --n-color-hover: rgba(255, 255, 255, 0.08) !important;
  --n-border: rgba(255, 255, 255, 0.14) !important;
  --n-border-hover: rgba(167, 139, 250, 0.6) !important;
  --n-border-focus: #8b5cf6 !important;
  --n-box-shadow-focus: 0 0 0 3px rgba(139, 92, 246, 0.25) !important;
  --n-text-color: #fff !important;
  --n-placeholder-color: rgba(255, 255, 255, 0.35) !important;
  --n-caret-color: #a78bfa !important;
}
/* 滑块轨道/圆点适配深色 */
.pwd-modal :deep(.n-slider-rail) {
  background: rgba(255, 255, 255, 0.14) !important;
}
/* 绿色 pill 生成按钮 */
.gen-btn {
  border-radius: 999px !important;
  background: linear-gradient(135deg, #10b981, #059669) !important;
  color: #fff !important;
  border: none !important;
  font-weight: 600;
  box-shadow: 0 4px 14px -4px rgba(16, 185, 129, 0.5);
}
.gen-btn:hover { filter: brightness(1.1); }
/* 渐变主按钮 */
.confirm-btn {
  border-radius: 10px;
  background: linear-gradient(135deg, #8b5cf6, #3b82f6) !important;
  color: #fff !important;
  border: none !important;
  font-weight: 600;
  box-shadow: 0 4px 14px -4px rgba(99, 102, 241, 0.5);
}
.confirm-btn:hover { filter: brightness(1.12); }
/* ghost 取消按钮 */
.ghost-btn {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: rgba(255, 255, 255, 0.85);
}
.ghost-btn:hover { background: rgba(255, 255, 255, 0.14); }
</style>
