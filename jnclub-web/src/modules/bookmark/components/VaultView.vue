<script setup lang="ts">
/**
 * VaultView.vue — 密码库面板（目录 type=5）
 * 条目列表（不显示密码）+ 新建/编辑（含随机密码生成）+ 详情（复制密码）+ 软删除
 */
import { ref, watch, onMounted } from 'vue'
import {
  NButton, NIcon, NSpin, NEmpty, NTag, useMessage, useDialog,
  NModal, NForm, NFormItem, NInput, NSpace, NInputGroup, NInputGroupLabel, NSelect,
} from 'naive-ui'
import {
  KeyRound, Plus, Pencil, Trash2, Copy, Eye, EyeOff, RefreshCw, Lock, User,
} from 'lucide-vue-next'
import { useVaultStore, type VaultItem } from '../stores/vault'
import { useDraggableSort } from '../composables/useDraggableSort'

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

// ========== 新建 / 编辑 ==========
const showModal = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ name: '', username: '', password: '', url: '', notes: '' })
const showPwd = ref(false)

const openCreate = () => {
  editingId.value = null
  form.value = { name: '', username: '', password: '', url: '', notes: '' }
  showPwd.value = false
  showModal.value = true
}

const openEdit = (item: VaultItem) => {
  editingId.value = item.id
  form.value = { name: item.name, username: item.username || '', password: '', url: item.url || '', notes: item.notes || '' }
  showPwd.value = false
  showModal.value = true
}

const submit = async () => {
  if (!form.value.name.trim()) { message.warning('请输入条目名称'); return }
  if (!props.directoryId) { message.warning('请先选择目录'); return }
  try {
    const payload = {
      directoryId: props.directoryId,
      name: form.value.name.trim(),
      username: form.value.username.trim(),
      password: form.value.password,
      url: form.value.url.trim(),
      notes: form.value.notes,
    }
    if (editingId.value === null) {
      await vaultStore.createItem(payload)
      message.success('已保存')
    } else {
      await vaultStore.updateItem(editingId.value, payload)
      message.success('已更新')
    }
    showModal.value = false
    load()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  }
}

// ========== 随机密码生成 ==========
const pwdLength = ref(16)
const generatePwd = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*()_+-='
  const arr = new Uint32Array(pwdLength.value)
  crypto.getRandomValues(arr)
  let pwd = ''
  for (let i = 0; i < pwdLength.value; i++) pwd += chars[arr[i] % chars.length]
  form.value.password = pwd
  showPwd.value = true
}

// ========== 详情（解密查看/复制） ==========
const revealPwd = async (item: VaultItem) => {
  try {
    const detail = await vaultStore.fetchDetail(item.id)
    item.password = detail.password
  } catch (e: any) {
    message.error(e.message || '获取密码失败')
  }
}

const copyText = async (text: string, tip: string) => {
  if (!text) { message.warning('内容为空'); return }
  try {
    await navigator.clipboard.writeText(text)
    message.success(tip)
  } catch {
    message.error('复制失败')
  }
}

const copyPwd = async (item: VaultItem) => {
  if (item.password == null) {
    await revealPwd(item)
    if (item.password == null) return
  }
  await copyText(item.password, '密码已复制')
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
            <NButton quaternary circle size="small" :title="item.password != null ? '隐藏密码' : '显示密码'" @click="item.password != null ? (item.password = null) : revealPwd(item)">
              <template #icon><NIcon :component="item.password != null ? EyeOff : Eye" size="16" /></template>
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
          </div>
        </div>
      </div>
    </NSpin>

    <!-- 新建/编辑弹窗 -->
    <NModal v-model:show="showModal" preset="dialog" :title="editingId === null ? '新建密码条目' : '编辑密码条目'">
      <NForm :model="form" style="margin-top: 12px;">
        <NFormItem label="名称" path="name">
          <NInput v-model:value="form.name" placeholder="如：GitHub / 邮箱 / 银行卡" clearable />
        </NFormItem>
        <NFormItem label="账号" path="username">
          <NInput v-model:value="form.username" placeholder="用户名 / 邮箱 / 卡号" clearable />
        </NFormItem>
        <NFormItem label="密码" path="password">
          <div class="pwd-field">
            <NInput
              v-model:value="form.password"
              :type="showPwd ? 'text' : 'password'"
              placeholder="留空保持不变（编辑时）"
              clearable
            >
              <template #prefix><NIcon :component="Lock" size="15" /></template>
            </NInput>
            <NButton quaternary circle size="small" @click="showPwd = !showPwd">
              <template #icon><NIcon :component="showPwd ? EyeOff : Eye" size="16" /></template>
            </NButton>
          </div>
          <div class="pwd-generate">
            <NInputGroup size="small">
              <NInputGroupLabel>长度</NInputGroupLabel>
              <NSelect v-model:value="pwdLength" :options="[8,12,16,20,24].map(n => ({ label: String(n), value: n }))" style="width: 76px" />
              <NButton @click="generatePwd">
                <template #icon><NIcon :component="RefreshCw" size="14" /></template>
                随机生成
              </NButton>
            </NInputGroup>
          </div>
        </NFormItem>
        <NFormItem label="站点地址" path="url">
          <NInput v-model:value="form.url" placeholder="https://…（可选）" clearable />
        </NFormItem>
        <NFormItem label="备注" path="notes">
          <NInput v-model:value="form.notes" type="textarea" :rows="2" placeholder="备注（可选）" clearable />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace>
          <NButton @click="showModal = false">取消</NButton>
          <NButton type="primary" @click="submit">确定</NButton>
        </NSpace>
      </template>
    </NModal>
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
.pwd-field {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.pwd-generate {
  margin-top: 8px;
}
</style>
