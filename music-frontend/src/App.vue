<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { showDialog } from 'vant'
import JIcon from './components/JIcon.vue'
import TrackList from './components/TrackList.vue'
import PlayerBar from './components/PlayerBar.vue'
import LyricsPanel from './components/LyricsPanel.vue'
import PlaylistPanel from './components/PlaylistPanel.vue'
const PlayerPage = defineAsyncComponent(() => import('./components/PlayerPage.vue'))
import BrandLogo from './components/BrandLogo.vue'
import LanzouAuthPanel from './components/LanzouAuthPanel.vue'
import { useThemeStore } from './stores/theme'
import { usePlayerStore, lanzouSessionExpired } from './stores/player'

const theme = useThemeStore()
const player = usePlayerStore()
function openLanzouAuth() {
  const event = new CustomEvent('open-lanzou-auth')
  window.dispatchEvent(event)
}

/** 返回 JNClub 工作台：iframe 内嵌时让父窗口跳转；独立访问（直链/PWA）时本窗口跳转 */
function goBack() {
  const home = '/jnclub/'
  if (window.self !== window.top) {
    try {
      window.parent.location.href = home
      return
    } catch {
      /* 跨域场景退化为本窗口跳转 */
    }
  }
  location.href = home
}

// 禁止选中、复制、粘贴
if (typeof window !== 'undefined') {
  document.addEventListener('selectstart', (e) => e.preventDefault())
  document.addEventListener('copy', (e) => e.preventDefault())
  document.addEventListener('cut', (e) => e.preventDefault())
  document.addEventListener('paste', (e) => e.preventDefault())
  document.addEventListener('contextmenu', (e) => e.preventDefault())
}

const BASE_TITLE = 'JNMusic · 夜猫电台'

// watch for title updates in PlayerPage

const themeLabel = computed(() => (theme.mode === 'dark' ? '切到白天模式' : '切到夜间模式'))
const showThemeTooltip = ref(false)
const pwaInstallable = ref(false)

/**
 * 内嵌时跟随 JNClub 主站主题（PLAN-2026-19 M3-1）。
 * 只接受主站 origin 发来的 `jn:theme`，独立直开时不会有任何消息，行为不变。
 * 用户在音乐页内手动切换后，主站再切主题仍会覆盖 —— 内嵌场景下以主站为准。
 */
function onHostMessage(e: MessageEvent) {
  if (e.origin !== window.location.origin) return
  const data = e.data
  if (!data || data.type !== 'jn:theme') return
  if (data.theme === 'light' || data.theme === 'dark') {
    theme.set(data.theme)
  }
}

onMounted(() => {
  if (document.documentElement.classList.contains('pwa-installable')) {
    pwaInstallable.value = true
  }
  window.addEventListener('beforeinstallprompt', () => { pwaInstallable.value = true })
  window.addEventListener('appinstalled', () => { pwaInstallable.value = false })
  window.addEventListener('message', onHostMessage)
  maybeResume()
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onHostMessage)
})

