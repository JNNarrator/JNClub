<script setup lang="ts">
import { h } from 'vue'
import { NCard, NGrid, NGi, NButton, NIcon, NEmpty, NDropdown, useMessage, useDialog } from 'naive-ui'
import { CreateOutline, TrashOutline, EllipsisVerticalOutline } from '@vicons/ionicons5'
import axios from 'axios'

interface Bookmark {
  id: number
  title: string
  url: string
  icon: string | null
  directoryId: number
  sortOrder: number
  createTime: string
}

defineProps<{
  bookmarks: Bookmark[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const message = useMessage()
const dialog = useDialog()

const getDomain = (url: string) => {
  try {
    return new URL(url).hostname
  } catch {
    return url
  }
}

const handleOpen = (url: string) => {
  window.open(url, '_blank')
}

const handleDelete = (id: number, title: string) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除收藏"${title}"吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete(`/api/bookmarks/${id}`)
        message.success('删除成功')
        emit('refresh')
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    },
  })
}

const getDropdownOptions = (_bookmark: Bookmark) => [
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

const handleDropdown = (key: string, bookmark: Bookmark) => {
  if (key === 'delete') {
    handleDelete(bookmark.id, bookmark.title)
  }
}
</script>

<template>
  <div class="bookmark-grid">
    <template v-if="bookmarks.length === 0">
      <NEmpty description="暂无收藏" />
    </template>
    <template v-else>
      <NGrid :x-gap="16" :y-gap="16" :cols="3">
        <NGi v-for="bookmark in bookmarks" :key="bookmark.id">
          <NCard
            hoverable
            style="cursor: pointer;"
            @click="handleOpen(bookmark.url)"
          >
            <template #header>
              <div style="display: flex; align-items: center; gap: 8px;">
                <img
                  v-if="bookmark.icon"
                  :src="bookmark.icon"
                  :alt="bookmark.title"
                  style="width: 20px; height: 20px; border-radius: 4px;"
                />
                <span>{{ bookmark.title }}</span>
              </div>
            </template>
            <template #header-extra>
              <NDropdown :options="getDropdownOptions(bookmark)" @select="(key) => handleDropdown(key, bookmark)">
                <NButton quaternary circle size="small" @click.stop>
                  <template #icon>
                    <NIcon :component="EllipsisVerticalOutline" />
                  </template>
                </NButton>
              </NDropdown>
            </template>
            <div style="color: var(--text-color-2); font-size: 12px;">
              {{ getDomain(bookmark.url) }}
            </div>
          </NCard>
        </NGi>
      </NGrid>
    </template>
  </div>
</template>

<style scoped>
.bookmark-grid {
  min-height: 200px;
}
</style>
