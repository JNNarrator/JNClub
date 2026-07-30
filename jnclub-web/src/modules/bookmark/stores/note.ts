import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export interface Note {
  id: number
  title: string
  content: string | null
  directoryId: number
  userId?: string
  sortOrder: number
  createTime: string
  updateTime: string
}

export const useNoteStore = defineStore('note', () => {
  const notes = ref<Note[]>([])
  const loading = ref(false)
  const currentNote = ref<Note | null>(null)

  const fetchNotes = async (directoryId: number) => {
    loading.value = true
    try {
      const res = await axios.get('/api/notes', { params: { directoryId } })
      if (res.data.code === 200) {
        notes.value = res.data.data || []
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
  }
})
