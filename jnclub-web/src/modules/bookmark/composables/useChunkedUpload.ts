import { ref, computed } from 'vue'
import axios from 'axios'

/**
 * 分片上传组合式函数 — 断点续传核心
 *
 * 设计（针对小带宽/不稳定网络）：
 * - 前端按 chunkSize 切片，逐片上传；单片失败自动指数退避重试，最大 retryCount 次。
 * - 每次开始前调用 status 查询服务端已落盘分片（以云端为准，非本地缓存），跳过已传分片。
 * - 上传暂停时保留 uploadId 与已传进度，恢复时继续。
 * - complete 由服务端校验完整性并合并推送 dufs，成功后清理。
 */

export interface ChunkUploadState {
  uploadId: string
  file: File
  chunkSize: number
  totalChunks: number
  totalSize: number
  /** 已成功上传的分片 index（按服务端确认） */
  uploadedChunks: Set<number>
  /** 当前正在上传的分片 index */
  currentChunk: number | null
  progress: number
  status: 'idle' | 'uploading' | 'paused' | 'completed' | 'error'
  error: string
}

const DEFAULT_CHUNK_SIZE_MB = 2
const MAX_RETRY = 3
/** 指数退避基础延迟（毫秒） */
const BASE_RETRY_DELAY = 1000

export function useChunkedUpload() {
  const state = ref<ChunkUploadState | null>(null)

  const progressPercent = computed(() =>
    state.value ? Math.round(state.value.progress * 100) : 0,
  )

  const isUploading = computed(() => state.value?.status === 'uploading')

  /**
   * 初始化上传：先调 init 获取 uploadId，再查 status 跳过已传分片（支持续传）。
   */
  const startUpload = async (file: File, directoryId: number, chunkSizeMb = DEFAULT_CHUNK_SIZE_MB) => {
    if (state.value && (state.value.status === 'uploading')) {
      throw new Error('已有文件正在上传，请等待完成')
    }
    const initRes = await axios.post('/api/clouddisk/upload/init', {
      filename: file.name,
      size: file.size,
      directoryId,
      chunkSizeMb,
    })
    if (initRes.data.code !== 200) {
      throw new Error(initRes.data.message || '初始化上传失败')
    }
    const init = initRes.data.data

    const st: ChunkUploadState = {
      uploadId: init.uploadId,
      file,
      chunkSize: init.chunkSize * 1024 * 1024,
      totalChunks: init.totalChunks,
      totalSize: init.totalSize,
      uploadedChunks: new Set(),
      currentChunk: null,
      progress: 0,
      status: 'uploading',
      error: '',
    }
    state.value = st

    // 查询云端已传分片（断点续传：刷新/中断后跳过已完成部分）
    try {
      const statusRes = await axios.get('/api/clouddisk/upload/status', {
        params: { uploadId: st.uploadId },
      })
      if (statusRes.data.code === 200) {
        const uploaded = statusRes.data.data?.uploaded || []
        uploaded.forEach((i: number) => st.uploadedChunks.add(Number(i)))
      }
    } catch { /* 查询失败按无进度处理 */ }

    updateProgress(st)
    await run(st)
  }

  /** 逐个上传未完成分片，单片失败重试 */
  const run = async (st: ChunkUploadState) => {
    st.status = 'uploading'
    for (let i = 0; i < st.totalChunks; i++) {
      // 通过函数读取状态，避免 TS 对对象属性做字面量收窄（pause 可能在异步中修改）
      if (currentStatus(st) === 'paused') return
      if (st.uploadedChunks.has(i)) continue

      st.currentChunk = i
      const slice = st.file.slice(i * st.chunkSize, Math.min((i + 1) * st.chunkSize, st.totalSize))
      await uploadWithRetry(st, i, slice)
      st.uploadedChunks.add(i)
      updateProgress(st)
    }

    if (currentStatus(st) === 'paused') return
    try {
      const completeRes = await axios.post('/api/clouddisk/upload/complete', { uploadId: st.uploadId })
      if (completeRes.data.code !== 200) {
        st.status = 'error'
        st.error = completeRes.data.message || '合并失败，请重新上传'
        return
      }
      st.status = 'completed'
    } catch (e: any) {
      st.status = 'error'
      st.error = e.response?.data?.message || '合并失败，请重试'
    }
  }

  const uploadWithRetry = async (st: ChunkUploadState, index: number, slice: Blob) => {
    for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
      if (st.status === 'paused') return
      try {
        const form = new FormData()
        form.append('uploadId', st.uploadId)
        form.append('chunkIndex', String(index))
        form.append('file', slice, `chunk-${index}`)
        const res = await axios.post('/api/clouddisk/upload/chunk', form)
        if (res.data.code === 200) return
        throw new Error(res.data.message || '分片上传失败')
      } catch (e: any) {
        if (attempt === MAX_RETRY) {
          st.status = 'error'
          st.error = `分片 ${index + 1}/${st.totalChunks} 上传失败：${e.response?.data?.message || e.message}`
          throw e
        }
        await sleep(BASE_RETRY_DELAY * 2 ** attempt)
      }
    }
  }

  const updateProgress = (st: ChunkUploadState) => {
    const done = st.uploadedChunks.size
    st.progress = st.totalChunks ? done / st.totalChunks : 0
  }

  /** 暂停：停止发送后续分片，进度保留，可稍后恢复 */
  const pause = () => {
    if (state.value?.status === 'uploading') {
      state.value.status = 'paused'
    }
  }

  /** 恢复：继续未完成分片 */
  const resume = async () => {
    if (!state.value || state.value.status !== 'paused') return
    await run(state.value)
  }

  const reset = () => {
    state.value = null
  }

  const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

  /** 读取状态（绕开 TS 对属性字面量的收窄） */
  const currentStatus = (st: ChunkUploadState): ChunkUploadState['status'] => st.status

  return {
    state,
    progressPercent,
    isUploading,
    startUpload,
    pause,
    resume,
    reset,
  }
}
