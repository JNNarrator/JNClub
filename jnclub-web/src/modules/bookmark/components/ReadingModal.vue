<script setup lang="ts">
/**
 * ReadingModal.vue — 收藏阅读模式
 * 调后端 /api/bookmarks/read 抓取正文（服务端提取+清洗），站内沉浸阅读；
 * 失败时回退"在新标签页打开原文"。
 */
import { ref, watch, computed } from 'vue'
import { NModal, NButton, NIcon, NSpin, NSwitch } from 'naive-ui'
import { ExternalLink, X, BookOpen, Settings2 } from 'lucide-vue-next'
import JErrorState from '../../../shared/components/ui/JErrorState.vue'
import { useUserPreferences } from '../../../shared/composables/useUserPreferences'
import axios from 'axios'

const props = defineProps<{
  show: boolean
  url: string
  bookmarkId?: number | null
}>()
const emit = defineEmits<{ 'update:show': [v: boolean] }>()

const prefs = useUserPreferences()

/* ─── 阅读器设置（字号 / 行距 / 栏宽 / 专注纸背景）持久化到用户偏好 ─── */
const FONT_SIZES = [15, 16, 17, 18] as const
const LINE_HEIGHTS = [1.6, 1.75, 1.85, 2.0] as const
const COLUMN_WIDTHS = [
  { key: 'narrow', label: '窄栏', value: 640 },
  { key: 'medium', label: '适中', value: 720 },
  { key: 'wide', label: '宽栏', value: 880 },
] as const

const settingsOpen = ref(false)
const fontSize = ref<number>(prefs.get<number>('reader.fontSize', 16))
const lineHeight = ref<number>(prefs.get<number>('reader.lineHeight', 1.85))
const columnWidth = ref<number>(prefs.get<number>('reader.columnWidth', 720))
const focusPaper = ref<boolean>(prefs.get<boolean>('reader.focusPaper', false))

const setFontSize = (v: number) => { fontSize.value = v; prefs.set('reader.fontSize', v) }
const setLineHeight = (v: number) => { lineHeight.value = v; prefs.set('reader.lineHeight', v) }
const setColumnWidth = (v: number) => { columnWidth.value = v; prefs.set('reader.columnWidth', v) }
const toggleFocusPaper = (v: boolean) => { focusPaper.value = v; prefs.set('reader.focusPaper', v) }

/** 主题检测：token 内联在 <html>，用 --bg-page 区分亮/暗 */
const isDarkMode = ref(false)
const detectTheme = () => {
  const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg-page').trim()
  isDarkMode.value = bg === '#000000'
}

/** 正文排版样式：字号 / 行距 / 栏宽（专注纸模式下叠加暖色文字） */
const articleStyle = computed(() => ({
  fontSize: `${fontSize.value}px`,
  lineHeight: lineHeight.value,
  maxWidth: `${columnWidth.value}px`,
  ...(focusPaper.value
    ? { color: isDarkMode.value ? '#EDE4D4' : '#3A3229' }
    : {}),
}))
/** 专注纸背景：暖纸色系（亮/暗主题分别适配），提升沉浸感 */
const shellStyle = computed(() => {
  if (!focusPaper.value) return {}
  return isDarkMode.value
    ? { background: 'linear-gradient(180deg, #2C271E 0%, #241F17 100%)' }
    : { background: 'linear-gradient(180deg, #FBF5EA 0%, #F6EFDF 100%)' }
})
const shellClass = computed(() => ({ 'reading-shell--paper': focusPaper.value }))

const loading = ref(false)
const error = ref('')
const article = ref<{ title: string; content: string } | null>(null)

watch(() => props.show, (v) => {
  if (v && props.url) {
    detectTheme()
    load()
  }
})

