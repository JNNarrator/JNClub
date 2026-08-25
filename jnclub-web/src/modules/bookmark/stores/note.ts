import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import { fetchRefTagsBatch } from '../composables/tags'

export interface Note {
  id: number
  title: string
  content: string | null
  /** 列表接口返回的摘要，详情接口返回完整 content */
  excerpt?: string | null
  directoryId: number
  userId?: string
  sortOrder: number
  createTime: string
  updateTime: string
  pinned?: number
  archived?: number
  tags?: string[]
}

export const useNoteStore = defineStore('note', () => {
  const notes = ref<Note[]>([])
  const loading = ref(false)
  const currentNote = ref<Note | null>(null)

  const fetchNotes = async (directoryId: number, tagId?: number | null, archived?: boolean) => {
    loading.value = true
    try {
      const res = await axios.get('/api/notes', {
        params: { directoryId, ...(tagId ? { tagId } : {}), ...(archived ? { archived: true } : {}) }
      })
      if (res.data.code === 200) {
        const list = res.data.data || []
        notes.value = list
        // 一次批量请求拉取全部标签，替代逐条 N+1
        const tagMap = await fetchRefTagsBatch('note', list.map((n: Note) => n.id))
        notes.value = list.map((n: Note) => ({
          ...n,
          tags: (tagMap[n.id] || []).map(t => t.name),
        }))
      }
    } finally {
      loading.value = false
    }
  }

  const fetchNoteDetail = async (id: number) => {
    try {
      const res = await axios.get(`/api/notes/${id}`)
      if (res.data.code === 200) {
        currentNote.value = res.data.data
      }
    } catch (e: any) {
      throw new Error(e.response?.data?.message || '获取便签失败')
    }
  }

  const createNote = async (params: { title?: string; directoryId: number }) => {
    const res = await axios.post('/api/notes', {
      title: params.title || '',
      directoryId: params.directoryId,
    })
    if (res.data.code === 200) {
      currentNote.value = res.data.data
      return res.data.data as Note
    }
    throw new Error(res.data.message || '创建失败')
  }

  const updateNote = async (id: number, params: { title?: string; content?: string }) => {
    const res = await axios.put(`/api/notes/${id}`, params)
    if (res.data.code === 200) return
    throw new Error(res.data.message || '保存失败')
  }

  const deleteNote = async (id: number) => {
    const res = await axios.delete(`/api/notes/${id}`)
    if (res.data.code === 200) return
    throw new Error(res.data.message || '删除失败')
  }

  const updateSortOrder = async (sortList: { id: number; sortOrder: number }[]) => {
    const res = await axios.put('/api/notes/sort', sortList)
    if (res.data.code === 200) return
    throw new Error(res.data.message || '排序失败')
  }

  const setPinned = async (id: number, pinned: boolean) => {
    const res = await axios.put(`/api/notes/${id}/pin`, { pinned })
    if (res.data.code !== 200) throw new Error(res.data.message || '操作失败')
  }

  const setArchived = async (id: number, archived: boolean) => {
    const res = await axios.put(`/api/notes/${id}/archive`, { archived })
    if (res.data.code !== 200) throw new Error(res.data.message || '操作失败')
  }

  const fetchVersions = async (id: number): Promise<any[]> => {
    const res = await axios.get(`/api/notes/${id}/versions`)
    if (res.data.code === 200) return res.data.data || []
    throw new Error(res.data.message || '获取版本失败')
  }

  const fetchVersionDetail = async (id: number, versionId: number): Promise<any> => {
    const res = await axios.get(`/api/notes/${id}/versions/${versionId}`)
    if (res.data.code === 200) return res.data.data
    throw new Error(res.data.message || '获取版本详情失败')
  }

  const restoreVersion = async (id: number, versionId: number) => {
    const res = await axios.put(`/api/notes/${id}/restore-version`, { versionId })
    if (res.data.code !== 200) throw new Error(res.data.message || '回滚失败')
  }

  return {
    notes,
    loading,
    currentNote,
    fetchNotes,
    fetchNoteDetail,
    createNote,
    updateNote,
    deleteNote,
    updateSortOrder,
    setPinned,
    setArchived,
    fetchVersions,
    fetchVersionDetail,
    restoreVersion,
  }
})
