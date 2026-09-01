import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export interface WebDavServer { id: number; name: string; url: string; username: string; password?: string | null; notes: string }
export interface WebDavEntry { name: string; path: string; isDir: boolean; size: number; modified: string }

export function normalizeEntry(e: any): WebDavEntry {
  const isDir = !!(e?.dir ?? e?.isDir)
  return { name: e?.name ?? '', path: e?.path ?? '', isDir, size: e?.size ?? 0, modified: e?.modified ?? '' }
}

export const useWebDavStore = defineStore('webdav', () => {
  const servers = ref<WebDavServer[]>([])
  const loadingServers = ref(false)
  const activeServerId = ref<number | null>(null)
  const currentPath = ref('')
  const entries = ref<WebDavEntry[]>([])
  const loadingEntries = ref(false)

  const resetEntries = () => { currentPath.value = ''; entries.value = []; loadingEntries.value = false }

  async function fetchServers() {
    loadingServers.value = true
    try {
      const res = await axios.get('/api/webdav/servers')
      if (res.data.code === 200) {
        servers.value = res.data.data || []
        if (activeServerId.value && !servers.value.some(s => s.id === activeServerId.value)) {
          activeServerId.value = null
          resetEntries()
        }
      }
    } finally { loadingServers.value = false }
  }

  async function createServer(server: Partial<WebDavServer>): Promise<WebDavServer> {
    const res = await axios.post('/api/webdav/servers', server)
    if (res.data.code === 200) return res.data.data
    throw new Error(res.data.message || '创建失败')
  }

  async function updateServer(server: Partial<WebDavServer>) {
    const res = await axios.put(`/api/webdav/servers/${server.id}`, server)
    if (res.data.code !== 200) throw new Error(res.data.message || '保存失败')
  }

  async function deleteServer(id: number) {
    const res = await axios.delete(`/api/webdav/servers/${id}`)
    if (res.data.code !== 200) throw new Error(res.data.message || '删除失败')
  }

  async function testServer(id: number) {
    const res = await axios.post(`/api/webdav/servers/${id}/test`)
    if (res.data.code !== 200) throw new Error(res.data.message || '连接失败')
    return res.data.data
  }

  // ========== 文件管理 ==========

  function selectServer(id: number | null) {
    activeServerId.value = id
    resetEntries()
  }

  async function listDir(path = '') {
    if (!activeServerId.value) return
    loadingEntries.value = true
    try {
      currentPath.value = path || ''
      const res = await axios.get(`/api/webdav/servers/${activeServerId.value}/list`, { params: { path: path || '' } })
      if (res.data.code === 200) {
        entries.value = (res.data.data || []).map(normalizeEntry)
      }
    } finally { loadingEntries.value = false }
  }

  async function mkdir(name: string) {
    if (!activeServerId.value) return
    const path = joinPath(currentPath.value, name)
    const res = await axios.post(`/api/webdav/servers/${activeServerId.value}/mkdir`, { path })
    if (res.data.code !== 200) throw new Error(res.data.message || '新建文件夹失败')
  }

  async function uploadFiles(files: File[]) {
    if (!activeServerId.value) return
    for (const f of files) {
      const form = new FormData()
      form.append('file', f)
      const res = await axios.post(`/api/webdav/servers/${activeServerId.value}/upload`, form, {
        params: { path: currentPath.value },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (res.data.code !== 200) throw new Error(res.data.message || '上传失败')
    }
  }

  function joinPath(dir: string, name: string) {
    const d = dir || ''
    const n = (name || '').replace(/^\//, '')
    return d ? `${d}/${n}` : n
  }

  async function remove(path: string, isDir: boolean) {
    if (!activeServerId.value) return
    const res = await axios.delete(`/api/webdav/servers/${activeServerId.value}/delete`, { params: { path, isDir } })
    if (res.data.code !== 200) throw new Error(res.data.message || '删除失败')
  }

  async function rename(path: string, newName: string) {
    if (!activeServerId.value) return
    const res = await axios.put(`/api/webdav/servers/${activeServerId.value}/rename`, { path, newName })
    if (res.data.code !== 200) throw new Error(res.data.message || '重命名失败')
  }

  return {
    servers, loadingServers, activeServerId, currentPath, entries, loadingEntries,
    fetchServers, createServer, updateServer, deleteServer, testServer,
    selectServer, listDir, mkdir, uploadFiles, remove, rename,
  }
})