const load = async () => {
  loading.value = true
  error.value = ''
  article.value = null
  try {
    const res = await axios.get('/api/bookmarks/read', { params: { url: props.url }, timeout: 20000 })
    if (res.data.code === 200 && res.data.data?.success) {
      article.value = { title: res.data.data.title, content: res.data.data.content }
    } else {
      error.value = res.data.data?.reason || '正文提取失败'
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || '网络错误'
  } finally {
    loading.value = false
  }
}

const close = () => {
  emit('update:show', false)
}
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    :style="{ width: 'min(880px, 94vw)' }"
    class="reading-modal"
  >
    <div class="reading-shell" :class="shellClass" :style="shellStyle">
      <div class="reading-head">
        <div class="reading-title-wrap">
          <NIcon :component="BookOpen" size="16" class="reading-title-icon" />
          <span class="reading-title">{{ article?.title || '阅读模式' }}</span>
        </div>
        <div class="reading-actions">
          <a v-if="props.url" :href="props.url" target="_blank" rel="noopener" class="reading-open">
            <NIcon :component="ExternalLink" size="14" /> 打开原文
          </a>
          <!-- 阅读器设置 -->
          <div class="reader-settings">
            <NButton quaternary circle size="small" title="阅读设置" :class="{ 'is-active': settingsOpen }" @click="settingsOpen = !settingsOpen">
              <template #icon><NIcon :component="Settings2" size="16" /></template>
            </NButton>
            <Transition name="reader-pop">
              <div v-if="settingsOpen" class="reader-settings-panel glass-card--modal">
                <div class="reader-setting-row">
                  <span class="reader-setting-label">字号</span>
                  <div class="reader-seg">
                    <button
                      v-for="s in FONT_SIZES" :key="s"
                      type="button"
                      class="reader-seg-btn"
                      :class="{ active: fontSize === s }"
                      @click="setFontSize(s)"
                    >{{ s }}</button>
                  </div>
                </div>
                <div class="reader-setting-row">
                  <span class="reader-setting-label">行距</span>
                  <div class="reader-seg">
                    <button
                      v-for="lh in LINE_HEIGHTS" :key="lh"
                      type="button"
                      class="reader-seg-btn"
                      :class="{ active: lineHeight === lh }"
                      @click="setLineHeight(lh)"
                    >{{ lh }}×</button>
                  </div>
                </div>
                <div class="reader-setting-row">
                  <span class="reader-setting-label">栏宽</span>
                  <div class="reader-seg">
                    <button
                      v-for="cw in COLUMN_WIDTHS" :key="cw.key"
                      type="button"
                      class="reader-seg-btn"
                      :class="{ active: columnWidth === cw.value }"
                      @click="setColumnWidth(cw.value)"
                    >{{ cw.label }}</button>
                  </div>
                </div>
                <div class="reader-setting-row">
                  <span class="reader-setting-label">专注纸背景</span>
                  <NSwitch :value="focusPaper" size="small" @update:value="toggleFocusPaper" />
                </div>
              </div>
            </Transition>
            <!-- 点击设置面板外部关闭 -->
            <div v-if="settingsOpen" class="reader-settings-mask" @click="settingsOpen = false"></div>
          </div>
          <NButton quaternary circle size="small" @click="close">
            <template #icon><NIcon :component="X" size="16" /></template>
          </NButton>
        </div>
      </div>

      <div class="reading-body">
        <NSpin :show="loading">
          <div v-if="loading" class="reading-hint">正在抓取正文…</div>
          <div v-else-if="error" class="reading-error">
            <JErrorState message="正文提取失败" :hint="error" @retry="load" />
            <a :href="props.url" target="_blank" rel="noopener" class="reading-fallback">
              在新标签页打开原文 →
            </a>
          </div>
          <article v-else-if="article" class="reading-article" :style="articleStyle" v-html="article.content" />
        </NSpin>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.reading-shell {
  display: flex;
  flex-direction: column;
  max-height: 86vh;
  border-radius: var(--radius-md);
  background: var(--glass-bg-solid);
  overflow: hidden;
}
.reading-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.reading-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.reading-title-icon { color: var(--brand); flex-shrink: 0; }
.reading-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.reading-open {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-sm);
  color: var(--brand);
  text-decoration: none;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--brand-soft);
}
.reading-open:hover { filter: brightness(1.05); }

.reading-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0;
}

/* ─── 阅读器设置 ─── */
.reader-settings { position: relative; }
.reader-settings .n-button.is-active {
  color: var(--brand);
  background: var(--brand-soft);
}
.reader-settings-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 232px;
  z-index: 30;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.reader-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.reader-setting-label {
  font-size: var(--fs-sm);
  color: var(--text-2);
  flex-shrink: 0;
}
.reader-seg {
  display: inline-flex;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-sm);
  padding: 2px;
  gap: 2px;
}
.reader-seg-btn {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  line-height: 1;
  padding: 5px 8px;
  border-radius: calc(var(--radius-sm) - 2px);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}
.reader-seg-btn:hover { color: var(--text-1); }
.reader-seg-btn.active {
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}
.reader-settings-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: transparent;
}

/* 设置面板弹出动效 */
.reader-pop-enter-active,
.reader-pop-leave-active {
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease);
  transform-origin: top right;
}
.reader-pop-enter-from,
.reader-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.97);
}

/* 专注纸背景：由 shellStyle 内联暖纸渐变实现，此处仅兜底 */
.reading-shell--paper { border-radius: var(--radius-lg); }
.reading-hint {
  padding: 60px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.reading-error {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.reading-empty :deep(.n-empty__description) { max-width: 360px; }
.reading-fallback {
  font-size: var(--fs-sm);
  color: var(--brand);
  text-decoration: none;
}
.reading-fallback:hover { text-decoration: underline; }

/* 阅读排版 */
.reading-article {
  max-width: 720px;
  margin: 0 auto;
  padding: 20px 28px 48px;
  font-size: 16px;
  line-height: 1.85;
  color: var(--text-1);
  word-break: break-word;
}
.reading-article :deep(h1),
.reading-article :deep(h2),
.reading-article :deep(h3),
.reading-article :deep(h4) {
  margin: 1.4em 0 0.6em;
  line-height: 1.4;
  font-weight: 700;
  color: var(--text-1);
}
.reading-article :deep(h1) { font-size: 1.6em; }
.reading-article :deep(h2) { font-size: 1.35em; }
.reading-article :deep(h3) { font-size: 1.15em; }
.reading-article :deep(p) { margin: 0.9em 0; }
.reading-article :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 12px 0;
}
.reading-article :deep(a) { color: var(--brand); text-decoration: none; }
.reading-article :deep(a):hover { text-decoration: underline; }
.reading-article :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 3px solid var(--brand);
  color: var(--text-2);
  background: var(--glass-chip-bg);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.reading-article :deep(pre) {
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-sm);
  padding: 14px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.reading-article :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: var(--glass-chip-bg);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 0.92em;
}
.reading-article :deep(pre code) { background: none; padding: 0; }
.reading-article :deep(ul),
.reading-article :deep(ol) { margin: 0.8em 0; padding-left: 1.6em; }
.reading-article :deep(li) { margin: 0.3em 0; }
.reading-article :deep(table) {
  border-collapse: collapse;
  margin: 14px 0;
  width: 100%;
  font-size: 14px;
}
.reading-article :deep(th),
.reading-article :deep(td) {
  border: 1px solid var(--glass-chip-border);
  padding: 8px 12px;
  text-align: left;
}
.reading-article :deep(th) { background: var(--glass-chip-bg); font-weight: 600; }
</style>
