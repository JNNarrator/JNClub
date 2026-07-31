<script setup lang="ts">
/**
 * NoteCard.vue — 便签卡片
 * 标题 + 摘要 + 更新时间 + 操作菜单
 * hover: 抬升阴影 + 边框转品牌粉 + translateY(-2px)
 */
import { h } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis } from 'naive-ui'
import {
  CreateOutline, TrashOutline, EyeOutline,
  EllipsisVerticalOutline, DocumentTextOutline,
  TimeOutline,
} from '@vicons/ionicons5'
import { formatDate } from '../composables/formatDate'
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
  const plain = content.replace(/[#*`\[\]()!>_~\-\n]/g, ' ').replace(/\s+/g, ' ').trim()
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
  <div class="note-card" @click="handleClick">
    <!-- 卡头 -->
    <div class="card-top">
      <div class="card-icon-wrap">
        <NIcon :component="DocumentTextOutline" size="20" color="var(--text-3)" />
      </div>
      <div class="card-title">
        <NEllipsis :tooltip="{ width: 360 }">
          {{ note.title || '无标题' }}
        </NEllipsis>
      </div>
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

    <!-- 操作菜单 — hover 显示 -->
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
.note-card {
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
.note-card:hover {
  transform: translateY(-2px);
  border-color: var(--brand);
  box-shadow: 0 4px 12px rgba(236, 91, 142, 0.08), 0 8px 30px rgba(0,0,0,0.06);
}
.note-card:active { transform: translateY(0); }

/* 卡头 */
.card-top {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.card-icon-wrap {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1); /* 非链接蓝 */
  line-height: 1.4;
}

/* 摘要 */
.card-summary {
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 底部 */
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

/* 操作 — hover 显示 */
.card-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}
.note-card:hover .card-actions { opacity: 1; }
.more-btn { color: var(--text-3); }
.more-btn:hover { color: var(--text-1); background: var(--hover-bg); }
</style>
