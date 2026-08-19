<script setup lang="ts">
/**
 * LyricsPanel.vue — 主页歌词面板（底部抽屉，跟随播放滚动）
 * - 当前行高亮 + 卡拉OK 渐变（复用 utils/lrc 的 findCurrentLine/getLineProgress）
 * - 当前行自动居中滚动（smooth）
 * - 切歌自动重新加载并回到顶部；触摸下滑关闭
 */
import { ref, watch, computed, onUnmounted, nextTick } from 'vue'
import { ElIcon } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { parseLrc, findCurrentLine, getLineProgress, fetchLyricsCached } from '../utils/lrc'
import { usePlayerStore } from '../stores/player'
import { useUiStore } from '../stores/ui'

const player = usePlayerStore()
const ui = useUiStore()

const open = computed(() => ui.showLyricsPanel)

const loading = ref(false)
const rawLyrics = ref('')
const error = ref('')

const parsed = computed(() => parseLrc(rawLyrics.value))
const hasTimedLyrics = computed(() => parsed.value.length > 0)

const currentLineIdx = ref(-1)
const lineProgress = ref(0)

const lyricsContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])
function setLineRef(el: HTMLElement | null, idx: number) {
  if (el) lineRefs.value[idx] = el
}

async function fetchLyrics(trackId: string) {
  if (!trackId) return
  loading.value = true
  error.value = ''
  rawLyrics.value = ''
  currentLineIdx.value = -1
  lineProgress.value = 0
  const { raw, error: err } = await fetchLyricsCached(trackId)
  rawLyrics.value = raw
  error.value = err
  loading.value = false
}

// 打开时加载当前曲目歌词
watch(open, (v) => {
  if (v) {
    const id = player.currentTrack?.trackId
    if (id) fetchLyrics(id)
  }
})

// 面板开着时切歌 → 重新加载
watch(() => player.currentTrack?.trackId, (id) => {
  if (open.value && id) fetchLyrics(id)
})

// ─── 滚动跟随（rAF 循环） ───
let rafId = 0
let lastProgressUpdate = 0
const PROGRESS_THROTTLE = 50

function syncLyrics() {
  const lines = parsed.value
  if (lines.length && open.value) {
    const now = performance.now()
    const idx = findCurrentLine(lines, player.currentTime)
    if (idx !== currentLineIdx.value) {
      currentLineIdx.value = idx
      lineProgress.value = getLineProgress(lines, idx, player.currentTime)
      lastProgressUpdate = now
      nextTick(() => {
        const container = lyricsContainer.value
        const el = lineRefs.value[idx]
        if (container && el) {
          const containerHeight = container.clientHeight
          const scrollTo = el.offsetTop - containerHeight / 2 + el.offsetHeight / 2
          container.scrollTo({ top: scrollTo, behavior: 'smooth' })
        }
      })
    } else if (now - lastProgressUpdate > PROGRESS_THROTTLE) {
      lineProgress.value = getLineProgress(lines, idx, player.currentTime)
      lastProgressUpdate = now
    }
  }
  rafId = requestAnimationFrame(syncLyrics)
}

watch(open, (v) => {
  cancelAnimationFrame(rafId)
  if (v) rafId = requestAnimationFrame(syncLyrics)
}, { immediate: true })

onUnmounted(() => cancelAnimationFrame(rafId))

// Touch drag to close
const dragOffset = ref(0)
const dragging = ref(false)
let startY = 0

function onTouchStart(e: TouchEvent) {
  const c = lyricsContainer.value
  if (c && c.scrollTop > 5) return
  startY = e.touches[0].clientY
  dragging.value = true
}

function onTouchMove(e: TouchEvent) {
  if (!dragging.value) return
  const dy = e.touches[0].clientY - startY
  if (dy > 0) dragOffset.value = dy
}

function onTouchEnd() {
  if (!dragging.value) return
  if (dragOffset.value > 100) {
    ui.closeLyricsPanel()
  }
  dragOffset.value = 0
  dragging.value = false
}
</script>

