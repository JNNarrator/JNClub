import { defineStore } from 'pinia'
import { ref } from 'vue'
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

export const useBookmarkStore = defineStore('bookmark', () => {
  const bookmarks = ref<Bookmark[]>([])
  const loading = ref(false)

  const fetchBookmarks = async (directoryId: number) => {
    loading.value = true
    try {
      const res = await axios.get('/api/bookmarks', {
        params: { directoryId }
      })
      if (res.data.code === 200) {
        bookmarks.value = res.data.data || []
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