/** 格式化秒数为 mm:ss */
function fmtTime(seconds: number) {
  const s = Math.floor(seconds)
  if (!Number.isFinite(s) || s < 0) return '0:00'
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** 启动时提示「继续播放」上次听到的位置（跨设备进度同步） */
async function maybeResume() {
  try {
    const last = await player.restoreLastPlay()
    if (!last || last.progressSeconds <= 30) return
    const track = last.track
    const confirmed = await showDialog({
      title: '继续播放',
      message: `上次听到 ${track.name} - ${track.artist || '未知艺人'}（${fmtTime(last.progressSeconds)}），接着听？`,
      confirmButtonText: '继续',
      cancelButtonText: '不了',
    })
    if (confirmed) {
      await player.resumeTrack(track, last.progressSeconds)
    }
  } catch {
    // 取消或失败均忽略
  }
}

const hasFatalError = ref(false)
const reloadPage = () => { location.reload() }

// 全局未捕获异常兜底
if (typeof window !== 'undefined') {
  window.addEventListener('error', (e) => {
    if (!e.target || (e.target as HTMLElement).tagName !== 'IMG') {
      hasFatalError.value = true
    }
  })
  window.addEventListener('unhandledrejection', () => {
    hasFatalError.value = true
  })
}

</script>

<template>
  <div class="shell">
    <div class="glow-orange" aria-hidden="true" />
    <div class="grain" aria-hidden="true" />

    <div v-if="lanzouSessionExpired.value" class="lanzou-alert">
      <span>蓝奏云会话已过期，歌曲暂时无法播放，请重新认证</span>
      <button class="alert-btn" @click="openLanzouAuth">前往认证</button>
      <button class="alert-close" @click="lanzouSessionExpired.value = false">×</button>
    </div>
    <header class="topbar">
      <div class="brand-group">
        <button
          class="back-btn"
          type="button"
          title="返回 JNClub 工作台"
          aria-label="返回 JNClub 工作台"
          @click="goBack"
        >
          <JIcon name="back" :size="16" />
          <span>返回</span>
        </button>
        <div class="brand">
          <BrandLogo :size="38" />
          <div class="brand-text">
            <p class="brand-name">JNMusic</p>
            <p class="brand-tag">深夜电台 · 一场没有主播的私人节目</p>
          </div>
        </div>
      </div>
      <div class="marquee" aria-hidden="true">
        <span>NOW ON AIR · SIDE B · 33 ⅓ RPM · KEEP THE NEEDLE STEADY · </span>
        <span>NOW ON AIR · SIDE B · 33 ⅓ RPM · KEEP THE NEEDLE STEADY · </span>
      </div>
      <div class="header-actions">
        <LanzouAuthPanel />
        <button v-if="pwaInstallable" class="install-btn" @click="(window as any).__installPrompt?.()" title="安装到桌面">
          <JIcon name="download" :size="15" />
          <span>安装</span>
        </button>
        <div class="theme-tooltip-wrapper"
             @mouseenter="showThemeTooltip = true"
             @mouseleave="showThemeTooltip = false">
          <button
            class="theme-toggle"
            :aria-label="themeLabel"
            @click="theme.toggle()"
          >
            <JIcon :name="theme.mode === 'dark' ? 'moon' : 'sunny'" :size="18" />
          </button>
          <div v-if="showThemeTooltip" class="theme-tooltip">{{ themeLabel }}</div>
        </div>
      </div>
    </header>

  
  <div v-if="hasFatalError" class="fatal-error">
    <div class="fatal-error-body">
      <h2>出错了</h2>
      <p>应用遇到了意外的错误，试试刷新页面。</p>
      <button class="fatal-retry" @click="reloadPage">刷新页面</button>
    </div>
  </div>

  <main class="stage">
      <TrackList />
    </main>

    <PlayerBar />
    <PlayerPage />
    <LyricsPanel />
    <PlaylistPanel mode="manage" />
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  height: 100vh;
  height: calc(var(--vh, 1vh) * 100);
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding: 32px 40px 0;
  padding: calc(32px + env(safe-area-inset-top, 0px)) calc(40px + env(safe-area-inset-right, 0px)) calc(20px + env(safe-area-inset-bottom, 0px)) calc(40px + env(safe-area-inset-left, 0px));
  color: var(--jn-ink);
  overflow: hidden;
  isolation: isolate;
  transition: color var(--jn-dur-slow) var(--jn-ease);
}

.stage {
  max-width: 1080px;
  margin: 0 auto;
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.glow-orange {
  position: fixed;
  top: calc(-20vh + env(safe-area-inset-top, 0px));
  right: -10vw;
  width: 70vw;
  height: 70vw;
  max-width: 900px;
  max-height: 900px;
  background: radial-gradient(circle at center, var(--jn-glow), transparent 60%);
  z-index: -1;
  pointer-events: none;
  filter: blur(20px);
  transition: background var(--jn-dur-slow) var(--jn-ease);
}

.grain {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: -1;
  opacity: var(--jn-grain-opacity);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
}

.brand-group {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 返回按钮：融入现有胶囊/描边语言，克制不抢戏 */
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px 0 10px;
  border-radius: var(--jn-radius-pill);
  border: 1px solid var(--jn-hair-strong);
  background: transparent;
  color: var(--jn-ink-dim);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 12px;
  cursor: pointer;
  transition: color var(--jn-dur) var(--jn-ease), border-color var(--jn-dur) var(--jn-ease), background var(--jn-dur) var(--jn-ease), transform var(--jn-dur-fast);
  white-space: nowrap;
}
.back-btn:hover {
  color: var(--jn-accent);
  border-color: var(--jn-accent);
  background: var(--jn-accent-soft);
}
.back-btn:active { transform: translateX(-2px); }

.brand-name {
  margin: 0;
  font-family: 'Fraunces', serif;
  font-weight: 700;
  font-size: 22px;
  letter-spacing: 0;
  color: var(--jn-ink-strong);
}

.brand-tag {
  margin: 2px 0 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-muted);
}

