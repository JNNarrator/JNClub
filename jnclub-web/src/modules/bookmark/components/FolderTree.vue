<script setup lang="ts">
/**
 * FolderTree.vue — 目录树组件
 * 同级拖拽排序（HTML5 DnD） | 预设 icon 选择与展示 | hover ... 菜单（重命名+删除）
 * 删除前 content-count 预检（有内容禁止删除）；创建带 type 参数
 */
import { ref, computed, h } from 'vue'
import { NTree, NButton, NIcon, NModal, NForm, NFormItem, NInput, NSpace, useMessage, useDialog } from 'naive-ui'
import type { TreeOption, TreeDropInfo } from 'naive-ui'
import { Plus, FolderOpen, Folder, Bookmark, Star, Heart, BookOpen, Tag, Archive, Pencil, Trash2, Ellipsis } from 'lucide-vue-next'
import { useDirectoryStore } from '../stores/directory'
import { openMenu } from '../../../shared/composables/useContextMenu'
import { useItemDragContext } from '../composables/useItemDragContext'
import axios from 'axios'

interface Directory {
  id: number
  parentId: number | null
  name: string
  icon?: string | null
  type?: number
  sortOrder: number
  children?: Directory[]
}

/** 预设目录图标 */
const ICON_OPTIONS = [
  { key: 'folder', icon: Folder, label: '文件夹' },
  { key: 'folderOpen', icon: FolderOpen, label: '打开' },
  { key: 'bookmark', icon: Bookmark, label: '收藏' },
  { key: 'star', icon: Star, label: '星标' },
  { key: 'heart', icon: Heart, label: '爱心' },
  { key: 'book', icon: BookOpen, label: '书籍' },
  { key: 'tag', icon: Tag, label: '标签' },
  { key: 'archive', icon: Archive, label: '归档' },
]
const iconMap: Record<string, any> = Object.fromEntries(ICON_OPTIONS.map(o => [o.key, o.icon]))

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
const directoryStore = useDirectoryStore()

const showCreateModal = ref(false)
const createName = ref('')
const createIcon = ref('')
const creating = ref(false)

const showRenameModal = ref(false)
const renameId = ref<number | null>(null)
const renameName = ref('')
const renameIcon = ref('')
const renaming = ref(false)

const deleting = ref(false)
const reordering = ref(false)

/** item 拖拽到目录树的落点（item 卡片/行 → 目录树跨容器） */
const { dragging, setDragging } = useItemDragContext()
/** 当前高亮的目标节点元素（dragenter/dragover 时置位，dragleave/drop 清除） */
const dropNodeEl = ref<HTMLElement | null>(null)
const dropBusy = ref(false)

/** 模块 key → move 接口前缀（与 MoveItemModal 一致） */
const ITEM_MOVE_API: Record<string, string> = {
  bookmarks: '/api/bookmarks',
  notes: '/api/notes',
  files: '/api/clouddisk/files',
  vault: '/api/vault',
}

/** 从鼠标位置解析目标目录节点（renderLabel 里给 label 注入了 data-dir-id） */
const findDropNode = (e: DragEvent): HTMLElement | null => {
  if (!dragging.value) return null
  const el = document.elementFromPoint(e.clientX, e.clientY)
  if (!el) return null
  return (el as HTMLElement).closest('[data-dir-id]') as HTMLElement | null
}

const onItemDragOver = (e: DragEvent) => {
  if (!dragging.value) return
  e.preventDefault() // 允许 drop
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  const label = findDropNode(e)
  const node = (label?.closest('.n-tree-node') as HTMLElement | null) ?? null
  if (dropNodeEl.value === node) return
  dropNodeEl.value?.classList.remove('item-drop-target')
  dropNodeEl.value = node
  node?.classList.add('item-drop-target')
}

const onItemDragLeave = () => {
  dropNodeEl.value?.classList.remove('item-drop-target')
  dropNodeEl.value = null
}

/** item 拖到目录节点上松手：直接移动（拖拽落定动画见样式） */
const onItemDrop = async (e: DragEvent) => {
  const payload = dragging.value
  e.preventDefault()
  onItemDragLeave()
  if (!payload) return
  // 先解析落点节点（findDropNode 依赖 dragging 上下文），再清上下文
  const node = findDropNode(e)
  setDragging(null)
  if (!node) return
  const targetDirId = Number(node.dataset.dirId)
  if (!targetDirId || isNaN(targetDirId)) return
  if (payload.currentDirectoryId === targetDirId) {
    message.info('已在该目录中')
    return
  }
  const api = ITEM_MOVE_API[payload.module]
  if (!api) return
  // 落定回弹动画：先播再等接口返回（视觉反馈优先）
  const treeNode = (node.closest('.n-tree-node') as HTMLElement | null) ?? node
  treeNode.classList.add('move-target-pulse')
  treeNode.addEventListener('animationend', () => treeNode.classList.remove('move-target-pulse'), { once: true })
  if (dropBusy.value) return
  dropBusy.value = true
  try {
    await axios.put(`${api}/${payload.itemId}/move`, { directoryId: targetDirId })
    message.success('已移动')
    emit('refresh')
  } catch (err: any) {
    message.error(err.response?.data?.message || '移动失败')
  } finally {
    dropBusy.value = false
  }
}

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
      prefix: () => h(NIcon, { component: iconMap[dir.icon || 'folder'] || FolderOpen, size: 16, style: { color: 'var(--brand)' } }),
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

