<script setup lang="ts">
/**
 * NoteCard.vue — 便签卡片（氛围升级版）
 * 顶部渐变装饰条 + 图标底盒 + 摘要 + 时间
 * hover: 卡片抬升 + 品牌粉阴影
 */
import { h } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis } from 'naive-ui'
import {
  CreateOutline, TrashOutline, EyeOutline,
  EllipsisVerticalOutline, DocumentTextOutline,
  TimeOutline,
} from '@vicons/ionicons5'
import { formatDate } from '../composables/formatDate'
import { stripMarkdown } from '../composables/stripMarkdown'
import type { Note } from '../stores/note'

const props = defineProps<{
  note: Note
}>()

const emit = defineEmits<{
  preview: [note: Note]
  edit: [note: Note]
  delete: [note: Note]
  refresh: []
}>()

const getSummary = (content: string | null) => {
  if (!content) return '暂无内容'
  const plain = stripMarkdown(content)
  if (!plain) return '暂无内容'
  return plain.length > 100 ? plain.substring(0, 100) + '…' : plain
}

const dropdownOptions = [
  { label: '预览', key: 'preview', icon: () => h(NIcon, null, { default: () => h(EyeOutline) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(CreateOutline) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(TrashOutline) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'preview') emit('preview', props.note)
  else if (key === 'edit') emit('edit', props.note)
  else if (key === 'delete') emit('delete', props.note)
}

const handleClick = () => {
  emit('preview', props.note)
}
</script>

<template>
  <div class="note-card jnclub-bouncy" @click="handleClick">
    <!-- 顶部渐变装饰条 -->
    <div class="card-top-bar"></div>

    <div class="card-body">
      <div class="card-head">
        <!-- 图标底盒 -->
        <div class="icon-box">
          <NIcon :component="DocumentTextOutline" size="20" color="var(--brand)" />
        </div>

        <!-- 操作菜单 hover 出现 -->
        <div class="card-actions" @click.stop>
          <NDropdown :options="dropdownOptions" @select="handleDropdown" placement="bottom-end">
            <NButton quaternary circle size="tiny" class="more-btn">
              <template #icon>
                <NIcon :component="EllipsisVerticalOutline" size="15" />
              </template>
            </NButton>
          </NDropdown>
        </div>
      </div>

      <!-- 标题 -->
      <div class="card-title">
        <NEllipsis :tooltip="{ width: 360 }">
          <span class="title-text">{{ note.title || '无标题' }}</span>
        </NEllipsis>
      </div>

      <!-- 摘要 -->
      <div class="card-summary">{{ getSummary(note.content) }}</div>

      <!-- 底部：时间 -->
      <div class="card-footer">
        <span class="card-time">
          <NIcon :component="TimeOutline" size="13" />
          {{ formatDate(note.updateTime || note.createTime) }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.note-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  overflow: hidden;
  box-shadow: var(--shadow-1);
}

.note-card:hover {
  transform: translateY(-2px);
  border-color: var(--pink-peach);
  box-shadow: var(--shadow-card-hover);
}

.note-card:active {
  transform: translateY(0);
}

/* === 卡片主体 === */
.card-body {
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

/* === 头部 === */
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

/* 图标底盒 */
.icon-box {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--pink-cherry);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--glow-icon);
  flex-shrink: 0;
}

/* === 标题 === */
.card-title {
  min-width: 0;
}

.title-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.4;
}

/* === 摘要 === */
.card-summary {
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* === 底部 === */
.card-footer {
  display: flex;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}

.card-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
}

/* === 操作 hover 显示 === */
.card-actions {
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}

.note-card:hover .card-actions {
  opacity: 1;
}

.more-btn {
  color: var(--text-3);
}

.more-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}
</style>