<template>
  <Transition name="lyrics-slide">
    <div v-if="open" class="lyrics-overlay" @click.self="ui.closeLyricsPanel()">
      <div class="lyrics-panel"
           :style="dragging && dragOffset > 0 ? { transform: `translateY(${dragOffset}px)` } : {}"
           @touchstart.passive="onTouchStart"
           @touchmove.passive="onTouchMove"
           @touchend.passive="onTouchEnd">
        <div class="lyrics-body">
          <header class="lyrics-head">
            <el-icon :size="18"><Document /></el-icon>
            <h3>{{ player.currentTrack?.name || '歌词' }}</h3>
            <span v-if="player.currentTrack" class="lyrics-artist">{{ player.currentTrack.artist }}</span>
          </header>

          <div v-if="loading" class="lyrics-loading">
            <div v-for="n in 5" :key="n" class="lyric-skel" />
          </div>

          <div v-else-if="error && !rawLyrics" class="lyrics-error">
            <p>{{ error }}</p>
          </div>

          <template v-else-if="hasTimedLyrics">
            <div class="lyrics-lines" ref="lyricsContainer">
              <div class="lyrics-pad-top" />
              <p
                v-for="(line, i) in parsed" :key="i"
                :ref="(el) => setLineRef(el as HTMLElement, i)"
                class="lyric-line"
                :class="{ active: i === currentLineIdx, past: i < currentLineIdx }"
                :style="i === currentLineIdx ? { '--p': lineProgress + '%' } : null"
              >
                {{ line.text || '···' }}
              </p>
              <div class="lyrics-pad-bottom" />
            </div>
          </template>

          <pre v-else-if="rawLyrics" class="lyrics-raw">{{ rawLyrics }}</pre>

          <div v-else class="lyrics-empty">
            <p>暂无歌词</p>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.lyrics-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.lyrics-panel {
  width: 100%;
  max-width: 600px;
  max-height: 60vh;
  background: var(--jn-bg-elev);
  border-top: 1px solid var(--jn-hair);
  border-radius: 16px 16px 0 0;
  overflow: hidden;
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.lyrics-slide-enter-active,
.lyrics-slide-leave-active {
  transition: opacity 0.3s ease;
}
.lyrics-slide-enter-active .lyrics-panel,
.lyrics-slide-leave-active .lyrics-panel {
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.lyrics-slide-enter-from,
.lyrics-slide-leave-to {
  opacity: 0;
}
.lyrics-slide-enter-from .lyrics-panel,
.lyrics-slide-leave-to .lyrics-panel {
  transform: translateY(100%);
}

.lyrics-body {
  display: flex;
  flex-direction: column;
  padding: 20px 24px 24px;
  color: var(--jn-ink);
  height: 60vh;
  box-sizing: border-box;
}

.lyrics-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 16px;
  color: var(--jn-ink-dim);
  flex-shrink: 0;
}
.lyrics-head h3 {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-size: 18px;
  font-weight: 500;
  color: var(--jn-ink-strong);
}
.lyrics-artist {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-muted);
}

/* 歌词滚动区 */
.lyrics-lines {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  scroll-behavior: smooth;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.lyrics-lines::-webkit-scrollbar { display: none; }
.lyrics-pad-top, .lyrics-pad-bottom { flex-shrink: 0; height: 24vh; }

.lyric-line {
  margin: 0;
  padding: 6px 16px;
  font-size: 14px;
  line-height: 1.8;
  text-align: center;
  max-width: 100%;
  word-break: break-word;
  transition: opacity 0.4s ease, transform 0.4s ease, font-size 0.4s ease;
  color: var(--jn-ink-muted);
  opacity: 0.5;
  transform: scale(0.95);
}
.lyric-line.past { opacity: 0.2; transform: scale(0.92); }
.lyric-line.active {
  font-size: 17px;
  font-weight: 600;
  opacity: 1;
  transform: scale(1.05);
  background: linear-gradient(
    90deg,
    var(--jn-accent) 0%,
    var(--jn-accent) var(--p, 0%),
    var(--jn-ink-muted) var(--p, 0%),
    var(--jn-ink-muted) 100%
  );
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

.lyrics-loading { display: flex; flex-direction: column; gap: 12px; padding-top: 40px; }
.lyric-skel {
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--jn-row-hover), var(--jn-hair), var(--jn-row-hover));
  background-size: 200% 100%;
  animation: skel 1.4s linear infinite;
}
@keyframes skel { 0% { background-position: 200% 0 } 100% { background-position: -200% 0 } }

.lyrics-error {
  padding: 40px 0;
  text-align: center;
  color: var(--jn-danger);
}

.lyrics-raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 13px;
  line-height: 1.8;
  color: var(--jn-ink-dim);
  overflow-y: auto;
  scrollbar-width: none;
}
.lyrics-raw::-webkit-scrollbar { display: none; }

.lyrics-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--jn-ink-muted);
}

@media (max-width: 720px) {
  .lyrics-panel {
    max-width: 100%;
    max-height: 70vh;
  }
  .lyrics-body { height: 70vh; padding: 16px 16px 20px; }
  .lyric-line { font-size: 13.5px; }
  .lyric-line.active { font-size: 16px; }
}
</style>
