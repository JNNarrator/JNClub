import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
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
  const loadingMore = ref(false)
  const totalFiles = ref(0)
  const page = ref(1)
  const PAGE_SIZE = 50

  const hasMoreFiles = computed(() => files.value.length < totalFiles.value)

  const fetchFiles = async (directoryId: number, opts?: { page?: number; size?: number; append?: boolean }) => {
    const nextPage = opts?.page ?? 1
    const size = opts?.size ?? PAGE_SIZE
    const append = opts?.append ?? false
    if (append) loadingMore.value = true
    else loading.value = true
    try {
      const res = await axios.get('/api/clouddisk/files', { params: { directoryId, page: nextPage, size } })
      if (res.data.code === 200) {
        const data = res.data.data
        // 兼容旧后端直接返回数组
        if (Array.isArray(data)) {
          totalFiles.value = data.length
          files.value = append ? [...files.value, ...data] : data
        } else {
          const items = data?.items || []
          totalFiles.value = data?.total ?? 0
          page.value = data?.page ?? nextPage
          files.value = append ? [...files.value, ...items] : items
        }
      }
    } finally {
      loading.value = false
      loadingMore.value = false
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
    loadingMore,
    totalFiles,
    page,
    hasMoreFiles,
    fetchFiles,
    updateSortOrder,
    deleteFile,
    formatSize,
  }
})