.marquee {
  flex: 1;
  max-width: 420px;
  overflow: hidden;
  white-space: nowrap;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: var(--jn-ink-muted);
  mask-image: linear-gradient(90deg, transparent, #000 15%, #000 85%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 15%, #000 85%, transparent);
}

.marquee span {
  display: inline-block;
  animation: scroll var(--jn-dur-aurora) var(--jn-ease-linear) infinite;
}

@keyframes scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-100%); }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.install-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 14px 0 12px;
  border-radius: var(--jn-radius-pill);
  border: 1px solid var(--jn-accent);
  background: var(--jn-accent-soft);
  color: var(--jn-accent);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11.5px;
  cursor: pointer;
  transition: background var(--jn-dur), transform var(--jn-dur-fast);
  white-space: nowrap;
}
.install-btn:hover {
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
}
.install-btn:active { transform: translateY(1px); }

@media (max-width: 720px) {
  .install-btn span { display: none; }
  .install-btn { padding: 0 10px; }
}

.theme-tooltip-wrapper {
  position: relative;
}

.theme-toggle {
  color: var(--jn-ink-dim);
  background: transparent;
  border: 1px solid var(--jn-hair);
  border-radius: 50%;
  width: 40px;
  height: 40px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: color var(--jn-dur) var(--jn-ease), border-color var(--jn-dur) var(--jn-ease), transform var(--jn-dur) var(--jn-ease);
}
.theme-toggle:hover {
  color: var(--jn-accent);
  border-color: var(--jn-accent);
  transform: rotate(-12deg);
}


.fatal-error {
  position: fixed; inset: 0; z-index: 999;
  display: flex; align-items: center; justify-content: center;
  background: var(--jn-bg);
}
.fatal-error-body { text-align: center; }
.fatal-error-body h2 {
  font-family: 'Fraunces', serif; font-weight: 500; font-style: italic;
  font-size: 28px; color: var(--jn-ink-strong); margin: 0 0 12px;
}
.fatal-error-body p { color: var(--jn-ink-dim); font-size: 14px; margin: 0 0 20px; }
.fatal-retry {
  padding: 10px 22px; border: none; border-radius: var(--jn-radius-pill);
  background: var(--jn-accent); color: var(--jn-accent-ink);
  font-size: 13px; font-weight: 600; cursor: pointer;
}

.lanzou-alert {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 12px;
  background: var(--jn-accent-soft);
  border: 1px solid var(--jn-accent);
  border-radius: var(--jn-radius-sm);
  font-size: 13px;
  color: var(--jn-accent);
  animation: alert-slide-in var(--jn-dur-slow) var(--jn-ease);
}
@keyframes alert-slide-in {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}
.lanzou-alert span { flex: 1; }
.alert-btn {
  padding: 5px 14px;
  border: none;
  border-radius: var(--jn-radius-pill);
  background: var(--jn-accent);
  color: var(--jn-accent-ink);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: transform var(--jn-dur-fast);
}
.alert-btn:hover { transform: scale(1.05); }
.alert-close {
  width: 28px; height: 28px;
  border: none; border-radius: 50%;
  background: transparent;
  color: var(--jn-ink-dim);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  transition: color var(--jn-dur-fast), background var(--jn-dur-fast);
}
.alert-close:hover { color: var(--jn-ink-strong); background: var(--jn-row-hover); }

.theme-tooltip {
  position: absolute;
  bottom: -32px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--jn-tooltip-bg);
  color: var(--jn-ink);
  padding: 4px 8px;
  border-radius: var(--jn-radius-xs);
  font-size: 12px;
  white-space: nowrap;
  z-index: 100;
  box-shadow: var(--jn-shadow-sm);
}

.stage {
  max-width: 1080px;
  margin: 0 auto;
}

@media (max-width: 720px) {
  .shell {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    padding: calc(16px + env(safe-area-inset-top, 0px)) calc(16px + env(safe-area-inset-right, 0px)) 0 calc(16px + env(safe-area-inset-left, 0px));
    overflow: hidden;
  }
  .topbar { margin-bottom: 8px; gap: 12px; flex-shrink: 0; }
  .stage {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
  .marquee { display: none; }
  .brand-name { font-size: 18px; }
  .brand-tag { font-size: 10.5px; }
  .theme-toggle { width: 36px; height: 36px; }
}
</style>
