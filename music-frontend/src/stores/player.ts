import { defineStore } from 'pinia'
import { computed, ref, watch, reactive } from 'vue'
import { fetchLyricsCached } from '../utils/lrc'
import { api } from '../utils/api'

// 蓝奏云会话过期标志（全局共享）
export const lanzouSessionExpired = ref(false)

export type Track = {
  trackId: string
  name: string
  artist: string
  format?: string
  fileSize?: number
  mediaUrl?: string
  urlExpiresAt?: string
}

// 三态播放模式：列表循环 -> 单曲循环 -> 随机播放 -> 列表循环。
export type PlayMode = 'list' | 'one' | 'shuffle'

const MODE_STORAGE_KEY = 'player.mode'
const VOLUME_STORAGE_KEY = 'player.volume'
const MODE_ORDER: PlayMode[] = ['list', 'one', 'shuffle']

// 播放稳定性参数：
// - 单曲加载/播放失败后，等待多久自动切到下一首
// - 从开始加载到真正可播放/出声的超时时间，超时视为卡死并进入失败跳过流程
// - 拉取直链接口的超时时间，避免后端/蓝奏云慢响应时前端一直转圈
const FAILED_PLAY_SKIP_DELAY_MS = 5000
const PLAY_STALL_TIMEOUT_MS = 12000
const MEDIA_URL_FETCH_TIMEOUT_MS = 15000
// 取链即失败 / 明确不可播（playable=false）时，不等 5s，尽快跳到下一可播曲目
const FAST_FAIL_SKIP_DELAY_MS = 1000

function readInitialMode(): PlayMode {
  if (typeof window === 'undefined') return 'list'
  const saved = window.localStorage.getItem(MODE_STORAGE_KEY)
  if (saved === 'list' || saved === 'one' || saved === 'shuffle') return saved
  return 'list'
}

// 单例音频对象：Vue 组件卸载不影响播放，符合桌面/移动端持久播放心理预期。
const audio: HTMLAudioElement | null =
  typeof window !== 'undefined' ? new Audio() : null
if (audio) audio.preload = 'metadata'

// 用于取消上一次未完成的切歌就绪回调
let pendingReady: (() => void) | null = null

// Android Wake Lock: 播放时阻止屏幕休眠（车机/导航场景）
let wakeLockSentinel: WakeLockSentinel | null = null

async function requestWakeLock() {
  try {
    if (wakeLockSentinel) return
    wakeLockSentinel = await navigator.wakeLock.request('screen')
    wakeLockSentinel.onrelease = () => { wakeLockSentinel = null }
  } catch { /* Wake Lock 不可用时不阻塞 */ }
}

async function releaseWakeLock() {
  if (wakeLockSentinel) {
    try { await wakeLockSentinel.release() } catch {}
    wakeLockSentinel = null
  }
}

// 前端直链缓存：trackId -> { url, format, expiresAt }
const urlCache = new Map<string, { url: string; format: string; expiresAt: number }>()

// 可播放候选池：trackId -> true=已知可播（直链已成功取得），false=已知坏链（取链失败/播放认证失败）
// 失败自动切换时优先在此池中找下一首「确定可播」的，而不是傻顺序切索引。
const knownPool = new Map<string, boolean>()

function markKnownGood(trackId?: string) { if (trackId) knownPool.set(trackId, true) }
function markKnownBad(trackId?: string) { if (trackId) knownPool.set(trackId, false) }
function isKnownGood(trackId?: string) { return !!trackId && knownPool.get(trackId) === true }
function isKnownBad(trackId?: string) { return !!trackId && knownPool.get(trackId) === false }

// ─── 播放进度上报（跨设备「继续播放」） ───
let lastReportedTrackId: string | null = null
let lastReportedAt = 0
const REPORT_INTERVAL = 15000

async function reportProgress(trackId: string | null | undefined, progress: number) {
  if (!trackId) return
  const now = Date.now()
  if (trackId === lastReportedTrackId && now - lastReportedAt < REPORT_INTERVAL) return
  lastReportedTrackId = trackId
  lastReportedAt = now
  try {
    await api('/api/v1/history', {
      method: 'POST',
      body: JSON.stringify({ trackId, progress: Math.max(0, Math.floor(progress)) }),
    })
  } catch {
    // 上报失败静默，不影响播放
  }
}

