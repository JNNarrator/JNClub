<script setup lang="ts">
/**
 * FolderTree.vue — 目录树组件
 * 叶子无三角 | hover 显示 ... 菜单（重命名+删除）
 * 创建时带 type 参数
 */
import { ref, computed, h } from 'vue'
import { NTree, NButton, NIcon, NDropdown, NModal, NForm, NFormItem, NInput, NSpace, useMessage, useDialog } from 'naive-ui'
import { Plus, FolderOpen, Pencil, Trash2, Ellipsis } from 'lucide-vue-next'
import axios from 'axios'
import type { TreeOption } from 'naive-ui'

interface Directory {
  id: number
  parentId: number | null
  name: string
  type?: number
  sortOrder: number
  children?: Directory[]
}

const props = defineProps<{
  directories: Directory[]
  selectedId: number | null
  type?: number
}>()

const emit = defineEmits<{
  select: [id: number]
  refresh: []
}>()

const message = useMessage()
const dialog = useDialog()

const showCreateModal = ref(false)
const createName = ref('')
const creating = ref(false)

const showRenameModal = ref(false)
const renameId = ref<number | null>(null)
const renameName = ref('')
const renaming = ref(false)

const deleting = ref(false)

const contextMenuDirId = ref<number | null>(null)
const showContextMenu = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)

const treeData = computed((): TreeOption[] => {
  const map = new Map<number, TreeOption & { id: number; isLeaf: boolean; name: string }>()
  const roots: (TreeOption & { id: number; isLeaf: boolean; name: string })[] = []

  props.directories.forEach(dir => {
    map.set(dir.id, {
      key: dir.id,
      id: dir.id,
      name: dir.name,
      label: dir.name,
      isLeaf: !dir.children || dir.children.length === 0,
      prefix: () => h(NIcon, { component: FolderOpen, size: 16, style: { color: 'var(--brand)' } }),
      children: dir.children && dir.children.length > 0 ? [] as TreeOption[] : undefined,
    })
  })

  props.directories.forEach(dir => {
    const node = map.get(dir.id)!
    if (dir.parentId === null) {
      roots.push(node)
    } else {
      const parent = map.get(dir.parentId)
      if (parent && parent.children) {
        parent.children.push(node)
      }
    }
  })

  return roots
})

const renderSwitcherIcon = (opt: TreeOption) => {
  if ((opt as any).isLeaf) return h('span', { style: 'display: inline-block; width: 16px;' })
  return undefined
}

const handleSelect = (keys: Array<string | number>) => {
  if (keys.length > 0) emit('select', keys[0] as number)
}

