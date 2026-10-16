<script setup lang="ts">
/**
 * CollectionRow.vue — 极简列表行（向 R 学习）
 * favicon + 标题（链接蓝） + 右侧"相对时间 + 浏览数"
 * 行 hover 浅底、整行可点
 * 提供操作入口 <n-dropdown>
 */
import { h, ref } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag, useMessage, NCheckbox } from 'naive-ui'
import { Pencil, Trash2, Ellipsis, Clock, ExternalLink, FolderInput, BookOpen } from 'lucide-vue-next'
import { openMenu, openMenuAt } from '../../../shared/composables/useContextMenu'
import { useLongPress } from '../../../shared/composables/useLongPress'
import MoveItemModal from './MoveItemModal.vue'
import axios from 'axios'
import { formatRelativeTime } from '../../../shared/utils/formatDate'
import { useRecentItems } from '../../../shared/composables/useRecentItems'

export interface BookmarkItem {
  id: number
  title: string
  url: string
  icon: string | null
  directoryId: number
  sortOrder: number
  createTime: string
  tags?: string[]
}

const props = defineProps<{
  bookmark: BookmarkItem
  batchMode?: boolean
  selected?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
  read: [bookmark: BookmarkItem]
  'toggle-select': []
}>()

const message = useMessage()
const longPress = useLongPress()
const { record: recordRecentItem } = useRecentItems()
const showMoveModal = ref(false)

const handleOpen = () => {
  window.open(props.bookmark.url, '_blank')
  // 打开收藏 → 记入「最近打开」
  recordRecentItem({ kind: 'bookmark', id: props.bookmark.id, title: props.bookmark.title || props.bookmark.url, url: props.bookmark.url })
}
const onRootClick = () => {
  if (props.batchMode) emit('toggle-select')
  else handleOpen()
}


const handleDelete = async () => {
  try {
    await axios.delete(`/api/bookmarks/${props.bookmark.id}`)
    message.success('删除成功')
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

const dropdownOptions = [
  { label: '打开', key: 'open', icon: () => h(NIcon, null, { default: () => h(ExternalLink) }) },
  { label: '阅读模式', key: 'read', icon: () => h(NIcon, null, { default: () => h(BookOpen) }) },
  { label: '移动到…', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'open') handleOpen()
  else if (key === 'read') emit('read', props.bookmark)
  else if (key === 'move') showMoveModal.value = true
  else if (key === 'edit') emit('edit', props.bookmark)
  else if (key === 'delete') handleDelete()
}
</script>

<template>
  <div
    :class="['collection-row jnclub-longpress', { 'is-selected': props.selected }]"
    role="link"
    tabindex="0"
    @click="onRootClick"
    @keydown.enter.prevent="onRootClick"
    @keydown.space.prevent="onRootClick"
    @contextmenu.prevent="openMenu($event, dropdownOptions, handleDropdown)"
    v-on="longPress((ev) => openMenuAt(ev.clientX, ev.clientY, dropdownOptions, handleDropdown))"
  >
      <div v-if="props.batchMode" class="batch-check" @click.stop="emit('toggle-select')">
        <NCheckbox :checked="props.selected" @update:checked="emit('toggle-select')" size="small" />
      </div>
    <!-- favicon -->
    <div class="row-favicon">
      <img
        v-if="bookmark.icon"
        :src="bookmark.icon"
        :alt="bookmark.title"
        class="favicon-img"
        loading="lazy"
        decoding="async"
        @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')"
      />
      <div v-else class="favicon-placeholder">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--text-3)" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5z" />
          <path d="M2 17l10 5 10-5" />
          <path d="M2 12l10 5 10-5" />
        </svg>
      </div>
    </div>

    <!-- 标题（链接蓝） -->
    <div class="row-title">
      <NEllipsis :tooltip="{ width: 400 }" class="title-link">
        {{ bookmark.title || bookmark.url }}
      </NEllipsis>
      <div v-if="bookmark.tags?.length" class="row-tags">
        <NTag v-for="t in bookmark.tags" :key="t" size="tiny" round :bordered="false" class="row-tag">
          {{ t }}
        </NTag>
      </div>
    </div>

    <!-- 右端信息 -->
    <div class="row-meta">
      <span class="meta-time">
        <NIcon :component="Clock" size="12" />
        {{ formatRelativeTime(bookmark.createTime) }}
      </span>
    </div>

    <!-- 操作菜单 -->
    <div class="row-actions" @click.stop>
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
      :item-type="1"
      :targets="[{ id: bookmark.id, name: bookmark.title }]"
      :current-directory-id="bookmark.directoryId ?? null"
      @refresh="emit('refresh')"
    />
  </div>
</template>

<style scoped>
/* 列表行统一规范：与 .note-row / .file-item / .vault-item / .recycle-item 同档
   （内边距 12px 14px、--bg-card 底、--border 框、radius-sm） */
.collection-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease);
}

/* hover 不再叠 inset 3px 左条：那条品牌竖条是 SideNav 表示「当前所在位置」的持久符号，
   列表行 hover 只是瞬时悬停，共用同一符号会让行读起来像「已选中」。
   改用「边框转品牌色 + 浮起阴影」，与 DiskView 的 .file-item 一致。 */
.collection-row:hover {
  border-color: var(--brand-text);
  box-shadow: var(--shadow-card-hover);
}

.collection-row:active {
  background: var(--brand-soft);
  border-color: var(--brand-text);
}

/* Favicon */
.row-favicon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.favicon-img {
  width: 16px;
  height: 16px;
  border-radius: var(--radius-xs);
}

.favicon-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 标题区 — 内容色（与卡片视图标题一致） */
.row-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

/* NEllipsis 把 class 透传到 naive-ui 内部元素，该元素不携带本组件 scope id，
   普通 scoped 选择器会静默失效（原 .title-link 规则的 font-weight/color 从未生效）。
   必须用 :deep() 穿透，命中 .row-title[data-v-*] 的后代。 */
:deep(.title-link) {
  color: var(--text-1) !important;
  font-size: var(--fs-base);
  font-weight: 600;
}

/* 行内标签 */
.row-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.row-tag {
  background: var(--brand-soft) !important;
  color: var(--brand-text) !important;
}

/* 元信息 */
.row-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  color: var(--text-3);
  font-size: var(--fs-sm);
}

.meta-time {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

/* 操作按钮 */
.row-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}

.collection-row:hover .row-actions,
.collection-row:focus-within .row-actions {
  opacity: 1;
}

@media (hover: none) {
  .row-actions { opacity: 1; }
}

.more-btn {
  color: var(--text-3);
  transition: color var(--dur) var(--ease);
}

.more-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}
</style>
<style scoped></style>
