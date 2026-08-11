<script setup lang="ts">
/**
 * VaultView.vue — 密码库面板（目录 type=5）
 * 主密钥体系：未设置 → 设置引导；已设置未解锁 → 锁定面板；已解锁 → 条目列表
 * 健康检查：弱/重复密码角标（仅提示不拦截）
 */
import { ref, computed, watch, onMounted } from 'vue'
import {
  NButton, NIcon, NSpin, NEmpty, NTag, NInput, useMessage, useDialog,
} from 'naive-ui'
import {
  KeyRound, Plus, Pencil, Trash2, Copy, User, Lock, Unlock, ShieldAlert, RotateCcw,
} from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
import { useUserStore } from '../../../shared/stores/user'
import { useDraggableSort } from '../composables/useDraggableSort'
import PasswordEditorModal from './PasswordEditorModal.vue'
import PasswordRevealPopover from './PasswordRevealPopover.vue'
import { copyText } from '../../../shared/utils/clipboard'

const props = defineProps<{
  directoryId: number | null
}>()

const emit = defineEmits<{
  refresh: []
  sort: [orderedIds: number[]]
}>()

const message = useMessage()
const dialog = useDialog()
const vaultStore = useVaultStore()
const userStore = useUserStore()

const configured = computed(() => vaultStore.masterStatus.configured)
const unlocked = computed(() => vaultStore.masterStatus.unlocked)

/** 未设置主密钥时的"暂不设置"（会话内记住，兼容旧配置密钥存量数据） */
const SKIP_KEY = 'jn-vault-setup-skip'
const skipped = ref(false)
try { skipped.value = sessionStorage.getItem(SKIP_KEY) === '1' } catch { /* 忽略 */ }

const skipSetup = () => {
  skipped.value = true
  try { sessionStorage.setItem(SKIP_KEY, '1') } catch { /* 忽略 */ }
}

// ========== 列表 ==========
const load = async () => {
  if (!props.directoryId || !unlocked.value) return
  await vaultStore.fetchItems(props.directoryId)
  try { await vaultStore.fetchHealth() } catch { /* 健康检查失败不影响列表 */ }
}

watch(() => props.directoryId, () => { if (unlocked.value) load() })

// ========== 主密钥：设置向导 ==========
const setupForm = ref({ key: '', confirm: '' })
const setupBusy = ref(false)

const setupStrength = computed(() => {
  const s = vaultStrengthScore(setupForm.value.key)
  return { score: s, level: s < 60 ? 'weak' : s < 80 ? 'medium' : 'strong' }
})

const doSetup = async () => {
  const { key, confirm } = setupForm.value
  if (key.length < 8) { message.warning('主密钥至少 8 位'); return }
  if (key !== confirm) { message.warning('两次输入不一致'); return }
  setupBusy.value = true
  try {
    await vaultStore.setMasterKey(key)
    message.success('主密钥已设置，全部密码已加密')
    try { sessionStorage.removeItem(SKIP_KEY) } catch { /* 忽略 */ }
    await load()
  } catch (e: any) {
    message.error(e.message || '设置失败')
  } finally { setupBusy.value = false }
}

// ========== 主密钥：解锁 / 锁定 ==========
const unlockForm = ref('')
const unlockBusy = ref(false)

const doUnlock = async () => {
  if (!unlockForm.value) { message.warning('请输入主密钥'); return }
  unlockBusy.value = true
  try {
    await vaultStore.unlock(unlockForm.value)
    message.success('已解锁')
    await load()
  } catch (e: any) {
    message.error(e.message || '解锁失败')
  } finally {
    unlockBusy.value = false
  }
}

const doLock = async () => {
  await vaultStore.lock()
  message.info('密码库已锁定')
}

/** 遗忘重置：双重确认（第一层告知后果 → 第二层输入确认码 RESET + 展示重置验证码） */
const resetCode = computed(() => {
  const uid = userStore.userinfo?.id || ''
  return uid.length > 8 ? uid.slice(-8) : uid
})

const showResetModal = ref(false)
const resetInput = ref('')
const resetBusy = ref(false)

