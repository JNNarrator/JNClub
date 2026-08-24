<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { NModal, NButton, NIcon, NInput, NSlider, NCheckbox, NSwitch, useMessage } from 'naive-ui'
import { Lock, Eye, EyeOff, RefreshCw, X, Copy } from 'lucide-vue-next'
import { useVaultStore } from '../stores/vault'
import { generatePassword, passwordStrength, DEFAULT_PASSWORD_OPTIONS } from '../../../shared/utils/password'

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
const pwdOpts = ref({ ...DEFAULT_PASSWORD_OPTIONS })
const saving = ref(false)

/** 当前输入密码强度 */
const strength = computed(() => passwordStrength(form.value.password))

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
  pwdOpts.value = { ...DEFAULT_PASSWORD_OPTIONS }
})

const generatePwd = () => {
  form.value.password = generatePassword({ ...pwdOpts.value })
  showPwd.value = true
}

const copyPwd = async () => {
  if (!form.value.password) { message.warning('请先生成密码'); return }
  try {
    await navigator.clipboard.writeText(form.value.password)
    message.success('密码已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
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
              <NButton quaternary circle size="small" title="复制密码" @click="copyPwd">
                <template #icon><NIcon :component="Copy" size="16" /></template>
              </NButton>
            </div>
            <!-- 强度条 -->
            <div class="strength-row" v-if="form.password">
              <div class="strength-track">
                <div
                  class="strength-fill"
                  :class="`strength-${strength.level}`"
                  :style="{ width: `${Math.max(4, strength.score)}%` }"
                />
              </div>
              <span class="strength-label" :class="`strength-${strength.level}`">{{ strength.label }} {{ strength.score }}</span>
            </div>
          </div>
          <div class="field">
            <label class="field-label">账号</label>
            <NInput v-model:value="form.username" placeholder="用户名 / 邮箱 / 卡号" clearable />
          </div>
          <div class="field">
            <label class="field-label">站点地址</label>
            <NInput v-model:value="form.url" placeholder="https://…（可选）" clearable />
          </div>
        </div>

        <!-- 密码生成器 -->
        <div class="generator glass-chip">
          <div class="generator-head">
            <span class="generator-title">
              <NIcon :component="RefreshCw" size="13" /> 密码生成器
            </span>
            <NButton size="tiny" type="primary" secondary class="gen-btn" @click="generatePwd">
              <template #icon><NIcon :component="RefreshCw" size="13" /></template>
              随机生成
            </NButton>
          </div>
          <div class="generator-body">
            <div class="gen-row">
              <span class="gen-label">长度</span>
              <NSlider v-model:value="pwdOpts.length" :min="6" :max="64" :step="1" class="pwd-slider" />
              <span class="length-badge">{{ pwdOpts.length }}</span>
            </div>
            <div class="gen-row gen-chars">
              <NCheckbox v-model:checked="pwdOpts.upper">大写</NCheckbox>
              <NCheckbox v-model:checked="pwdOpts.lower">小写</NCheckbox>
              <NCheckbox v-model:checked="pwdOpts.digits">数字</NCheckbox>
              <NCheckbox v-model:checked="pwdOpts.symbols">符号</NCheckbox>
              <span class="gen-sep" />
              <NSwitch v-model:value="pwdOpts.excludeAmbiguous" size="small">
                <template #checked>排除易混淆</template>
                <template #unchecked>排除易混淆</template>
              </NSwitch>
            </div>
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
  border-radius: var(--radius-md);
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
  border-radius: var(--radius-sm);
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}
.field { min-width: 0; }
.field-label {
  display: block;
  font-size: var(--fs-sm);
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
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--glass-chip-text);
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-pill);
  padding: 2px 8px;
}
.pwd-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
/* 玻璃输入框：圆角走 token，色彩由 main.css 全局覆盖，此处只补文本/占位符色 */
.pwd-modal :deep(.n-input) {
  border-radius: var(--radius-sm);
  --n-text-color: var(--text-1) !important;
  --n-placeholder-color: var(--glass-text-placeholder) !important;
}
/* 滑块轨道适配 */
.pwd-modal :deep(.n-slider-rail) {
  background: var(--glass-input-border) !important;
}
/* 绿色 pill 生成按钮（保持"生成"语义，渐变/阴影走 token） */
.gen-btn {
  background: var(--gradient-success) !important;
  box-shadow: var(--shadow-success);
}
.gen-btn:hover { filter: brightness(1.1); }
/* 渐变确认按钮 */
.confirm-btn {
  border-radius: var(--radius-sm);
}
/* ghost 取消按钮 */
.ghost-btn {
  border-radius: var(--radius-sm);
}

/* ─── 强度条 ─── */
.strength-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.strength-track {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--glass-input-bg);
  border: 1px solid var(--glass-input-border);
  overflow: hidden;
}
.strength-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 240ms var(--ease-bouncy);
}
.strength-fill.strength-weak { background: var(--danger); }
.strength-fill.strength-medium { background: var(--warning-text); }
.strength-fill.strength-strong { background: var(--success-text); }
.strength-label {
  font-size: var(--fs-xs, 12px);
  font-weight: 600;
  white-space: nowrap;
  min-width: 52px;
  text-align: right;
}
.strength-label.strength-weak { color: var(--danger); }
.strength-label.strength-medium { color: var(--warning-text); }
.strength-label.strength-strong { color: var(--success-text); }

/* ─── 密码生成器 ─── */
.generator {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  margin: 4px 0 16px;
  border-radius: var(--radius-sm);
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
}
.generator-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.generator-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--glass-text-secondary);
}
.generator-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.gen-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.gen-label {
  font-size: var(--fs-sm);
  color: var(--glass-text-secondary);
  min-width: 28px;
}
.gen-chars {
  flex-wrap: wrap;
}
.gen-sep {
  flex: 1;
}

/* 移动端：弹窗贴边、表单单列 */
@media (max-width: 767px) {
  .pwd-modal {
    width: calc(100vw - 24px);
    max-width: calc(100vw - 24px);
    padding: 18px;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
