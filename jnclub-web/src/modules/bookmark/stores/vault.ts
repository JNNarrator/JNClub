import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export interface VaultItem {
  id: number
  directoryId: number
  name: string
  username: string
  /** 列表为空；详情才返回明文 */
  password?: string | null
  url: string
  notes: string
  sortOrder: number
  createTime: string
}

export const useVaultStore = defineStore('vault', () => {
  const items = ref<VaultItem[]>([])
  const loading = ref(false)

  const fetchItems = async (directoryId: number) => {
    loading.value = true
    try {
      const res = await axios.get('/api/vault', { params: { directoryId } })
      if (res.data.code === 200) {
        items.value = res.data.data || []
      }
    } finally {
      loading.value = false
    }
  }

  /** 获取详情（含解密密码） */
  const fetchDetail = async (id: number): Promise<VaultItem> => {
    const res = await axios.get(`/api/vault/${id}`)
    if (res.data.code === 200) return res.data.data
    throw new Error(res.data.message || '获取失败')
  }

  const createItem = async (vault: Partial<VaultItem>): Promise<VaultItem> => {
    const res = await axios.post('/api/vault', vault)
    if (res.data.code === 200) return res.data.data
    throw new Error(res.data.message || '创建失败')
  }

  const updateItem = async (id: number, vault: Partial<VaultItem>) => {
    const res = await axios.put(`/api/vault/${id}`, vault)
    if (res.data.code !== 200) throw new Error(res.data.message || '保存失败')
  }

  const deleteItem = async (id: number) => {
    const res = await axios.delete(`/api/vault/${id}`)
    if (res.data.code !== 200) throw new Error(res.data.message || '删除失败')
  }

  const updateSortOrder = async (sortList: { id: number; sortOrder: number }[]) => {
    const res = await axios.put('/api/vault/sort', sortList)
    if (res.data.code !== 200) throw new Error(res.data.message || '排序失败')
  }

  return {
    items,
    loading,
    fetchItems,
    fetchDetail,
    createItem,
    updateItem,
    deleteItem,
    updateSortOrder,
  }
})