const doReset = () => {
  dialog.warning({
    title: '重置密码库（不可恢复）',
    content: '将永久删除全部密码条目与主密钥设置，且无法恢复。确定继续吗？',
    positiveText: '继续',
    negativeText: '取消',
    onPositiveClick: () => {
      resetInput.value = ''
      showResetModal.value = true
    },
  })
}

const confirmReset = async () => {
  if (resetInput.value.trim().toUpperCase() !== 'RESET') {
    message.warning('请输入确认码 RESET')
    return
  }
  resetBusy.value = true
  try {
    await vaultStore.reset(resetCode.value)
    message.success('密码库已重置，请重新设置主密钥')
    showResetModal.value = false
  } catch (e: any) {
    message.error(e.message || '重置失败')
  } finally { resetBusy.value = false }
}

/** 简化强度评分（前端即时提示，服务端为准） */
const vaultStrengthScore = (pwd: string): number => {
  if (!pwd) return 0
  let score = Math.min(50, pwd.length * 4)
  if (/[A-Z]/.test(pwd)) score += 8
  if (/[a-z]/.test(pwd)) score += 8
  if (/[0-9]/.test(pwd)) score += 8
  if (/[^A-Za-z0-9]/.test(pwd)) score += 8
  return Math.max(0, Math.min(100, score))
}

// ========== 新建 / 编辑 ==========
const modalShow = ref(false)
const editingId = ref<number | null>(null)
const modalInitial = ref<{ name: string; username: string; url: string; notes: string } | null>(null)

const openCreate = () => {
  if (!unlocked.value) {
    message.warning(configured.value ? '请先输入主密钥解锁' : '请先设置主密钥')
    return
  }
  editingId.value = null
  modalInitial.value = null
  modalShow.value = true
}

const openEdit = (item: VaultItem) => {
  editingId.value = item.id
  modalInitial.value = {
    name: item.name,
    username: item.username || '',
    url: item.url || '',
    notes: item.notes || '',
  }
  modalShow.value = true
}

const onSaved = () => {
  modalShow.value = false
  load()
}

// ========== 复制密码 ==========
const copyPwd = async (item: VaultItem) => {
  let pwd = item.password
  if (pwd == null) {
    try {
      const detail = await vaultStore.fetchDetail(item.id)
      pwd = detail.password
    } catch (e: any) {
      message.error(e.message || '获取密码失败')
      return
    }
  }
  if (!pwd) { message.warning('内容为空'); return }
  if (await copyText(pwd)) message.success('密码已复制')
  else message.error('复制失败')
}

