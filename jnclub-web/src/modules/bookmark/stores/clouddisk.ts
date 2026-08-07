import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export interface DiskFile {
  id: number
  directoryId: number
  originalName: string
  storedKey: string
  url: string
  size: number
  mime: string
  sortOrder: number
  createTime: string
}

export const useCloudDiskStore = defineStore('clouddisk', () => {
  const files = ref<DiskFile[]>([])
  const loading = ref(false)

  const fetchFiles = async (directoryId: number) => {
    loading.value = true
    try {
      const res = await axios.get('/api/clouddisk/files', { params: { directoryId } })
      if (res.data.code === 200) {
        files.value = res.data.data || []
      }
    } finally {
      loading.value = false
    }
  }

  const updateSortOrder = async (sortList: { id: number; sortOrder: number }[]) => {
    const res = await axios.put('/api/clouddisk/files/sort', sortList)
    if (res.data.code === 200) return
    throw new Error(res.data.message || '排序失败')
  }

  const deleteFile = async (id: number) => {
    const res = await axios.delete(`/api/clouddisk/files/${id}`)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '删除失败')
    }
  }

  const formatSize = (bytes: number) => {
    if (!bytes) return '0 B'
    const units = ['B', 'KB', 'MB', 'GB', 'TB']
    let i = 0
    let n = bytes
    while (n >= 1024 && i < units.length - 1) {
      n /= 1024
      i++
    }
    return `${n.toFixed(n >= 100 || i === 0 ? 0 : 1)} ${units[i]}`
  }

  return {
    files,
    loading,
    fetchFiles,
    updateSortOrder,
    deleteFile,
    formatSize,
  }
})
