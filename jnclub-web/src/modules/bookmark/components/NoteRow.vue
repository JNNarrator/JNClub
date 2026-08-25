<script setup lang="ts">
/**
 * NoteRow.vue — 便签极简列表行
 * 文档图标 + 标题(--text-1, 非链接蓝) + 摘要 + 更新时间 + 操作菜单
 * hover 浅底，整行可点=进入预览
 */
import { computed, h, ref } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag, NCheckbox, useMessage } from 'naive-ui'
import { Pencil, Trash2, Eye, Ellipsis, StickyNote, Clock, FolderInput, Link2, Share2, Pin, PinOff, Archive } from 'lucide-vue-next'
import { formatRelativeTime } from '../composables/formatDate'
import { stripMarkdown } from '../composables/stripMarkdown'
import { openMenu } from '../../../shared/composables/useContextMenu'
import ShareModal from './ShareModal.vue'
import { useItemDragContext } from '../composables/useItemDragContext'
import MoveItemModal from './MoveItemModal.vue'
import { useNoteStore, type Note } from '../stores/note'

const props = defineProps<{
  note: Note
  batchMode?: boolean
  selected?: boolean
}>()

const emit = defineEmits<{
  preview: [note: Note]
  edit: [note: Note]
  delete: [note: Note]
  'toggle-select': []
  refresh: []
}>()

const message = useMessage()
const noteStore = useNoteStore()

const showMoveModal = ref(false)
const showShare = ref(false)
const { setDragging } = useItemDragContext()

const getSummary = (note: Note) => {
  // 列表接口已返回纯文本 excerpt；兼容旧数据/详情场景回退到完整 content
  const content = note.excerpt ?? note.content
  if (!content) return '暂无内容'
  const plain = stripMarkdown(content)
  if (!plain) return '暂无内容'
  return plain.length > 80 ? plain.substring(0, 80) + '…' : plain
}

