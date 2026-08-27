import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../utils/api'
import type { Track } from './player'

export type PlaylistItem = {
  id: number
  name: string
  trackCount: number
}

export type PlaylistDetail = {
  id: number
  name: string
  tracks: Track[]
}

/**
 * 歌单 store：列表 / 详情 / 面板状态，CRUD 与曲目操作。
 */
export const usePlaylistsStore = defineStore('playlists', () => {
  const playlists = ref<PlaylistItem[]>([])
  const loading = ref(false)
  const panelOpen = ref(false)
  const activeId = ref<number | null>(null)
  const detail = ref<PlaylistDetail | null>(null)
  const detailLoading = ref(false)

  async function fetchPlaylists(): Promise<PlaylistItem[]> {
    loading.value = true
    const res = await api<PlaylistItem[]>('/api/v1/playlists')
    loading.value = false
    if (res.success) {
      playlists.value = res.data || []
    }
    return playlists.value
  }

  async function createPlaylist(name: string): Promise<PlaylistItem | null> {
    const res = await api<PlaylistItem>('/api/v1/playlists', {
      method: 'POST',
      body: JSON.stringify({ name }),
    })
    if (!res.success) throw new Error(res.error?.message || '创建歌单失败')
    await fetchPlaylists()
    return res.data || null
  }

  async function renamePlaylist(id: number, name: string): Promise<void> {
    const res = await api(`/api/v1/playlists/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ name }),
    })
    if (!res.success) throw new Error(res.error?.message || '重命名失败')
    await fetchPlaylists()
    if (activeId.value === id) await openDetail(id)
  }

  async function removePlaylist(id: number): Promise<void> {
    const res = await api(`/api/v1/playlists/${id}`, { method: 'DELETE' })
    if (!res.success) throw new Error(res.error?.message || '删除歌单失败')
    if (activeId.value === id) {
      activeId.value = null
      detail.value = null
    }
    await fetchPlaylists()
  }

  async function openDetail(id: number): Promise<PlaylistDetail | null> {
    activeId.value = id
    detailLoading.value = true
    const res = await api<PlaylistDetail>(`/api/v1/playlists/${id}/tracks`)
    detailLoading.value = false
    if (res.success) {
      detail.value = res.data || null
    }
    return detail.value
  }

  async function addTrack(playlistId: number, trackId: string): Promise<void> {
    const res = await api(`/api/v1/playlists/${playlistId}/tracks`, {
      method: 'POST',
      body: JSON.stringify({ trackId }),
    })
    if (!res.success) throw new Error(res.error?.message || '添加失败')
    if (activeId.value === playlistId) await openDetail(playlistId)
    await fetchPlaylists()
  }

  async function removeTrack(playlistId: number, trackId: string): Promise<void> {
    const res = await api(`/api/v1/playlists/${playlistId}/tracks/${trackId}`, {
      method: 'DELETE',
    })
    if (!res.success) throw new Error(res.error?.message || '移除失败')
    if (activeId.value === playlistId) await openDetail(playlistId)
    await fetchPlaylists()
  }

  function openPanel() {
    panelOpen.value = true
    if (!playlists.value.length) fetchPlaylists()
  }

  function closePanel() {
    panelOpen.value = false
    activeId.value = null
    detail.value = null
  }

  return {
    playlists,
    loading,
    panelOpen,
    activeId,
    detail,
    detailLoading,
    fetchPlaylists,
    createPlaylist,
    renamePlaylist,
    removePlaylist,
    openDetail,
    addTrack,
    removeTrack,
    openPanel,
    closePanel,
  }
})