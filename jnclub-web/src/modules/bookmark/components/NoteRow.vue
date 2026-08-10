<script setup lang="ts">
/**
 * NoteRow.vue — 便签极简列表行
 * 文档图标 + 标题(--text-1, 非链接蓝) + 摘要 + 更新时间 + 操作菜单
 * hover 浅底，整行可点=进入预览
 */
import { h } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag } from 'naive-ui'
import { Pencil, Trash2, Eye, Ellipsis, StickyNote, Clock } from 'lucide-vue-next'
import { formatRelativeTime } from '../composables/formatDate'
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
  return plain.length > 80 ? plain.substring(0, 80) + '…' : plain
}

const dropdownOptions = [
  { label: '预览', key: 'preview', icon: () => h(NIcon, null, { default: () => h(Eye) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
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
  <div class="note-row" @click="handleClick">
    <!-- 文档图标 -->
    <div class="row-icon">
      <NIcon :component="StickyNote" size="18" color="var(--text-3)" />
    </div>

    <!-- 标题 + 摘要 -->
    <div class="row-main">
      <div class="row-title">
        <NEllipsis :tooltip="{ width: 400 }">
          {{ note.title || '无标题' }}
        </NEllipsis>
      </div>
      <div class="row-summary">{{ getSummary(note.content) }}</div>
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
.note-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease);
}
.note-row:hover { background: var(--hover-bg); }

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
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1); /* 非链接蓝 */
}
.row-summary {
  font-size: 12px;
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
  font-size: 12px;
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
.note-row:hover .row-actions { opacity: 1; }
.more-btn {
  color: var(--text-3);
  transition: color var(--dur) var(--ease);
}
.more-btn:hover { color: var(--text-1); background: var(--hover-bg); }
</style>