const handleContextMenuAction = (dirId: number, key: string) => {
  if (key === 'rename') {
    const dir = findDir(props.directories, dirId)
    if (dir) {
      renameId.value = dirId
      renameName.value = dir.name
      renameIcon.value = dir.icon || ''
      showRenameModal.value = true
    }
  } else if (key === 'delete') {
    const dir = findDir(props.directories, dirId)
    dialog.warning({
      title: '确认删除',
      content: `确定要删除"${dir?.name}"及其所有子目录吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        if (deleting.value) return
        deleting.value = true
        try {
          // 删除保护预检：有内容（含子目录）禁止删除
          const res = await axios.get(`/api/directories/${dirId}/content-count`)
          const { bookmarkCount, noteCount } = res.data.data || {}
          if ((bookmarkCount || 0) > 0 || (noteCount || 0) > 0) {
            message.warning('目录下存在条目，请先清空或移动后再删除')
            return
          }
          await axios.delete(`/api/directories/${dirId}`)
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

/** 同级拖拽排序：drop 后重新计算同级 sortOrder 并提交 */
const handleDrop = async ({ node, dragNode, dropPosition }: TreeDropInfo) => {
  if (!node || !dragNode) return
  const dragId = dragNode.key as number
  const targetId = node.key as number
  if (dragId === targetId) return
  if (dropPosition === 'inside') {
    message.info('暂不支持移动到目录内，仅支持同级调整顺序')
    return
  }
  if (reordering.value) return
  reordering.value = true
  try {
    const siblings = findSiblings(props.directories, dragId, targetId)
    if (!siblings || siblings.length < 2) return
    const keys = siblings.map(s => s.id)
    const from = keys.indexOf(dragId)
    const to = keys.indexOf(targetId)
    if (from === -1 || to === -1) return
    keys.splice(from, 1)
    // 删除后若 from < to，目标索引需回退 1（off-by-one 修复）
    const adjustedTo = from < to ? to - 1 : to
    const insertAt = dropPosition === 'before' ? adjustedTo : adjustedTo + 1
    keys.splice(Math.min(insertAt, keys.length), 0, dragId)
    const sortList = keys.map((id, idx) => ({ id, sortOrder: idx }))
    await directoryStore.updateSortOrder(sortList)
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '排序失败')
  } finally { reordering.value = false }
}

/** 递归查找同时包含两个节点的兄弟层级 */
const findSiblings = (dirs: Directory[], a: number, b: number): Directory[] | null => {
  if (dirs.some(d => d.id === a) && dirs.some(d => d.id === b)) return dirs
  for (const d of dirs) {
    if (d.children?.length) {
      const found = findSiblings(d.children, a, b)
      if (found) return found
    }
  }
  return null
}

/* each row: hover shows ... menu + 右键打开同一菜单 */
const renderLabel = ({ option }: { option: TreeOption }) => {
  const node = option as any
  const openNodeMenu = (e: MouseEvent) => {
    openMenu(e, contextMenuOptions, (key: string) => handleContextMenuAction(node.id, key))
  }
  return h('span', {
    style: 'display: flex; align-items: center; justify-content: space-between; width: 100%;',
    onContextmenu: openNodeMenu,
  }, [
    h('span', {
      'data-dir-id': String(node.id),
      style: 'overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; font-size: var(--fs-base);',
    }, node.name),
    h(NButton, {
      quaternary: true, circle: true, size: 'tiny',
      style: 'opacity: 0; flex-shrink: 0; margin-left: 4px;',
      class: 'node-menu-btn',
      onClick: (e: MouseEvent) => {
        e.stopPropagation()
        openNodeMenu(e)
      },
    }, { default: () => h(NIcon, { component: Ellipsis, size: 14 }) }),
  ])
}

const handleCreateSubmit = async () => {
  if (!createName.value.trim()) { message.warning('请输入名称'); return }
  if (creating.value) return
  creating.value = true
  try {
    await axios.post('/api/directories', { name: createName.value.trim(), parentId: null, type: props.type || 1, icon: createIcon.value || null })
    message.success('创建成功')
    showCreateModal.value = false
    createName.value = ''
    createIcon.value = ''
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
    await axios.put(`/api/directories/${renameId.value}`, { name: renameName.value.trim(), icon: renameIcon.value })
    message.success('重命名成功')
    showRenameModal.value = false
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '重命名失败')
  } finally { renaming.value = false }
}
</script>

<template>
  <div
    class="folder-tree"
    @dragenter="onItemDragOver"
    @dragover="onItemDragOver"
    @dragleave="onItemDragLeave"
    @drop="onItemDrop"
  >
    <div class="tree-toolbar">
      <button type="button" class="add-dir-btn" @click="showCreateModal = true">
        <NIcon :component="Plus" size="16" />
        <span>新建目录</span>
      </button>
    </div>

    <NTree
      :data="treeData"
      :selected-keys="selectedId ? [selectedId] : []"
      selectable default-expand-all block-line draggable
      :render-switcher-icon="renderSwitcherIcon"
      :render-label="renderLabel"
      @update:selected-keys="handleSelect"
      @drop="handleDrop"
      class="folder-n-tree"
    />

    <!-- 新建目录 -->
    <NModal v-model:show="showCreateModal" preset="dialog" title="新建目录">
      <NForm style="margin-top: 16px;">
        <NFormItem label="名称">
          <NInput v-model:value="createName" placeholder="请输入目录名称" clearable @keyup.enter="handleCreateSubmit" />
        </NFormItem>
        <NFormItem label="图标">
          <div class="icon-picker">
            <button
              v-for="opt in ICON_OPTIONS"
              :key="opt.key"
              type="button"
              :class="['icon-opt', { active: createIcon === opt.key }]"
              :title="opt.label"
              @click="createIcon = opt.key"
            >
              <NIcon :component="opt.icon" size="18" />
            </button>
            <button type="button" :class="['icon-opt', { active: createIcon === '' }]" title="默认" @click="createIcon = ''">
              <NIcon :component="FolderOpen" size="18" />
            </button>
          </div>
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace><NButton @click="showCreateModal = false">取消</NButton><NButton type="primary" :loading="creating" @click="handleCreateSubmit">确定</NButton></NSpace>
      </template>
    </NModal>

    <!-- 重命名目录 -->
    <NModal v-model:show="showRenameModal" preset="dialog" title="重命名目录">
      <NForm style="margin-top: 16px;">
        <NFormItem label="名称">
          <NInput v-model:value="renameName" placeholder="请输入新名称" clearable @keyup.enter="handleRenameSubmit" />
        </NFormItem>
        <NFormItem label="图标">
          <div class="icon-picker">
            <button
              v-for="opt in ICON_OPTIONS"
              :key="opt.key"
              type="button"
              :class="['icon-opt', { active: renameIcon === opt.key }]"
              :title="opt.label"
              @click="renameIcon = opt.key"
            >
              <NIcon :component="opt.icon" size="18" />
            </button>
            <button type="button" :class="['icon-opt', { active: renameIcon === '' }]" title="默认" @click="renameIcon = ''">
              <NIcon :component="FolderOpen" size="18" />
            </button>
          </div>
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

/* 统一的新建目录入口（通栏虚线卡片） */
.tree-toolbar { flex-shrink: 0; padding: 0 0 10px; }
.add-dir-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border: 1px dashed var(--glass-chip-border);
  border-radius: var(--radius-sm);
  background: var(--glass-chip-bg);
  color: var(--text-2);
  font-size: var(--fs-md);
  cursor: pointer;
  transition: all .18s ease;
}
.add-dir-btn:hover {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-soft);
}

/* 图标选择器 */
.icon-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.icon-opt {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: all .15s ease;
}
.icon-opt:hover {
  color: var(--brand);
  border-color: var(--brand);
}
.icon-opt.active {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-soft);
}

:deep(.n-tree-node--selected) { background: var(--brand-soft) !important; border-radius: var(--radius-sm); }
:deep(.n-tree-node) { border-radius: var(--radius-sm); transition: background var(--dur) var(--ease); }
:deep(.n-tree-node:hover) { background: var(--glass-chip-bg); }
:deep(.n-tree-node:hover .node-menu-btn) { opacity: 1 !important; }
:deep(.n-tree-node--selected .node-menu-btn) { opacity: 1 !important; }

/* 拖拽排序视觉优化 */
:deep(.n-tree-node-content--drag-over) {
  background: var(--brand-soft) !important;
  box-shadow: inset 0 0 0 2px var(--brand);
  border-radius: var(--radius-sm);
}
:deep(.n-tree-node-content--dragging) {
  opacity: 0.5;
}
:deep(.n-tree-switcher--dragging) { opacity: 0.5; }
:deep(.n-tree-node--dragging) { opacity: 0.5; }

/* item 拖拽落点：目录节点高亮（品牌粉 + 描边 + 轻微放大） */
:deep(.n-tree-node.item-drop-target) {
  background: var(--brand-soft) !important;
  box-shadow: inset 0 0 0 2px var(--brand);
  border-radius: var(--radius-sm);
  transform: scale(1.03);
  transition: background var(--dur) var(--ease), box-shadow var(--dur) var(--ease),
    transform var(--dur) var(--ease-bouncy);
}

/* item 落定回弹（参照 jnclub-dropSettle 的 bouncy 节奏） */
:deep(.n-tree-node.move-target-pulse) {
  animation: move-target-pulse 0.32s var(--ease-bouncy);
}
@keyframes move-target-pulse {
  0% { transform: scale(1); }
  40% { transform: scale(1.08); }
  100% { transform: scale(1); }
}
</style>
