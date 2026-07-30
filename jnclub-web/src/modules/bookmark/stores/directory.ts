import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface Directory {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  userId: string
  createTime: string
  updateTime: string
  children?: Directory[]
}

export const useDirectoryStore = defineStore('directory', () => {
  const directories = ref<Directory[]>([])
  const loading = ref(false)

  const fetchDirectories = async () => {
    loading.value = true
    try {
      const res = await axios.get('/api/directories')
      if (res.data.code === 200) {
        directories.value = res.data.data || []
      }
    } finally {
      loading.value = false
    }
  }

  return {
    directories,
    loading,
    fetchDirectories,
  }
})
