<script setup lang="ts">
/**
 * CollectionRow.vue — 极简列表行（向 R 学习）
 * favicon + 标题（链接蓝） + 右侧"相对时间 + 浏览数"
 * 行 hover 浅底、整行可点
 * 提供操作入口 <n-dropdown>
 */
import { h } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag, useMessage } from 'naive-ui'
import { Pencil, Trash2, Ellipsis, Clock, Eye } from 'lucide-vue-next'
import axios from 'axios'
import { formatRelativeTime } from '../composables/formatDate'

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
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
}>()

const message = useMessage()

const handleOpen = () => {
  window.open(props.bookmark.url, '_blank')
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
  { label: '打开', key: 'open', icon: () => h(NIcon, null, { default: () => h(Ellipsis) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'open') handleOpen()
  else if (key === 'edit') emit('edit', props.bookmark)
  else if (key === 'delete') handleDelete()
}
</script>

<template>
  <div class="collection-row" @click="handleOpen">
    <!-- favicon -->
    <div class="row-favicon">
      <img
        v-if="bookmark.icon"
        :src="bookmark.icon"
        :alt="bookmark.title"
        class="favicon-img"
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
      <span class="meta-views">
        <NIcon :component="Eye" size="12" />
        0
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
  </div>
</template>

<style scoped>
.collection-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease);
}

.collection-row:hover {
  background: var(--hover-bg);
}

.collection-row:active {
  background: var(--brand-soft);
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
  border-radius: 2px;
}

.favicon-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 标题区 — 链接蓝 */
.row-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.title-link {
  color: var(--link) !important;
  font-size: 14px;
  font-weight: 500;
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
  color: var(--brand) !important;
}

/* 元信息 */
.row-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  color: var(--text-3);
  font-size: 12px;
}

.meta-time,
.meta-views {
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

.collection-row:hover .row-actions {
  opacity: 1;
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
