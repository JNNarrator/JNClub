import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
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
  const loadingMore = ref(false)
  const totalItems = ref(0)
  const page = ref(1)
  const PAGE_SIZE = 50

  const hasMoreItems = computed(() => items.value.length < totalItems.value)

  /** 主密钥状态：{ configured: 是否已设置, unlocked: 当前是否已解锁 } */
  const masterStatus = ref<{ configured: boolean; unlocked: boolean }>({ configured: false, unlocked: false })

  /** 健康检查结果：弱密码 / 重复密码条目 id → 展示角标（仅提示不拦截） */
  const health = ref<{ weakIds: Set<number>; dupIds: Set<number> }>({ weakIds: new Set(), dupIds: new Set() })

  const fetchItems = async (directoryId: number, opts?: { page?: number; size?: number; append?: boolean }) => {
    const nextPage = opts?.page ?? 1
    const size = opts?.size ?? PAGE_SIZE
    const append = opts?.append ?? false
    if (append) loadingMore.value = true
    else loading.value = true
    try {
      const res = await axios.get('/api/vault', { params: { directoryId, page: nextPage, size } })
      if (res.data.code === 200) {
        const data = res.data.data
        // 兼容旧后端直接返回数组
        if (Array.isArray(data)) {
          totalItems.value = data.length
          items.value = append ? [...items.value, ...data] : data
        } else {
          const list = data?.items || []
          totalItems.value = data?.total ?? 0
          page.value = data?.page ?? nextPage
          items.value = append ? [...items.value, ...list] : list
        }
      }
    } finally {
      loading.value = false
      loadingMore.value = false
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

  // ========== 主密钥管理 ==========

  /** 查询主密钥状态（configured/unlocked） */
  const fetchMasterStatus = async () => {
    const res = await axios.get('/api/vault/master-key/status')
    if (res.data.code === 200) masterStatus.value = res.data.data || { configured: false, unlocked: false }
    return masterStatus.value
  }

  /** 设置/修改主密钥（修改需传旧密钥，全量重加密迁移） */
  const setMasterKey = async (newMasterKey: string, oldMasterKey?: string) => {
    const res = await axios.post('/api/vault/master-key', { newMasterKey, oldMasterKey })
    if (res.data.code !== 200) throw new Error(res.data.message || '设置失败')
    masterStatus.value = { configured: true, unlocked: true }
  }

  /** 解锁 */
  const unlock = async (masterKey: string) => {
    const res = await axios.post('/api/vault/unlock', { masterKey })
    if (res.data.code !== 200) throw new Error(res.data.message || '解锁失败')
    masterStatus.value = { configured: true, unlocked: true }
  }

  /** 锁定 */
  const lock = async () => {
    await axios.post('/api/vault/lock')
    masterStatus.value = { configured: true, unlocked: false }
  }

  /** 遗忘重置（双重确认） */
  const reset = async (resetCode: string) => {
    const res = await axios.post('/api/vault/reset', { confirm: 'RESET', resetCode })
    if (res.data.code !== 200) throw new Error(res.data.message || '重置失败')
    masterStatus.value = { configured: false, unlocked: false }
    items.value = []
    health.value = { weakIds: new Set(), dupIds: new Set() }
  }

  /** 健康检查（需解锁）：拉取弱/重复密码条目 id 集合 */
  const fetchHealth = async () => {
    const res = await axios.get('/api/vault/check-health')
    if (res.data.code === 200) {
      const data = res.data.data || { weak: [], duplicates: [] }
      health.value = {
        weakIds: new Set((data.weak || []).map((w: any) => w.id)),
        dupIds: new Set((data.duplicates || []).map((d: any) => d.id)),
      }
    }
    return health.value
  }

  return {
    items,
    loading,
    loadingMore,
    totalItems,
    page,
    hasMoreItems,
    masterStatus,
    health,
    fetchItems,
    fetchDetail,
    createItem,
    updateItem,
    deleteItem,
    updateSortOrder,
    fetchMasterStatus,
    setMasterKey,
    unlock,
    lock,
    reset,
    fetchHealth,
  }
})