const contextMenuOptions = [
  { label: '重命名', key: 'rename', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleContextMenuAction = (key: string) => {
  const id = contextMenuDirId.value
  if (!id) return
  if (key === 'rename') {
    const dir = findDir(props.directories, id)
    if (dir) {
      renameId.value = id
      renameName.value = dir.name
      showRenameModal.value = true
    }
  } else if (key === 'delete') {
    const dir = findDir(props.directories, id)
    dialog.warning({
      title: '确认删除',
      content: `确定要删除"${dir?.name}"及其所有子目录吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        if (deleting.value) return
        deleting.value = true
        try {
          await axios.delete(`/api/directories/${id}`)
          message.success('删除成功')
          emit('refresh')
        } catch (e: any) {
          message.error(e.response?.data?.message || '删除失败')
        } finally { deleting.value = false }
      },
    })
  }
}

const findDir = (dirs: Directory[], id: number): Directory | null => {
  for (const d of dirs) {
    if (d.id === id) return d
    if (d.children) {
      const found = findDir(d.children, id)
      if (found) return found
    }
  }
  return null
}

/* each row: hover shows ... menu */
const renderLabel = ({ option }: { option: TreeOption }) => {
  const node = option as any
  return h('span', { style: 'display: flex; align-items: center; justify-content: space-between; width: 100%;' }, [
    h('span', { style: 'overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; font-size: 14px;' }, node.name),
    h(NButton, {
      quaternary: true, circle: true, size: 'tiny',
      style: 'opacity: 0; flex-shrink: 0; margin-left: 4px;',
      class: 'node-menu-btn',
      onClick: (e: MouseEvent) => {
        e.stopPropagation()
        contextMenuDirId.value = node.id
        showContextMenu.value = true
        contextMenuX.value = e.clientX
        contextMenuY.value = e.clientY
      },
    }, { default: () => h(NIcon, { component: Ellipsis, size: 14 }) }),
  ])
}

const handleCreateSubmit = async () => {
  if (!createName.value.trim()) { message.warning('请输入名称'); return }
  if (creating.value) return
  creating.value = true
  try {
    await axios.post('/api/directories', { name: createName.value.trim(), parentId: null, type: props.type || 1 })
    message.success('创建成功')
    showCreateModal.value = false
    createName.value = ''
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '创建失败')
  } finally { creating.value = false }
}

const handleRenameSubmit = async () => {
  if (!renameName.value.trim() || renameId.value === null) { message.warning('请输入名称'); return }
  if (renaming.value) return
  renaming.value = true
  try {
    await axios.put(`/api/directories/${renameId.value}`, { name: renameName.value.trim() })
    message.success('重命名成功')
    showRenameModal.value = false
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '重命名失败')
  } finally { renaming.value = false }
}
</script>

<template>
  <div class="folder-tree">
    <div class="tree-toolbar">
      <NButton size="small" ghost @click="showCreateModal = true" class="add-btn">
        <template #icon><NIcon :component="Plus" size="16" /></template>
        新建目录
      </NButton>
    </div>

    <NTree
      :data="treeData"
      :selected-keys="selectedId ? [selectedId] : []"
      selectable default-expand-all block-line
      :render-switcher-icon="renderSwitcherIcon"
      :render-label="renderLabel"
      @update:selected-keys="handleSelect"
      class="folder-n-tree"
    />

    <NDropdown
      v-if="contextMenuDirId"
      :options="contextMenuOptions"
      :show="showContextMenu"
      :x="contextMenuX" :y="contextMenuY"
      placement="bottom-start"
      @clickoutside="showContextMenu = false"
      @select="handleContextMenuAction"
    />

    <NModal v-model:show="showCreateModal" preset="dialog" title="新建目录">
      <NForm style="margin-top: 16px;">
        <NFormItem label="名称">
          <NInput v-model:value="createName" placeholder="请输入目录名称" clearable @keyup.enter="handleCreateSubmit" />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace><NButton @click="showCreateModal = false">取消</NButton><NButton type="primary" :loading="creating" @click="handleCreateSubmit">确定</NButton></NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showRenameModal" preset="dialog" title="重命名目录">
      <NForm style="margin-top: 16px;">
        <NFormItem label="名称">
          <NInput v-model:value="renameName" placeholder="请输入新名称" clearable @keyup.enter="handleRenameSubmit" />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace><NButton @click="showRenameModal = false">取消</NButton><NButton type="primary" :loading="renaming" @click="handleRenameSubmit">确定</NButton></NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.folder-tree { display: flex; flex-direction: column; height: 100%; }
.tree-toolbar { flex-shrink: 0; padding: 0 0 8px; }
.add-btn { width: 100%; justify-content: flex-start; }
:deep(.n-tree-node--selected) { background: var(--brand-soft) !important; border-radius: var(--radius-sm); }
:deep(.n-tree-node) { border-radius: var(--radius-sm); transition: background var(--dur) var(--ease); }
:deep(.n-tree-node:hover) { background: var(--hover-bg); }
:deep(.n-tree-node:hover .node-menu-btn) { opacity: 1 !important; }
:deep(.n-tree-node--selected .node-menu-btn) { opacity: 1 !important; }
</style>
