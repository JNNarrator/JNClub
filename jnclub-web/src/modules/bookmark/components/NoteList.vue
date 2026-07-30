<script setup lang="ts">
import { h } from 'vue'
import { NCard, NList, NListItem, NButton, NIcon, NEmpty, NDropdown, useMessage, useDialog } from 'naive-ui'
import { CreateOutline, TrashOutline, EllipsisVerticalOutline, TimeOutline } from '@vicons/ionicons5'
import axios from 'axios'

interface Note {
  id: number
  title: string
  content: string | null
  directoryId: number
  sortOrder: number
  createTime: string
  updateTime: string
}

defineProps<{
  notes: Note[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const message = useMessage()
const dialog = useDialog()

const getSummary = (content: string | null) => {
  if (!content) return '暂无内容'
  // 移除 Markdown 语法，取前 100 个字符
  const plainText = content.replace(/[#*`\[\]()!>_~\-]/g, '').trim()
  return plainText.length > 100 ? plainText.substring(0, 100) + '...' : plainText
}

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const handleDelete = (id: number, title: string) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除便签"${title}"吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete(`/api/notes/${id}`)
        message.success('删除成功')
        emit('refresh')
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    },
  })
}

const getDropdownOptions = (_note: Note) => [
  {
    label: '编辑',
    key: 'edit',
    icon: () => h(NIcon, null, { default: () => h(CreateOutline) }),
  },
  {
    label: '删除',
    key: 'delete',
    icon: () => h(NIcon, null, { default: () => h(TrashOutline) }),
  },
]

const handleDropdown = (key: string, note: Note) => {
  if (key === 'delete') {
    handleDelete(note.id, note.title)
  }
}
</script>

<template>
  <div class="note-list">
    <template v-if="notes.length === 0">
      <NEmpty description="暂无便签" />
    </template>
    <template v-else>
      <NList hoverable clickable>
        <NListItem v-for="note in notes" :key="note.id">
          <NCard size="small" style="margin-bottom: 8px;">
            <template #header>
              <div style="display: flex; align-items: center; justify-content: space-between;">
                <span>{{ note.title }}</span>
                <NDropdown :options="getDropdownOptions(note)" @select="(key) => handleDropdown(key, note)">
                  <NButton quaternary circle size="small" @click.stop>
                    <template #icon>
                      <NIcon :component="EllipsisVerticalOutline" />
                    </template>
                  </NButton>
                </NDropdown>
              </div>
            </template>
            <div style="color: var(--text-color-2); font-size: 13px; margin-bottom: 8px;">
              {{ getSummary(note.content) }}
            </div>
            <div style="display: flex; align-items: center; gap: 4px; color: var(--text-color-3); font-size: 12px;">
              <NIcon :component="TimeOutline" size="14" />
              <span>{{ formatDate(note.updateTime) }}</span>
            </div>
          </NCard>
        </NListItem>
      </NList>
    </template>
  </div>
</template>

<style scoped>
.note-list {
  min-height: 200px;
}
</style>