const dropdownOptions = computed(() => {
  const isPinned = (props.note.pinned ?? 0) === 1
  return [
    { label: '预览', key: 'preview', icon: () => h(NIcon, null, { default: () => h(Eye) }) },
    { label: isPinned ? '取消置顶' : '置顶', key: 'pin', icon: () => h(NIcon, null, { default: () => h(isPinned ? PinOff : Pin) }) },
    { label: '移动到…', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
    { label: '分享', key: 'share', icon: () => h(NIcon, null, { default: () => h(Link2) }) },
    { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
    { label: '归档', key: 'archive', icon: () => h(NIcon, null, { default: () => h(Archive) }) },
    { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
  ]
})

const handleDropdown = async (key: string) => {
  if (key === 'preview') emit('preview', props.note)
  else if (key === 'move') showMoveModal.value = true
  else if (key === 'share') showShare.value = true
  else if (key === 'edit') emit('edit', props.note)
  else if (key === 'pin') {
    try {
      await noteStore.setPinned(props.note.id, (props.note.pinned ?? 0) !== 1)
      message.success((props.note.pinned ?? 0) !== 1 ? '已置顶' : '已取消置顶')
      emit('refresh')
    } catch (e: any) { message.error(e.message || '操作失败') }
  }
  else if (key === 'archive') {
    try {
      await noteStore.setArchived(props.note.id, true)
      message.success('已归档')
      emit('refresh')
    } catch (e: any) { message.error(e.message || '操作失败') }
  }
  else if (key === 'delete') emit('delete', props.note)
}

const handleClick = () => {
  emit('preview', props.note)
}
const onRootClick = () => {
  if (props.batchMode) emit('toggle-select')
  else handleClick()
}


/** 拖拽到目录树：写入跨容器上下文 */
const handleDragStart = (e: DragEvent) => {
  setDragging({
    itemId: props.note.id,
    module: 'notes',
    currentDirectoryId: props.note.directoryId ?? null,
  })
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    try { e.dataTransfer.setData('text/plain', String(props.note.id)) } catch { /* 忽略 */ }
  }
}

const handleDragEnd = () => setDragging(null)
</script>

<template>
  <div
    class="note-row"
    role="link"
    tabindex="0"
    draggable="true"
    @click="onRootClick"
    @keydown.enter.prevent="onRootClick"
    @keydown.space.prevent="onRootClick"
    @dragstart="handleDragStart"
    @dragend="handleDragEnd"
    @contextmenu.prevent="openMenu($event, dropdownOptions, handleDropdown)"
  >
      <div v-if="props.batchMode" class="batch-check" @click.stop="emit('toggle-select')">
        <NCheckbox :checked="props.selected" @update:checked="emit('toggle-select')" size="small" />
      </div>
    <!-- 文档图标 -->
    <div class="row-icon">
      <NIcon :component="StickyNote" size="18" color="var(--text-3)" />
    </div>

    <!-- 标题 + 摘要 -->
    <div class="row-main">
      <div class="row-title">
        <span v-if="(note.pinned ?? 0) === 1" class="pin-badge" title="已置顶">
          <NIcon :component="Pin" size="12" />
        </span>
        <NEllipsis :tooltip="{ width: 400 }">
          {{ note.title || '无标题' }}
        </NEllipsis>
      </div>
      <div class="row-summary">{{ getSummary(note) }}</div>
      <div v-if="note.tags?.length" class="row-tags">
        <NTag v-for="t in note.tags" :key="t" size="tiny" round :bordered="false" class="row-tag">
          {{ t }}
        </NTag>
      </div>
    </div>

    <!-- 右端信息 -->
    <div class="row-meta">
      <span class="meta-time">
        <NIcon :component="Clock" size="12" />
        {{ formatRelativeTime(note.updateTime || note.createTime) }}
      </span>
    </div>

    <!-- 操作菜单 -->
    <div class="row-actions" @click.stop>
      <NButton quaternary circle size="tiny" class="more-btn" title="分享" @click="showShare = true">
        <template #icon><NIcon :component="Share2" size="16" /></template>
      </NButton>
      <NDropdown :options="dropdownOptions" @select="handleDropdown" placement="bottom-end">
        <NButton quaternary circle size="tiny" class="more-btn">
          <template #icon>
            <NIcon :component="Ellipsis" size="16" />
          </template>
        </NButton>
      </NDropdown>
    </div>

    <!-- 移动到目录弹窗 -->
    <MoveItemModal
      v-model:show="showMoveModal"
      :item-type="2"
      :targets="[{ id: note.id, name: note.title }]"
      :current-directory-id="note.directoryId ?? null"
      @refresh="emit('refresh')"
    />

    <ShareModal
      :show="showShare"
      ref-type="note"
      :ref-id="note.id"
      :name="note.title || '未命名便签'"
      @update:show="(v: boolean) => (showShare = v)"
    />
  </div>
</template>

<style scoped>
.note-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.note-row:hover {
  background: var(--glass-chip-bg);
  box-shadow: inset 3px 0 0 var(--brand);
}

/* 图标 */
.row-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}

/* 主内容 */
.row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.row-title {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1); /* 非链接蓝 */
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.pin-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 5px;
  background: var(--brand-soft);
  color: var(--brand);
  flex-shrink: 0;
}
.row-summary {
  font-size: var(--fs-sm);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 行内标签 */
.row-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.row-tag {
  background: var(--brand-soft) !important;
  color: var(--brand) !important;
}

/* 元信息 */
.row-meta {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-3);
  font-size: var(--fs-sm);
  white-space: nowrap;
}
.meta-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 操作按钮 — hover 显示 */
.row-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}
.note-row:hover .row-actions,
.note-row:focus-within .row-actions { opacity: 1; }
@media (hover: none) {
  .row-actions { opacity: 1; }
}
.more-btn {
  color: var(--text-3);
  transition: color var(--dur) var(--ease);
}
.more-btn:hover { color: var(--text-1); background: var(--hover-bg); }
</style>
<style scoped>
.batch-check {
  position: absolute;
  top: 10px; left: 10px;
  z-index: 5;
  background: rgba(0,0,0,0.35);
  border-radius: 6px;
  padding: 2px;
}
</style>