async function fetchJsonWithTimeout(input: string, timeoutMs: number): Promise<any> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(input, { signal: controller.signal })
    return await res.json()
  } finally {
    clearTimeout(timer)
  }
}

// 取链结果：null=网络/接口异常；{ playable:false }=后端明确判定不可播（直接快跳）；
// { url, format }=可播放直链。
type MediaUrlResult = { url: string; format: string; playable: true } | { url: null; format: ''; playable: false; message?: string } | null

async function fetchMediaUrl(trackId: string, force = false): Promise<MediaUrlResult> {
  // 先查缓存
  const cached = urlCache.get(trackId)
  if (!force && cached && Date.now() < cached.expiresAt) {
    return { url: cached.url, format: cached.format, playable: true }
  }
  try {
    const payload = await fetchJsonWithTimeout(`/music/api/v1/tracks/${trackId}/media-url`, MEDIA_URL_FETCH_TIMEOUT_MS)
    if (payload?.error?.code === 'LANZOU_SESSION_EXPIRED') {
      lanzouSessionExpired.value = true
      markKnownBad(trackId)
      return null
    }
    // 后端明确判定不可播（playable=false 或缺少 mediaUrl）→ 直接标记坏链
    if (!payload || !payload.success || !payload.data?.mediaUrl || payload.data.playable === false) {
      markKnownBad(trackId)
      return { url: null, format: '', playable: false, message: payload?.data?.message }
    }
    const expiresAt = payload.data.expiresAt
      ? new Date(payload.data.expiresAt).getTime()
      : Date.now() + 3.5 * 60 * 60 * 1000
    urlCache.set(trackId, { url: payload.data.mediaUrl, format: payload.data.format || '', expiresAt })
    markKnownGood(trackId)
    return { url: payload.data.mediaUrl, format: payload.data.format || '', playable: true }
  } catch {
    // 网络/超时异常：不标记坏链（可能是临时抖动），让上层走正常失败流程
    return null
  }
}

// 批量获取直链
async function fetchMediaUrls(trackIds: string[]): Promise<Map<string, { url: string; format: string }>> {
  const result = new Map<string, { url: string; format: string }>()
  
  // 过滤出需要请求的ID（缓存中没有或已过期）
  const idsToFetch: string[] = []
  for (const id of trackIds) {
    const cached = urlCache.get(id)
    if (cached && Date.now() < cached.expiresAt) {
      result.set(id, { url: cached.url, format: cached.format })
      markKnownGood(id)
    } else {
      idsToFetch.push(id)
    }
  }
  
  if (idsToFetch.length === 0) return result
  
  try {
    const payload = await fetchJsonWithTimeout(`/music/api/v1/tracks/media-urls?ids=${idsToFetch.join(',')}`, MEDIA_URL_FETCH_TIMEOUT_MS)
    if (payload && payload.success && payload.data) {
      for (const [trackId, data] of Object.entries(payload.data)) {
        const mediaData = data as { mediaUrl: string; format?: string; expiresAt?: string; playable?: boolean }
        const playable = mediaData.playable !== false
        if (mediaData.mediaUrl && playable) {
          const expiresAt = mediaData.expiresAt
            ? new Date(mediaData.expiresAt).getTime()
            : Date.now() + 3.5 * 60 * 60 * 1000
          urlCache.set(trackId, { url: mediaData.mediaUrl, format: mediaData.format || '', expiresAt })
          result.set(trackId, { url: mediaData.mediaUrl, format: mediaData.format || '' })
          markKnownGood(trackId)
        } else if (!playable) {
          markKnownBad(trackId)
        }
      }
    }
  } catch {
    // 批量获取失败，回退到单个获取
    for (const id of idsToFetch) {
      const singleResult = await fetchMediaUrl(id)
      if (singleResult?.url) {
        result.set(id, { url: singleResult.url, format: singleResult.format })
      }
    }
  }
  
  return result
}

