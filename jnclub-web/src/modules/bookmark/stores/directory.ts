import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export interface Directory {
  id: number
  parentId: number | null
  name: string
  icon?: string | null
  type?: number
  sortOrder: number
  userId: string
  createTime: string
  updateTime: string
  children?: Directory[]
}

export const useDirectoryStore = defineStore('directory', () => {
  const directories = ref<Directory[]>([])
  const loading = ref(false)

  const fetchDirectories = async (type?: number) => {
    loading.value = true
    try {
      const params: any = {}
      if (type !== undefined) params.type = type
      const res = await axios.get('/api/directories', { params })
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