// ========== 删除 ==========
const handleDelete = (item: VaultItem) => {
  dialog.warning({
    title: '确认删除',
    content: `确定删除"${item.name}"吗？删除后进入回收站。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await vaultStore.deleteItem(item.id)
        message.success('已移入回收站')
        load()
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

// ========== 拖拽排序 ==========
const listRef = ref<HTMLElement | null>(null)
const { init: initSort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
})

onMounted(async () => {
  await vaultStore.fetchMasterStatus()
  if (unlocked.value) await load()
  initSort()
})

// 健康角标判定
const isWeak = (id: number) => vaultStore.health.weakIds.has(id)
const isDup = (id: number) => vaultStore.health.dupIds.has(id)

defineExpose({ openCreate })
</script>

<template>
  <div class="vault-view">
    <!-- 未设置主密钥：设置引导 -->
    <div v-if="!configured && !skipped" class="master-guide glass-panel">
      <div class="guide-head">
        <div class="guide-icon"><NIcon :component="ShieldAlert" size="22" /></div>
        <div>
          <div class="guide-title">设置密码库主密钥</div>
          <div class="guide-desc">主密钥用于加密全部密码条目，密钥仅存于内存，永不落库。</div>
        </div>
      </div>
      <div class="guide-warn">
        主密钥遗忘后<b>无法找回</b>，只能重置清空密码库。请妥善保管！
      </div>
      <div class="guide-form">
        <NInput
          v-model:value="setupForm.key"
          type="password" show-password-on="click"
          placeholder="设置主密钥（至少 8 位）"
          size="large"
        />
        <NInput
          v-model:value="setupForm.confirm"
          type="password" show-password-on="click"
          placeholder="再次输入确认"
          size="large"
        />
        <div class="strength-bar">
          <div class="strength-track">
            <div class="strength-fill" :class="'lv-' + setupStrength.level" :style="{ width: setupStrength.score + '%' }"></div>
          </div>
          <span class="strength-text" :class="'lv-' + setupStrength.level">
            {{ setupStrength.score < 60 ? '弱' : setupStrength.score < 80 ? '中' : '强' }}
          </span>
        </div>
      </div>
      <div class="guide-actions">
        <NButton type="primary" :loading="setupBusy" @click="doSetup">设置并加密</NButton>
        <NButton quaternary @click="skipSetup">暂不设置</NButton>
      </div>
    </div>

    <!-- 已设置未解锁：锁定面板 -->
    <div v-else-if="configured && !unlocked" class="master-lock glass-panel">
      <div class="lock-icon"><NIcon :component="Lock" size="30" /></div>
      <div class="lock-title">密码库已锁定</div>
      <div class="lock-desc">输入主密钥解锁以查看密码条目</div>
      <NInput
        v-model:value="unlockForm"
        type="password" show-password-on="click"
        placeholder="主密钥"
        size="large"
        class="lock-input"
        @keyup.enter="doUnlock"
      />
      <div class="lock-actions">
        <NButton type="primary" :loading="unlockBusy" class="lock-btn" @click="doUnlock">
          <template #icon><NIcon :component="Unlock" /></template>
          解锁
        </NButton>
      </div>
      <button type="button" class="reset-link" @click="doReset">
        <NIcon :component="RotateCcw" size="13" style="vertical-align: -2px" />
        遗忘主密钥？重置密码库
      </button>
    </div>

    <!-- 已解锁：正常列表 -->
    <template v-else>
      <div class="vault-toolbar">
        <NButton size="small" type="primary" secondary @click="openCreate">
          <template #icon><NIcon :component="Plus" size="15" /></template>
          新建条目
        </NButton>
        <NButton size="small" quaternary class="lock-now" title="锁定密码库" @click="doLock">
          <template #icon><NIcon :component="Lock" size="14" /></template>
          锁定
        </NButton>
      </div>

      <NSpin :show="vaultStore.loading">
        <NEmpty v-if="!vaultStore.loading && !vaultStore.items.length" description="这个目录还没有密码条目" class="vault-empty" />
        <div v-else ref="listRef" class="vault-list">
          <div v-for="item in vaultStore.items" :key="item.id" :data-id="item.id" class="vault-item jnclub-bouncy">
            <div class="item-icon"><NIcon :component="KeyRound" size="20" /></div>
            <div class="item-main">
              <div class="item-title">
                {{ item.name }}
                <span v-if="item.url" class="item-url">{{ item.url.replace(/^https?:\/\//, '').split('/')[0] }}</span>
              </div>
              <div class="item-meta">
                <span class="meta-user">
                  <NIcon :component="User" size="12" />
                  {{ item.username || '--' }}
                </span>
                <NTag size="tiny" round :bordered="false" class="pwd-tag">密码已加密</NTag>
                <!-- 健康角标：弱/重复（仅提示不拦截） -->
                <NTag v-if="isWeak(item.id)" size="tiny" round :bordered="false" class="health-tag weak" title="密码强度弱，建议修改">弱</NTag>
                <NTag v-if="isDup(item.id)" size="tiny" round :bordered="false" class="health-tag dup" title="与其他条目密码重复">重复</NTag>
              </div>
            </div>
            <div class="item-actions">
              <PasswordRevealPopover :item="item" />
              <NButton quaternary circle size="small" title="复制密码" @click="copyPwd(item)">
                <template #icon><NIcon :component="Copy" size="16" /></template>
              </NButton>
              <NButton quaternary circle size="small" title="编辑" @click="openEdit(item)">
                <template #icon><NIcon :component="Pencil" size="16" /></template>
              </NButton>
              <NButton quaternary circle size="small" type="error" title="删除" @click="handleDelete(item)">
                <template #icon><NIcon :component="Trash2" size="16" /></template>
              </NButton>
            </div>
          </div>
        </div>
      </NSpin>
    </template>

    <!-- 新建/编辑弹窗（玻璃拟态） -->
    <PasswordEditorModal
      v-model:show="modalShow"
      :editing-id="editingId"
      :directory-id="props.directoryId"
      :initial="modalInitial"
      @saved="onSaved"
    />

    <!-- 遗忘重置：二次确认（输入确认码 RESET） -->
    <NModal v-model:show="showResetModal" preset="dialog" title="确认重置密码库">
      <div class="reset-modal">
        <p class="reset-danger">
          此操作将<strong>永久删除</strong>全部密码条目与主密钥设置，无法恢复。
        </p>
        <p class="reset-code">重置验证码：<code>{{ resetCode }}</code></p>
        <NInput
          v-model:value="resetInput"
          placeholder="输入确认码 RESET 以继续"
          size="large"
          @keyup.enter="confirmReset"
        />
      </div>
      <template #action>
        <NButton @click="showResetModal = false">取消</NButton>
        <NButton type="error" :loading="resetBusy" @click="confirmReset">确认重置</NButton>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.glass-panel {
  padding: 28px 24px;
  border-radius: var(--radius-md);
}

/* === 主密钥设置引导 === */
.guide-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}
.guide-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.guide-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.guide-desc {
  font-size: 13px;
  color: var(--text-2);
}
.guide-warn {
  font-size: var(--fs-sm);
  color: var(--state-warning);
  background: var(--state-warning-soft);
  border: 1px solid color-mix(in srgb, var(--state-warning) 25%, transparent);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  margin-bottom: 14px;
  line-height: 1.6;
}
.guide-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.strength-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.strength-track {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--hover-bg);
  overflow: hidden;
}
.strength-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s var(--ease), background 0.3s;
}
.strength-fill.lv-weak { background: var(--state-error); }
.strength-fill.lv-medium { background: var(--state-warning); }
.strength-fill.lv-strong { background: var(--state-success); }
.strength-text { font-size: 12px; min-width: 18px; }
.strength-text.lv-weak { color: var(--state-error); }
.strength-text.lv-medium { color: var(--state-warning); }
.strength-text.lv-strong { color: var(--state-success); }
.guide-actions {
  display: flex;
  gap: 10px;
}

/* === 锁定面板 === */
.master-lock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 24px;
  text-align: center;
}
.lock-icon {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
}
.lock-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}
.lock-desc {
  font-size: 13px;
  color: var(--text-2);
}
.lock-input {
  max-width: 320px;
  margin-top: 8px;
}
.lock-actions {
  margin-top: 4px;
}
.lock-btn {
  min-width: 160px;
}
.reset-link {
  margin-top: 12px;
  border: none;
  background: transparent;
  color: var(--text-3);
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.reset-link:hover {
  color: var(--state-error);
}

/* === 列表 === */
.vault-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.lock-now {
  margin-left: auto;
  color: var(--text-3);
}
.vault-empty { padding: 40px 0; }
.vault-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.vault-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease);
}
.vault-item:hover {
  background: var(--glass-chip-bg);
  box-shadow: inset 3px 0 0 var(--brand);
}
.item-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand);
  flex-shrink: 0;
}
.item-main {
  flex: 1;
  min-width: 0;
}
.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
.item-url {
  font-size: 11px;
  color: var(--text-3);
  font-weight: 400;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}
.item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  flex-wrap: wrap;
}
.meta-user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
}
.pwd-tag {
  background: var(--hover-bg) !important;
  color: var(--text-3) !important;
}
.health-tag.weak {
  background: var(--state-warning-soft) !important;
  color: var(--state-warning) !important;
  border: 1px solid var(--state-warning-soft) !important;
}
.health-tag.dup {
  background: var(--state-error-soft) !important;
  color: var(--state-error) !important;
  border: 1px solid var(--state-error-soft) !important;
}
.item-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

/* 移动端 */
@media (max-width: 767px) {
  .glass-panel {
    padding: 20px 16px;
  }
  .master-lock {
    padding: 32px 16px;
  }
}

/* 重置确认弹窗 */
.reset-modal {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}
.reset-danger {
  font-size: 13px;
  color: var(--state-error);
  line-height: 1.6;
}
.reset-code {
  font-size: 13px;
  color: var(--text-2);
}
.reset-code code {
  font-family: var(--font-mono);
  background: var(--hover-bg);
  color: var(--brand);
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 1px;
}
</style>
