import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { parseLrc, findCurrentLine, getLineProgress, fetchLyricsCached } from '../utils/lrc'
import { usePlayerStore } from '../stores/player'

/**
 * useLyrics —— 歌词内核（PLAN-2026-19 M3-5：消除 LyricsPanel 与 PlayerPage 的双实现）
 *
 * 此前两处各写了一份几乎逐字相同的歌词逻辑：拉取 + 解析 + 当前行 + 行内进度 +
 * 50ms 节流 + 居中滚动，差异只有「什么时候跑 rAF」和「要不要滚动」。
 * 抽成 composable 后，差异由 active / scroll 两个 getter 表达。
 *
 * @param active    rAF 跟随循环是否运行（60fps 更新当前行与行内进度）
 * @param scroll    active 为真时，当前行变化是否平滑滚动到居中
 * @param autoLoad  是否在挂载/切歌时立即加载。传 false 表示「只在 active 时才加载」
 *                  （底部抽屉场景：没打开就不该发请求）
 */
export interface UseLyricsOptions {
  active: () => boolean
  scroll?: () => boolean
  autoLoad?: boolean
}

export function useLyrics(options: UseLyricsOptions) {
  const player = usePlayerStore()

  const rawLyrics = ref('')
  const loading = ref(false)
  const error = ref('')

  const lines = computed(() => parseLrc(rawLyrics.value))
  const hasTimedLyrics = computed(() => lines.value.length > 0)

  const currentLineIdx = ref(-1)
  const lineProgress = ref(0)

  const containerRef = ref<HTMLElement | null>(null)
  const lineRefs = ref<HTMLElement[]>([])
  function setLineRef(el: HTMLElement | null, idx: number) {
    if (el) lineRefs.value[idx] = el
  }

  async function load(trackId: string) {
    if (!trackId) return
    loading.value = true
    error.value = ''
    rawLyrics.value = ''
    currentLineIdx.value = -1
    lineProgress.value = 0
    try {
      const { raw, error: err } = await fetchLyricsCached(trackId)
      rawLyrics.value = raw
      error.value = err || ''
    } catch {
      error.value = '歌词加载失败'
    } finally {
      loading.value = false
    }
  }

  /** 重试当前曲目的歌词加载 */
  function retry() {
    const id = player.currentTrack?.trackId
    if (id) load(id)
  }

  // 切歌自动重新加载
  watch(() => player.currentTrack?.trackId, (id) => {
    if (!id) return
    if (options.autoLoad !== false || options.active()) load(id)
  }, { immediate: true })

  // autoLoad=false 时，首次进入 active 也要加载（抽屉打开那一刻）
  if (options.autoLoad === false) {
    watch(() => options.active(), (on) => {
      const id = player.currentTrack?.trackId
      if (on && id) load(id)
    })
  }

  /** 滚到当前行并居中 */
  function scrollToLine(idx: number) {
    nextTick(() => {
      const container = containerRef.value
      const el = lineRefs.value[idx]
      if (container && el) {
        const scrollTo = el.offsetTop - container.clientHeight / 2 + el.offsetHeight / 2
        container.scrollTo({ top: scrollTo, behavior: 'smooth' })
      }
    })
  }

  let rafId = 0
  let lastProgressUpdate = 0
  const PROGRESS_THROTTLE = 50

  /**
   * 计算当前歌词行与行内进度。
   * @param allowScroll 当前行变化时是否同步滚动容器
   */
  function updateLineState(now: number, allowScroll: boolean) {
    const list = lines.value
    if (!list.length) return
    const idx = findCurrentLine(list, player.currentTime)
    if (idx !== currentLineIdx.value) {
      currentLineIdx.value = idx
      lineProgress.value = getLineProgress(list, idx, player.currentTime)
      lastProgressUpdate = now
      if (allowScroll) scrollToLine(idx)
    } else if (now - lastProgressUpdate > PROGRESS_THROTTLE) {
      lineProgress.value = getLineProgress(list, idx, player.currentTime)
      lastProgressUpdate = now
    }
  }

  function rafLoop() {
    updateLineState(performance.now(), options.scroll ? options.scroll() : true)
    rafId = requestAnimationFrame(rafLoop)
  }

  function startRaf() {
    // 幂等：避免重复启动导致多个 rAF 链并行
    if (rafId) return
    rafId = requestAnimationFrame(rafLoop)
  }

  function stopRaf() {
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = 0
    }
  }

  watch(() => options.active(), (on) => { if (on) startRaf(); else stopRaf() }, { immediate: true })

  // rAF 停时（如播放页关闭）仍要维持「当前行」，供 document.title 等使用。
  // currentTime 由 audio 的 timeupdate 事件驱动（约 4Hz），足够；不滚动。
  watch(() => player.currentTime, () => {
    if (options.active()) return
    updateLineState(performance.now(), false)
  })

  onUnmounted(stopRaf)

  return {
    rawLyrics, loading, error,
    lines, hasTimedLyrics,
    currentLineIdx, lineProgress,
    containerRef, setLineRef,
    load, retry,
  }
}
