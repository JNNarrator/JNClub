<script setup lang="ts">
/**
 * VaultView.vue — 密码库面板（目录 type=5）
 * 主密钥体系：未设置 → 设置引导；已设置未解锁 → 锁定面板；已解锁 → 条目列表
 * 健康检查：弱/重复密码角标（仅提示不拦截）
 */
import { ref, computed, watch, onMounted, onUnmounted, h } from 'vue'
import {
  NButton, NIcon, NSpin, NTag, NInput, NDropdown, NModal, useMessage, useDialog,
} from 'naive-ui'
import {
  KeyRound, Plus, Pencil, Trash2, Copy, User, Lock, Unlock, ShieldAlert, RotateCcw, Ellipsis, FolderInput, ShieldCheck, Archive,
} from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
import { useUserStore } from '../../../shared/stores/user'
import { useDraggableSort } from '../composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'
import { openMenu } from '../../../shared/composables/useContextMenu'
import PasswordEditorModal from './PasswordEditorModal.vue'
import PasswordRevealPopover from './PasswordRevealPopover.vue'
import EmptyState from './EmptyState.vue'
import MoveItemModal from './MoveItemModal.vue'
import VaultBackupModal from './VaultBackupModal.vue'
import { copyText } from '../../../shared/utils/clipboard'
import axios from 'axios'

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

const showMoveModal = ref(false)
const moveTarget = ref<VaultItem | null>(null)
const showBackupModal = ref(false)
const { setDragging } = useItemDragContext()

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

/** 行点击：打开编辑（与行内编辑按钮一致） */
const handleRowClick = (item: VaultItem) => openEdit(item)
const handleRowKeydown = (e: KeyboardEvent, item: VaultItem) => {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    handleRowClick(item)
  }
}

// ========== 行菜单（三点 + 右键共用；含移动到） ==========
const rowMenu = () => [
  { label: '移动到…', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleRowMenu = (key: string, item: VaultItem) => {
  if (key === 'move') {
    moveTarget.value = item
    showMoveModal.value = true
  } else if (key === 'edit') {
    openEdit(item)
  } else if (key === 'delete') {
    handleDelete(item)
  }
}

// ========== 拖拽到目录树 ==========
const handleDragStart = (e: DragEvent, item: VaultItem) => {
  setDragging({
    itemId: item.id,
    module: 'vault',
    currentDirectoryId: item.directoryId ?? null,
  })
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    try { e.dataTransfer.setData('text/plain', String(item.id)) } catch { /* 忽略 */ }
  }
}

const handleDragEnd = () => setDragging(null)

// ========== 拖拽排序 ==========
const listRef = ref<HTMLElement | null>(null)
const vaultDragDisabled = computed(() => vaultStore.hasMoreItems)
const { init: initSort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
}, vaultDragDisabled)

/** 加载下一页密码条目并追加 */
const loadMoreItems = async () => {
  if (!props.directoryId || vaultStore.loadingMore || !vaultStore.hasMoreItems) return
  await vaultStore.fetchItems(props.directoryId, {
    page: vaultStore.page + 1,
    append: true,
  })
}


// ========== 自动锁定（空闲 5 分钟自动锁库） ==========
const AUTO_LOCK_MS = 5 * 60 * 1000
let idleTimer: ReturnType<typeof setTimeout> | null = null
const resetIdleTimer = () => {
  if (idleTimer) clearTimeout(idleTimer)
  idleTimer = setTimeout(() => {
    if (vaultStore.masterStatus.unlocked) {
      message.info('长时间未操作，密码库已自动锁定')
      vaultStore.lock()
    }
  }, AUTO_LOCK_MS)
}
const IDLE_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const
const bindIdle = () => {
  resetIdleTimer()
  IDLE_EVENTS.forEach(ev => window.addEventListener(ev, resetIdleTimer, { passive: true }))
}
const unbindIdle = () => {
  if (idleTimer) clearTimeout(idleTimer)
  IDLE_EVENTS.forEach(ev => window.removeEventListener(ev, resetIdleTimer))
}
watch(unlocked, (u) => { if (u) bindIdle(); else unbindIdle() })
onUnmounted(unbindIdle)

