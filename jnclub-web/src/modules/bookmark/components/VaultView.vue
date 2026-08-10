<script setup lang="ts">
/**
 * VaultView.vue — 密码库面板（目录 type=5）
 * 条目列表（不显示密码）+ 新建/编辑（含随机密码生成）+ 详情（复制密码）+ 软删除
 */
import { ref, watch, onMounted } from 'vue'
import {
  NButton, NIcon, NSpin, NEmpty, NTag, useMessage, useDialog,
} from 'naive-ui'
import {
  KeyRound, Plus, Pencil, Trash2, Copy, User,
} from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
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

// ========== 列表 ==========
const load = async () => {
  if (!props.directoryId) return
  await vaultStore.fetchItems(props.directoryId)
}

watch(() => props.directoryId, () => load())

// ========== 新建 / 编辑（弹窗已拆分为独立组件） ==========
const modalShow = ref(false)
const editingId = ref<number | null>(null)
const modalInitial = ref<{ name: string; username: string; url: string; notes: string } | null>(null)

const openCreate = () => {
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

// ========== 复制密码（先拉明文，再走共享剪贴板工具，HTTP 环境可用） ==========
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
onMounted(() => { initSort() })

defineExpose({ openCreate })
</script>

<template>
  <div class="vault-view">
    <div class="vault-toolbar">
      <NButton size="small" type="primary" secondary @click="openCreate">
        <template #icon><NIcon :component="Plus" size="15" /></template>
        新建条目
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

    <!-- 新建/编辑弹窗（玻璃拟态） -->
    <PasswordEditorModal
      v-model:show="modalShow"
      :editing-id="editingId"
      :directory-id="props.directoryId"
      :initial="modalInitial"
      @saved="onSaved"
    />
  </div>
</template>

<style scoped>
.vault-toolbar {
  display: flex;
  margin-bottom: 14px;
}
.vault-empty { padding: 40px 0; }
.vault-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.vault-list :deep(.sortable-ghost) {
  opacity: 0.4;
  background: var(--brand-soft) !important;
  border-radius: var(--radius-sm);
  outline: 2px dashed var(--brand);
  outline-offset: -2px;
}
.vault-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}
.vault-item:hover { border-color: var(--brand); }
.item-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--brand-soft);
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
  gap: 8px;
  margin-top: 3px;
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
.item-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
</style>
