<script setup lang="ts">
/**
 * LyricsPanel.vue — 主页歌词面板（底部抽屉，跟随播放滚动）
 * - 歌词加载/解析/当前行/滚动跟随全部来自 composables/useLyrics（与播放页共用一份内核）
 * - 当前行高亮 + 卡拉OK 渐变
 * - 切歌自动重新加载并回到顶部；触摸下滑关闭
 */
import { ref, computed } from 'vue'
import JIcon from './JIcon.vue'
import { useLyrics } from '../composables/useLyrics'
import { usePlayerStore } from '../stores/player'
import { useUiStore } from '../stores/ui'

const player = usePlayerStore()
const ui = useUiStore()

const open = computed(() => ui.showLyricsPanel)

// 面板打开时才有必要跑 rAF；抽屉里的歌词总是跟随滚动。
// autoLoad: false —— 抽屉没打开就不发歌词请求（保持原有的懒加载行为）。
const {
  lines: parsed, loading, error, rawLyrics, hasTimedLyrics,
  currentLineIdx, lineProgress,
  containerRef: lyricsContainer, setLineRef,
} = useLyrics({ active: () => ui.showLyricsPanel, scroll: () => true, autoLoad: false })

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
            <JIcon name="document" :size="18" />
            <h3>{{ player.currentTrack?.name || '歌词' }}</h3>
            <span v-if="player.currentTrack" class="lyrics-artist">{{ player.currentTrack.artist }}</span>
          </header>

          <div v-if="loading" class="jn-skel-stack lyrics-loading">
            <div v-for="n in 5" :key="n" class="jn-skel" />
          </div>

          <div v-else-if="error && !rawLyrics" class="jn-state jn-state--error lyrics-error">
            <p class="jn-state-text">{{ error }}</p>
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

          <div v-else class="jn-state lyrics-empty">
            <p class="jn-state-text">暂无歌词</p>
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
  background: var(--jn-scrim);
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
  border-radius: var(--jn-radius-md) var(--jn-radius-md) 0 0;
  overflow: hidden;
  transition: transform var(--jn-dur-slow) var(--jn-ease);
}

.lyrics-slide-enter-active,
.lyrics-slide-leave-active {
  transition: opacity var(--jn-dur-slow) var(--jn-ease);
}
.lyrics-slide-enter-active .lyrics-panel,
.lyrics-slide-leave-active .lyrics-panel {
  transition: transform var(--jn-dur-slow) var(--jn-ease);
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
  transition: opacity var(--jn-dur-slow) var(--jn-ease), transform var(--jn-dur-slow) var(--jn-ease), font-size var(--jn-dur-slow) var(--jn-ease);
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

/* 加载 / 错误 / 空三态走 styles.css 的 .jn-skel* / .jn-state* 工具类，
   这里只保留本面板的局部差异。 */
.lyrics-loading { --jn-state-pad: 40px 0 0; }
.lyrics-error { --jn-state-pad: 40px 0; }
.lyrics-empty { --jn-state-pad: 40px 0; }

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