async function prefetchNextUrls(tracks: Track[], currentIdx: number) {
  // 预取接下来3首的直链
  const prefetchCount = 3
  const idsToPrefetch: string[] = []
  
  for (let i = 1; i <= prefetchCount; i++) {
    const idx = (currentIdx + i) % tracks.length
    const track = tracks[idx]
    if (track?.trackId && !track.mediaUrl) {
      idsToPrefetch.push(track.trackId)
    }
  }
  
  if (idsToPrefetch.length === 0) return
  
  const urlMap = await fetchMediaUrls(idsToPrefetch)
  
  // 回写到队列
  for (let i = 1; i <= prefetchCount; i++) {
    const idx = (currentIdx + i) % tracks.length
    const track = tracks[idx]
    if (track?.trackId) {
      const urlData = urlMap.get(track.trackId)
      if (urlData) {
        tracks[idx] = { ...tracks[idx], mediaUrl: urlData.url }
      }
    }
  }
}


function updateMediaSession(track: Track) {
  if (!('mediaSession' in navigator)) return
  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.name,
    artist: track.artist,
    artwork: [{ src: '/music/favicon.svg', sizes: '512x512', type: 'image/svg+xml' }]
  })
}

export const usePlayerStore = defineStore('player', () => {
  const queue = ref<Track[]>([])
  const currentIndex = ref(-1)
  const isPlaying = ref(false)
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(0.85)
  const mode = ref<PlayMode>(readInitialMode())
  const loading = ref(false)
  let needsResume = false  // 后台切歌后需前台恢复播放
  let mediaRetryCount = 0  // 播放失败(403/网络)后强制重取直链的重试次数

  // 播放稳定性辅助状态
  let playRequestId = 0        // 每次 playIndex/重试递增，用于丢弃过期异步结果
  let failureSkipTimer: ReturnType<typeof setTimeout> | null = null
  let stallTimer: ReturnType<typeof setTimeout> | null = null
  let consecutiveFailures = 0  // 连续失败自动跳过的计数，避免全部死链时无限循环
  let switchingSource = false  // 主动清空/切换 src 时抑制空源 error 事件

  // 每首歌的就绪状态：idle(未加载) | loading(取直链中) | ready(可播放) | error(失败)
  const readyStates = reactive(new Map<string, 'idle' | 'loading' | 'ready' | 'error'>())
  function setReady(trackId: string, s: 'idle' | 'loading' | 'ready' | 'error') {
    if (!trackId) return
    readyStates.set(trackId, s)
  }
  function getTrackState(trackId?: string): 'idle' | 'loading' | 'ready' | 'error' {
    if (!trackId) return 'idle'
    return readyStates.get(trackId) || 'idle'
  }

  function clearStallTimer() {
    if (stallTimer) {
      clearTimeout(stallTimer)
      stallTimer = null
    }
  }

  function clearFailureSkipTimer() {
    if (failureSkipTimer) {
      clearTimeout(failureSkipTimer)
      failureSkipTimer = null
    }
  }

  function clearPlaybackTimers() {
    clearStallTimer()
    clearFailureSkipTimer()
  }

  /** 从开始加载到可播放的超时兜底：防止 CDN/网络卡住时永远转圈 */
  function scheduleStallTimeout(trackId: string) {
    clearStallTimer()
    stallTimer = setTimeout(() => {
      stallTimer = null
      if (currentTrack.value?.trackId === trackId && !isPlaying.value) {
        handlePlaybackFailure(trackId, 'stall-timeout')
      }
    }, PLAY_STALL_TIMEOUT_MS)
  }

  /** 播放失败后等待一小段时间再自动切到「可播放」的下一首 */
  function scheduleFailureSkip(trackId: string, fastSkip = false) {
    clearFailureSkipTimer()
    failureSkipTimer = setTimeout(() => {
      failureSkipTimer = null
      if (currentTrack.value?.trackId !== trackId) return
      // 等待期间已经恢复播放，取消跳过
      if (isPlaying.value) {
        consecutiveFailures = 0
        return
      }
      consecutiveFailures++
      setReady(trackId, 'error')
      markKnownBad(trackId)
      // 队列只有一首，或已经连续失败跳过一整轮：停止，避免死循环
      if (queue.value.length <= 1 || consecutiveFailures >= queue.value.length) {
        consecutiveFailures = 0
        return
      }
      // 智能跳到「已知可播放」的下一首；找不到时按顺序前进（并跳过已知坏链）。
      // userTriggered=true 绕过「单曲循环」，失败时一定要真正切歌。
      nextPlayable(true)
    }, fastSkip ? FAST_FAIL_SKIP_DELAY_MS : FAILED_PLAY_SKIP_DELAY_MS)
  }

  /** 统一失败入口：标记错误、停止加载，并安排自动切歌。
   *  fastSkip=true 表示「确定性失败」（取链失败/后端判不可播），尽快跳走，不等 5s。 */
  function handlePlaybackFailure(trackId: string, _reason: string, fastSkip = false) {
    if (currentTrack.value?.trackId !== trackId) return
    clearStallTimer()
    loading.value = false
    isPlaying.value = false
    mediaRetryCount = 0
    setReady(trackId, 'error')
    markKnownBad(trackId)
    scheduleFailureSkip(trackId, fastSkip)
  }

  /**
   * 智能跳过：优先选下一个「已知可播」曲目；无则顺序前进，但跳过「已知坏链」。
   * 全部候选都被试过（consecutiveFailures 已达一整轮）时由调用方停止，这里不防死循环。
   */
  function nextPlayable(userTriggered = true) {
    if (!queue.value.length) return
    const n = queue.value.length
    if (n <= 1) { next(userTriggered); return }
    let start = currentIndex.value
    // 1) 优先：从当前起向后找已知可播的曲目
    for (let step = 1; step <= n - 1; step++) {
      const idx = (start + step) % n
      const t = queue.value[idx]
      if (t && isKnownGood(t.trackId) && idx !== currentIndex.value) {
        playIndex(idx)
        return
      }
    }
    // 2) 没有已知可播：顺序前进，但跳过已知坏链
    for (let step = 1; step <= n; step++) {
      const idx = (start + step) % n
      const t = queue.value[idx]
      if (t && isKnownBad(t.trackId)) continue
      playIndex(idx)
      return
    }
    // 3) 全是坏链：退化为顺序 next（让连败保护决定是否停止）
    next(userTriggered)
  }

  const currentTrack = computed(() =>
    currentIndex.value >= 0 ? queue.value[currentIndex.value] : null
  )

  function setQueue(tracks: Track[], startIndex = 0) {
    queue.value = tracks
    if (!tracks.length) {
      currentIndex.value = -1
      stop()
      return
    }
    playIndex(Math.min(startIndex, tracks.length - 1))
  }

  function doPlay(url: string) {
    if (!audio) return
    // 取消上一次未完成的切歌回调，防止快速连点时状态混乱
    if (pendingReady) {
      audio.removeEventListener('canplay', pendingReady)
      pendingReady = null
    }
    clearFailureSkipTimer()
    loading.value = true
    isPlaying.value = false
    // 彻底终止并重置音频元素，避免旧音频残留
    switchingSource = true
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
    // 切换到新源
    audio.src = url
    audio.currentTime = 0
    audio.volume = volume.value
    // 加载/播放超时兜底：CDN 卡住时不再无限转圈
    scheduleStallTimeout(currentTrack.value?.trackId || '')

    // 尝试播放（含锁屏重试）
    // 注意：不在这里设 isPlaying，由 play/pause 事件监听器管理，
    // 避免锁屏下 play() resolve 但无声时误显示播放状态
    const tryPlay = () => {
      loading.value = false
      const p = audio.play()
      if (p && typeof p.then === 'function') {
        p.then(() => {
          // play() resolve 了，但检查音频是否真的在播放
          // 锁屏下 iOS 可能 resolve 但不输出声音
          if (audio.paused) {
            isPlaying.value = false
          }
          // 如果没 pause，play 事件会设 isPlaying = true
        }).catch(() => {
          // 锁屏或后台模式下播放可能被拒绝，延迟重试一次
          setTimeout(() => {
            audio.play().then(() => {
              if (audio.paused) isPlaying.value = false
            }).catch(() => {
              isPlaying.value = false
            })
          }, 600)
        })
      }
    }

    // 等待缓冲就绪后播放，同时设超时兜底（锁屏下 canplay 可能延迟较大）
    let played = false
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    const onReady = () => {
      if (played) return
      played = true
      if (timeoutId) clearTimeout(timeoutId)
      audio.removeEventListener('canplay', onReady)
      pendingReady = null
      tryPlay()
    }

    pendingReady = onReady
    audio.addEventListener('canplay', onReady)

    // 超时兜底：3 秒后无论如何尝试播放（处理锁屏/后台场景）
    timeoutId = setTimeout(() => {
      if (!played) {
        played = true
        audio.removeEventListener('canplay', onReady)
        pendingReady = null
        tryPlay()
      }
    }, 3000)
  }

  async function playIndex(index: number) {
    if (!audio) return
    if (index < 0 || index >= queue.value.length) return
    // 每次手动/自动切歌都使旧的异步取链结果失效，避免慢请求“回头”覆盖新歌
    const requestId = ++playRequestId
    clearPlaybackTimers()
    mediaRetryCount = 0
    // 切歌前上报上一曲进度
    if (currentIndex.value >= 0 && audio.currentTime > 5) {
      reportProgress(queue.value[currentIndex.value]?.trackId, audio.currentTime)
    }
    currentIndex.value = index
    const track = queue.value[index]
    if (!track) return

    // 停止旧音频并清空源，防止旧歌在加载期间继续播放
    loading.value = true
    switchingSource = true
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
    // 锁定用户手势：play 会失败（无源）但仍会标记音频元素为已激活
    audio.play().catch(() => {})

    // 每次播放都经过 fetchMediaUrl（内含 45min 前端缓存 + 后端缓存），
    // 避免直接使用列表里可能已失效的直链导致"点了没声音"。
    setReady(track.trackId, 'loading')
    const result = await fetchMediaUrl(track.trackId)
    if (requestId !== playRequestId || currentIndex.value !== index) return
    if (!result) {
      // 网络/超时等异常（非确定性坏链）→ 走正常失败流程，等 5s 再切
      handlePlaybackFailure(track.trackId, 'url-fetch')
      return
    }
    if (!result.playable) {
      // 后端明确判定不可播 → 快速跳过到下一可播曲目
      handlePlaybackFailure(track.trackId, 'not-playable', true)
      return
    }
    setReady(track.trackId, 'ready')
    markKnownGood(track.trackId)
    // 回写到 queue 中，后续切回这首歌不再请求
    queue.value = queue.value.map((t, i) => i === index ? { ...t, mediaUrl: result.url } : t)
    doPlay(result.url)
    // 更新 Media Session 元数据（锁屏/控制中心显示）
    updateMediaSession(track)
    // 后台预取下一首 + 预加载当前歌词
    prefetchNextUrls(queue.value, index)
    fetchLyricsCached(track.trackId)
  }

  function toggle() {
    if (!audio) return
    // error 状态下点击播放视为重试
    if (!isPlaying.value && currentTrack.value
        && getTrackState(currentTrack.value.trackId) === 'error') {
      playIndex(currentIndex.value)
      return
    }
    if (!currentTrack.value) {
      if (queue.value.length) playIndex(0)
      return
    }
    if (audio.paused) {
      audio.play().catch(() => {})
    } else {
      audio.pause()
    }
  }

  /** 播放失败后手动重试当前曲 */
  function retryCurrent() {
    if (currentIndex.value >= 0) {
      playIndex(currentIndex.value)
    }
  }

  /** 获取最近一次播放记录（跨设备「继续播放」用） */
  async function restoreLastPlay(): Promise<{ track: Track; progressSeconds: number } | null> {
    try {
      const res = await api<{ track: Track; progressSeconds: number }>('/api/v1/history/latest')
      if (res.success && res.data?.track) {
        return { track: res.data.track, progressSeconds: res.data.progressSeconds || 0 }
      }
    } catch {
      // 静默
    }
    return null
  }

  /** 用户确认后：从指定进度继续播放 */
  async function resumeTrack(track: Track, seconds: number) {
    setQueue([track], 0)
    if (audio) {
      const onReady = () => {
        audio.removeEventListener('canplay', onReady)
        try {
          audio.currentTime = Math.max(0, Math.min(seconds, audio.duration || seconds))
        } catch { /* seek 失败忽略 */ }
      }
      audio.addEventListener('canplay', onReady)
    }
    toggle()
  }

  function stop() {
    if (!audio) return
    ++playRequestId
    clearPlaybackTimers()
    switchingSource = true
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
    isPlaying.value = false
    loading.value = false
    currentTime.value = 0
    duration.value = 0
  }

  function pickShuffleIndex(): number {
    if (queue.value.length <= 1) return 0
    let next = currentIndex.value
    while (next === currentIndex.value) {
      next = Math.floor(Math.random() * queue.value.length)
    }
    return next
  }

  function next(userTriggered = true) {
    if (!queue.value.length) return
    // 单曲循环仅在自然结束时生效，用户手动切歌应正常前进。
    if (!userTriggered && mode.value === 'one') {
      playIndex(currentIndex.value)
      return
    }
    if (mode.value === 'shuffle') {
      playIndex(pickShuffleIndex())
      return
    }
    const last = queue.value.length - 1
    if (currentIndex.value >= last) {
      // list 模式与手动下一曲：到末尾回到开头。
      playIndex(0)
      return
    }
    playIndex(currentIndex.value + 1)
  }

  function prev() {
    if (!queue.value.length) return
    // 3 秒后按上一曲视作重播当前曲，符合主流播放器交互。
    if (audio && audio.currentTime > 3) {
      audio.currentTime = 0
      return
    }
    if (mode.value === 'shuffle') {
      playIndex(pickShuffleIndex())
      return
    }
    if (currentIndex.value <= 0) {
      playIndex(queue.value.length - 1)
      return
    }
    playIndex(currentIndex.value - 1)
  }

  function seek(seconds: number) {
    if (!audio) return
    audio.currentTime = Math.max(0, Math.min(seconds, duration.value || seconds))
  }

  function setVolume(value: number) {
    volume.value = Math.max(0, Math.min(1, value))
    if (audio) audio.volume = volume.value
  }

  function cyclePlayMode() {
    mode.value = MODE_ORDER[(MODE_ORDER.indexOf(mode.value) + 1) % MODE_ORDER.length]
  }

  function setPlayMode(next: PlayMode) {
    mode.value = next
  }

  if (audio) {
    // Media Session API: 让 iOS 在锁屏/后台识别为媒体应用，保持音频会话活跃
    if ('mediaSession' in navigator) {
      // play 必须永远尝试播放（不 toggle），解决锁屏下 audio 僵尸状态
      // 用户按锁屏/方向盘播放键时强制重连音频会话
      navigator.mediaSession.setActionHandler('play', () => {
        if (currentTrack.value) {
          audio.play().catch(() => {})
        }
      })
      navigator.mediaSession.setActionHandler('pause', () => {
        if (!audio.paused) audio.pause()
      })
      navigator.mediaSession.setActionHandler('previoustrack', () => {
        prev()
        updateMediaSession(currentTrack.value!)
        audio.play().catch(() => {})
      })
      navigator.mediaSession.setActionHandler('nexttrack', () => {
        next(true)
        updateMediaSession(currentTrack.value!)
        audio.play().catch(() => {})
      })
      // 方向盘长按快进/快退（Android 车机硬件按键）
      navigator.mediaSession.setActionHandler('seekforward', () => {
        if (audio) audio.currentTime = Math.min(audio.currentTime + 10, audio.duration || 0)
      })
      navigator.mediaSession.setActionHandler('seekbackward', () => {
        if (audio) audio.currentTime = Math.max(audio.currentTime - 10, 0)
      })
    }

    audio.addEventListener('loadstart', () => {
      // 新源开始加载后，不再抑制 error 事件，避免漏掉真实播放失败
      switchingSource = false
    })
    audio.addEventListener('play', () => {
      isPlaying.value = true
      clearStallTimer()
      clearFailureSkipTimer()
      consecutiveFailures = 0
      requestWakeLock()
    })
    audio.addEventListener('playing', () => {
      isPlaying.value = true
      clearStallTimer()
      clearFailureSkipTimer()
      consecutiveFailures = 0
      loading.value = false
    })
    audio.addEventListener('pause', () => {
      isPlaying.value = false
      releaseWakeLock()
    })
    audio.addEventListener('timeupdate', () => {
      currentTime.value = audio.currentTime
      // 节流上报播放进度（切歌/暂停/结束时另有上报）
      if (audio.currentTime > 5) {
        reportProgress(currentTrack.value?.trackId, audio.currentTime)
      }
      // 更新 Media Session 播放位置，保持音频会话活跃
      if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
        try {
          navigator.mediaSession.setPositionState({
            duration: audio.duration || 0,
            playbackRate: audio.playbackRate || 1,
            position: audio.currentTime || 0,
          })
        } catch {}
      }
    })
    audio.addEventListener('loadedmetadata', () => {
      duration.value = audio.duration || 0
      loading.value = false
    })
    audio.addEventListener('canplay', () => {
      loading.value = false
      // 真正可播放：重置 403 重试计数和失败跳过计数。
      // 注意这里不清理 stallTimer：canplay 后若 play() 因浏览器策略被拒，
      // 仍需超时兜底自动切歌；真正开始出声由 play/playing 清理。
      mediaRetryCount = 0
      consecutiveFailures = 0
      if (currentTrack.value) setReady(currentTrack.value.trackId, 'ready')
    })
    audio.addEventListener('waiting', () => {
      loading.value = true
      // 播放中途缓冲太久也走同一套超时跳过逻辑；
      // 已在失败跳过的等待期内时不再重复计时，避免无限延后切歌
      if (currentTrack.value && !stallTimer && !failureSkipTimer) {
        scheduleStallTimeout(currentTrack.value.trackId)
      }
    })
    audio.addEventListener('ended', () => {
      // 播完上报整曲进度（可视为「已听到结尾」）
      if (currentTrack.value) {
        reportProgress(currentTrack.value.trackId, audio.duration || 0)
      }
      next(false)
      // 锁屏/后台切歌标记：iOS 可能接受 play() 但不输出声音
      if (document.hidden) {
        needsResume = true
      }
    })
    audio.addEventListener('error', () => {
      // 主动清空 src / 切歌过程中的空源 error 不参与失败处理
      if (switchingSource || !audio.currentSrc) return
      loading.value = false
      isPlaying.value = false
      clearStallTimer()
      if (!currentTrack.value) return

      // 播放失败（直链被 CDN 拒 403 等）：清除该曲直链缓存并强制重取一次
      // 蓝奏云直链有时效，列表里缓存的旧直链可能已失效，重新拉取往往可恢复。
      if (mediaRetryCount < 2) {
        const tid = currentTrack.value.trackId
        const retryRequestId = ++playRequestId
        mediaRetryCount++
        setReady(tid, 'loading')
        urlCache.delete(tid)
        // 清源后异步重新拉直链播放
        switchingSource = true
        audio.removeAttribute('src')
        audio.load()
        fetchMediaUrl(tid, true).then((result) => {
          if (retryRequestId !== playRequestId || currentIndex.value < 0
              || currentTrack.value?.trackId !== tid) {
            return
          }
          if (result?.playable) {
            queue.value = queue.value.map((t, i) =>
              i === currentIndex.value ? { ...t, mediaUrl: result.url } : t)
            setReady(tid, 'ready')
            doPlay(result.url)
          } else {
            // 重取后仍不可播（含后端 playable=false）：快速跳到下一可播曲目
            handlePlaybackFailure(tid, 'retry-fetch-failed', true)
          }
        })
        return
      }
      // 重试仍失败：标记 error 并等待一定时间后自动切下一首
      if (currentTrack.value) {
        handlePlaybackFailure(currentTrack.value.trackId, 'media-error')
      }
    })

    // iOS PWA 后台切歌恢复：回到前台时若需要恢复播放，pause+play 重连音频输出
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && needsResume && currentTrack.value) {
        needsResume = false
        // 短暂暂停再播放，强制 iOS 重连音频会话
        audio.pause()
        setTimeout(() => {
          audio.play().catch(() => {})
        }, 50)
      }
    })
  }

  // 音量持久化，避免刷新后回到默认值影响耳机用户体验。
  const persisted = typeof window !== 'undefined'
    ? Number(window.localStorage.getItem(VOLUME_STORAGE_KEY))
    : NaN
  if (!Number.isNaN(persisted) && persisted > 0 && persisted <= 1) {
    setVolume(persisted)
  }
  watch(volume, (v) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VOLUME_STORAGE_KEY, String(v))
    }
  })
  watch(mode, (m) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(MODE_STORAGE_KEY, m)
    }
  })

  return {
    queue,
    currentIndex,
    currentTrack,
    isPlaying,
    currentTime,
    duration,
    volume,
    mode,
    loading,
    setQueue,
    playIndex,
    toggle,
    retryCurrent,
    restoreLastPlay,
    resumeTrack,
    next,
    prev,
    seek,
    setVolume,
    cyclePlayMode,
    setPlayMode,
    getTrackState,
  }
})
