<script setup lang="ts">
/**
 * CollectionCard.vue — 卡片网格中的单张收藏卡
 * favicon + 标题 + 域名 + 操作入口
 * hover: 抬升阴影 + 边框转品牌色 + translateY(-2px)
 * 标题截断 <n-ellipsis>
 */
import { h, ref } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, useMessage } from 'naive-ui'
import {
  CreateOutline,
  TrashOutline,
  EllipsisVerticalOutline,
  OpenOutline,
} from '@vicons/ionicons5'
import axios from 'axios'
import type { BookmarkItem } from './CollectionRow.vue'

const props = defineProps<{
  bookmark: BookmarkItem
}>()

const emit = defineEmits<{
  refresh: []
}>()

const message = useMessage()
const imgError = ref(false)

const getDomain = (url: string) => {
  try { return new URL(url).hostname } catch { return url }
}

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
  { label: '打开', key: 'open', icon: () => h(NIcon, null, { default: () => h(OpenOutline) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(CreateOutline) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(TrashOutline) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'open') handleOpen()
  else if (key === 'delete') handleDelete()
}
</script>

<template>
  <div class="collection-card" @click="handleOpen">
    <!-- 卡头 -->
    <div class="card-top">
      <div class="card-favicon">
        <img
          v-if="bookmark.icon && !imgError"
          :src="bookmark.icon"
          :alt="bookmark.title"
          class="card-icon"
          @error="imgError = true"
        />
        <div v-else class="card-icon-fallback">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--text-3)" stroke-width="1.5">
            <path d="M12 2L2 7l10 5 10-5-10-5z" />
            <path d="M2 17l10 5 10-5" />
            <path d="M2 12l10 5 10-5" />
          </svg>
        </div>
      </div>

      <!-- 标题 — 截断 + tooltip -->
      <div class="card-title">
        <NEllipsis :tooltip="{ width: 360 }" class="title-link">
          {{ bookmark.title || bookmark.url }}
        </NEllipsis>
      </div>
    </div>

    <!-- 域名 -->
    <div class="card-domain">{{ getDomain(bookmark.url) }}</div>

    <!-- 操作菜单 -->
    <div class="card-actions" @click.stop>
      <NDropdown :options="dropdownOptions" @select="handleDropdown" placement="bottom-end">
        <NButton quaternary circle size="tiny" class="more-btn">
          <template #icon>
            <NIcon :component="EllipsisVerticalOutline" size="16" />
          </template>
        </NButton>
      </NDropdown>
    </div>
  </div>
</template>

<style scoped>
.collection-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--dur) var(--ease);
  gap: 8px;
}

/* hover 抬升阴影 + 边框转品牌色 + 微上移 */
.collection-card:hover {
  transform: translateY(-2px);
  border-color: var(--brand);
  box-shadow: 0 4px 12px rgba(236, 91, 142, 0.08), 0 8px 30px rgba(0,0,0,0.06);
}

.collection-card:active {
  transform: translateY(0);
}

/* 卡头 */
.card-top {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.card-favicon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
}

.card-icon-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-title {
  flex: 1;
  min-width: 0;
}

.title-link {
  color: var(--link) !important;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

/* 域名 */
.card-domain {
  font-size: 12px;
  color: var(--text-3);
  padding-left: 34px; /* 与标题缩进对齐 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作按钮 — hover 才显示 */
.card-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}

.collection-card:hover .card-actions {
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
