import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import { fetchRefTags } from '../composables/tags'

export interface Bookmark {
  id: number
  title: string
  url: string
  icon: string | null
  directoryId: number
  sortOrder: number
  createTime: string
  tags?: string[]
}

export const useBookmarkStore = defineStore('bookmark', () => {
  const bookmarks = ref<Bookmark[]>([])
  const loading = ref(false)

  const fetchBookmarks = async (directoryId: number, tagId?: number | null) => {
    loading.value = true
    try {
      const res = await axios.get('/api/bookmarks', {
        params: tagId ? { directoryId, tagId } : { directoryId }
      })
      if (res.data.code === 200) {
        bookmarks.value = res.data.data || []
        // 并行拉取每条收藏的标签（轻量展示）
        bookmarks.value = await Promise.all(bookmarks.value.map(async (b) => {
          const tags = await fetchRefTags('bookmark', b.id)
          return { ...b, tags: tags.map(t => t.name) }
        }))
      }
    } finally {
      loading.value = false
    }
  }

  const updateSortOrder = async (sortList: { id: number; sortOrder: number }[]) => {
    const res = await axios.put('/api/bookmarks/sort', sortList)
    if (res.data.code === 200) return
    throw new Error(res.data.message || '排序失败')
  }

  return {
    bookmarks,
    loading,
    fetchBookmarks,
    updateSortOrder,
  }
})
