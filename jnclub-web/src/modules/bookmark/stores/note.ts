import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

interface Note {
  id: number
  title: string
  content: string | null
  directoryId: number
  sortOrder: number
  createTime: string
  updateTime: string
}

export const useNoteStore = defineStore('note', () => {
  const notes = ref<Note[]>([])
  const loading = ref(false)

  const fetchNotes = async (directoryId: number) => {
    loading.value = true
    try {
      const res = await axios.get('/api/notes', {
        params: { directoryId }
      })
      if (res.data.code === 200) {
        notes.value = res.data.data || []
      }
    } finally {
      loading.value = false
    }
  }

  return {
    notes,
    loading,
    fetchNotes,
  }
})
