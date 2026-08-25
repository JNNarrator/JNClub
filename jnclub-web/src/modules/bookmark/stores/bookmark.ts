import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import { fetchRefTagsBatch } from '../composables/tags'

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
        const list = res.data.data || []
        bookmarks.value = list
        // 一次批量请求拉取全部标签，替代逐条 N+1
        const tagMap = await fetchRefTagsBatch('bookmark', list.map((b: Bookmark) => b.id))
        bookmarks.value = list.map((b: Bookmark) => ({
          ...b,
          tags: (tagMap[b.id] || []).map(t => t.name),
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