// ========== TOTP 双因素 ==========
const showTotpModal = ref(false)
const totpItem = ref<VaultItem | null>(null)
const totpState = ref<'loading' | 'code' | 'setup'>('loading')
const totpCode = ref('')
const totpRemaining = ref(0)
const totpSecret = ref('')
const totpBusy = ref(false)
let totpTimer: ReturnType<typeof setInterval> | null = null

const openTotp = async (item: VaultItem) => {
  totpItem.value = item
  showTotpModal.value = true
  await loadTotp()
}
const loadTotp = async () => {
  if (!totpItem.value) return
  totpState.value = 'loading'
  try {
    const res = await axios.get(`/api/vault/${totpItem.value.id}/totp`)
    if (res.data.code === 200) {
      totpCode.value = res.data.data.totp
      totpRemaining.value = res.data.data.remaining
      totpState.value = 'code'
      startTotpCountdown()
      return
    }
    totpState.value = 'setup'
  } catch {
    totpState.value = 'setup'
  }
}
const startTotpCountdown = () => {
  if (totpTimer) clearInterval(totpTimer)
  totpTimer = setInterval(() => {
    totpRemaining.value--
    if (totpRemaining.value <= 0) loadTotp()
  }, 1000)
}
const saveTotp = async () => {
  if (!totpItem.value || !totpSecret.value.trim()) {
    message.warning('请输入 TOTP 种子')
    return
  }
  totpBusy.value = true
  try {
    await axios.put(`/api/vault/${totpItem.value.id}/totp`, { secret: totpSecret.value.trim() })
    message.success('TOTP 已保存')
    await loadTotp()
  } catch (e: any) {
    message.error(e.response?.data?.message || '保存失败')
  } finally { totpBusy.value = false }
}
const deleteTotp = () => {
  if (!totpItem.value) return
  dialog.warning({
    title: '删除 TOTP',
    content: '确定删除该条目的 TOTP 双因素设置吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete(`/api/vault/${totpItem.value!.id}/totp`)
        message.success('已删除')
        closeTotp()
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    },
  })
}
const closeTotp = () => {
  if (totpTimer) clearInterval(totpTimer)
  totpTimer = null
  showTotpModal.value = false
}

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
        <NButton size="small" quaternary title="加密备份（导出/导入）" @click="showBackupModal = true">
          <template #icon><NIcon :component="Archive" size="14" /></template>
          备份
        </NButton>
        <NButton size="small" quaternary class="lock-now" title="锁定密码库" @click="doLock">
          <template #icon><NIcon :component="Lock" size="14" /></template>
          锁定
        </NButton>
      </div>

      <NSpin :show="vaultStore.loading">
        <EmptyState
          v-if="!vaultStore.loading && !vaultStore.items.length"
          icon="vault"
          message="密码库空空如也"
          hint="添加第一条密码，开始你的安全清单"
          cta-label="新建条目"
          @create="openCreate"
        />
        <div v-else ref="listRef" class="vault-list">
          <div
            v-for="item in vaultStore.items"
            :key="item.id"
            :data-id="item.id"
            class="vault-item jnclub-bouncy"
            role="button"
            tabindex="0"
            :aria-label="`编辑密码 ${item.name}`"
            draggable="true"
            @click="handleRowClick(item)"
            @keydown="handleRowKeydown($event, item)"
            @dragstart="handleDragStart($event, item)"
            @dragend="handleDragEnd"
            @contextmenu.prevent="openMenu($event, rowMenu(), (key: string) => handleRowMenu(key, item))"
          >
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
            <div class="item-actions" @click.stop>
              <PasswordRevealPopover :item="item" />
              <NButton quaternary circle size="small" title="TOTP 验证码" @click="openTotp(item)">
                <template #icon><NIcon :component="ShieldCheck" size="16" /></template>
              </NButton>
              <NButton quaternary circle size="small" title="复制密码" @click="copyPwd(item)">
                <template #icon><NIcon :component="Copy" size="16" /></template>
              </NButton>
              <NButton quaternary circle size="small" title="编辑" @click="openEdit(item)">
                <template #icon><NIcon :component="Pencil" size="16" /></template>
              </NButton>
              <NButton quaternary circle size="small" type="error" title="删除" @click="handleDelete(item)">
                <template #icon><NIcon :component="Trash2" size="16" /></template>
              </NButton>
              <!-- 三点菜单（与右键共用 rowMenu） -->
              <NDropdown :options="rowMenu()" @select="(k: string) => handleRowMenu(k, item)" placement="bottom-end">
                <NButton quaternary circle size="small" title="更多操作">
                  <template #icon><NIcon :component="Ellipsis" size="16" /></template>
                </NButton>
              </NDropdown>
            </div>
          </div>
          <div v-if="vaultStore.hasMoreItems" class="load-more-wrap">
            <NButton size="small" quaternary :loading="vaultStore.loadingMore" @click="loadMoreItems">
              {{ vaultStore.loadingMore ? '加载中…' : `加载更多（${vaultStore.items.length}/${vaultStore.totalItems}）` }}
            </NButton>
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

    <!-- 移动到目录弹窗（type=5 密码库目录） -->
    <MoveItemModal
      v-model:show="showMoveModal"
      :item-type="5"
      :targets="moveTarget ? [{ id: moveTarget.id, name: moveTarget.name }] : []"
      :current-directory-id="moveTarget?.directoryId ?? null"
      @refresh="load"
    />

    <!-- TOTP 双因素弹窗 -->
    <NModal v-model:show="showTotpModal" preset="card" :title="totpItem ? `TOTP 验证码 · ${totpItem.name}` : 'TOTP'" style="width: 400px" :bordered="false" @close="closeTotp">
      <div class="totp-body">
        <div v-if="totpState === 'loading'" class="totp-loading">加载中…</div>

        <template v-else-if="totpState === 'code'">
          <div class="totp-code" :class="{ 'totp-code-warn': totpRemaining <= 5 }">{{ totpCode }}</div>
          <div class="totp-remaining">
            <div class="totp-ring" :style="{ '--r': (totpRemaining / 30) * 360 + 'deg' }" />
            <span>{{ totpRemaining }}s 后刷新</span>
          </div>
          <p class="totp-tip">验证码每 30 秒自动刷新，用于该站点二次验证。</p>
          <div class="totp-actions">
            <NButton size="small" quaternary @click="loadTotp">
              <template #icon><NIcon :component="RotateCcw" size="14" /></template>
              立即刷新
            </NButton>
            <NButton size="small" type="error" secondary @click="deleteTotp">删除 TOTP</NButton>
          </div>
        </template>

        <template v-else>
          <p class="totp-tip">输入该站点的 TOTP 种子（Base32，通常在开启两步验证时提供），保存后即可生成动态验证码。</p>
          <NInput v-model:value="totpSecret" placeholder="如 JBSWY3DPEHPK3PXP" size="large" class="totp-secret-input" @keyup.enter="saveTotp" />
          <div class="totp-actions">
            <NButton type="primary" :loading="totpBusy" @click="saveTotp">保存并生成</NButton>
          </div>
        </template>
      </div>
    </NModal>

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

    <!-- 加密备份弹窗 -->
    <VaultBackupModal v-model:show="showBackupModal" @imported="load" />
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
  font-size: var(--fs-md);
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
.strength-text { font-size: var(--fs-sm); min-width: 18px; }
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
  font-size: var(--fs-md);
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
  font-size: var(--fs-sm);
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
.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 8px;
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
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
}
.item-url {
  font-size: var(--fs-xs);
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
  font-size: var(--fs-sm);
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
  font-size: var(--fs-md);
  color: var(--state-error);
  line-height: 1.6;
}
.reset-code {
  font-size: var(--fs-md);
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
.totp-body { display: flex; flex-direction: column; gap: 14px; align-items: center; }
.totp-loading { color: var(--text-3); font-size: var(--fs-sm); padding: 20px 0; }
.totp-code {
  font-family: var(--font-mono);
  font-size: 44px;
  font-weight: 700;
  letter-spacing: 8px;
  color: var(--brand);
  padding: 8px 0;
  transition: color 0.2s;
}
.totp-code-warn { color: var(--danger); }
.totp-remaining {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-sm);
  color: var(--text-2);
}
.totp-ring {
  width: 12px; height: 12px; border-radius: 50%;
  background: conic-gradient(var(--brand) var(--r, 360deg), var(--glass-chip-bg) 0);
}
.totp-tip { font-size: var(--fs-sm); color: var(--text-3); text-align: center; line-height: 1.6; margin: 0; }
.totp-secret-input { width: 100%; }
.totp-actions { display: flex; gap: 10px; justify-content: center; align-items: center; }
</style>