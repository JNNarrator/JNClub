<script setup lang="ts">
/**
 * SearchDrawer.vue — 全局搜索抽屉（Ctrl/Cmd+K 唤起）
 * 收藏(标题+URL) / 便签(标题+内容摘要) / 云盘(文件名) 分组展示
 * 点击结果 → 切到对应模块并选中目录
 */
import { ref, watch, computed, nextTick } from 'vue'
import { NDrawer, NInput, NIcon, NSpin, NEllipsis } from 'naive-ui'
import { Search, Bookmark, StickyNote, FileText, KeyRound, Tag, Music, ArrowRight, Lock, Moon, Trash2, LayoutDashboard, Puzzle, Plus } from 'lucide-vue-next'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import axios from 'axios'
import { JGradientText } from '../../../shared/components/animation'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  /** 跳转：切模块 + 选目录（music 直接开播放器，无目录） */
  'jump': [module: 'bookmarks' | 'notes' | 'files' | 'vault' | 'music', directoryId: number | null]
  'action': [key: string]
}>()

const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const result = ref<{
  bookmarks: any[]
  notes: any[]
  files: any[]
  vault: any[]
  tags: any[]
  tracks: any[]
}>({ bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] })

/* ─── 搜索历史（localStorage，最多 10 条） ─── */
const HISTORY_KEY = 'jn-search-history'
const history = ref<string[]>([])
const loadHistory = () => {
  try {
    history.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
  } catch { history.value = [] }
}

/* ─── 最近使用的快捷命令（localStorage，最多 6 条） ─── */
const RECENT_CMD_KEY = 'jn-recent-commands'
const recentCommands = ref<string[]>([])
const loadRecentCommands = () => {
  try {
    recentCommands.value = JSON.parse(localStorage.getItem(RECENT_CMD_KEY) || '[]')
  } catch { recentCommands.value = [] }
}
const recordCommand = (key: string) => {
  const arr = recentCommands.value.filter(k => k !== key)
  arr.unshift(key)
  recentCommands.value = arr.slice(0, 6)
  try { localStorage.setItem(RECENT_CMD_KEY, JSON.stringify(recentCommands.value)) } catch { /* 忽略 */ }
}
const pushHistory = (kw: string) => {
  const arr = history.value.filter(h => h !== kw)
  arr.unshift(kw)
  history.value = arr.slice(0, 10)
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value)) } catch { /* 忽略 */ }
}
const clearHistory = () => {
  history.value = []
  try { localStorage.removeItem(HISTORY_KEY) } catch { /* 忽略 */ }
}
loadHistory()
loadRecentCommands()

let timer: ReturnType<typeof setTimeout> | null = null

watch(() => props.show, (v) => {
  if (v) {
    keyword.value = ''
    result.value = { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
    searched.value = false
    activeIndex.value = -1
    loadHistory()
    loadRecentCommands()
  }
})

const doSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) {
    result.value = { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
    searched.value = false
    return
  }
  loading.value = true
  try {
    const res = await axios.get('/api/search', { params: { keyword: kw, limit: 20 } })
    if (res.data.code === 200) {
      result.value = res.data.data || { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
      searched.value = true
      activeIndex.value = -1
      pushHistory(kw)
    }
  } catch { /* 静默 */ }
  finally { loading.value = false }
}

const searchByHistory = (kw: string) => {
  keyword.value = kw
  doSearch()
}

const onInput = () => {
  activeIndex.value = -1
  if (timer) clearTimeout(timer)
  timer = setTimeout(doSearch, 300)
}

const total = () => result.value.bookmarks.length + result.value.notes.length + result.value.files.length
  + result.value.vault.length + result.value.tags.length + result.value.tracks.length

const handleJump = (module: 'bookmarks' | 'notes' | 'files' | 'vault' | 'music', directoryId: number | null) => {
  emit('close')
  emit('jump', module, directoryId)
}

/* ─── 快捷动作命令 ─── */
interface CommandAction {
  key: string
  label: string
  icon: any
  group: string
}
const COMMANDS: CommandAction[] = [
  { key: 'note.new', label: '新建便签', icon: Plus, group: '操作' },
  { key: 'bookmark.new', label: '新建收藏', icon: Plus, group: '操作' },
  { key: 'vault.lock', label: '锁定密码库', icon: Lock, group: '操作' },
  { key: 'theme.toggle', label: '切换主题', icon: Moon, group: '操作' },
  { key: 'module.bookmarks', label: '收藏夹', icon: Bookmark, group: '导航' },
  { key: 'module.notes', label: '便签', icon: StickyNote, group: '导航' },
  { key: 'module.files', label: '云盘', icon: FileText, group: '导航' },
  { key: 'module.vault', label: '密码库', icon: KeyRound, group: '导航' },
  { key: 'module.music', label: '音乐', icon: Music, group: '导航' },
  { key: 'go.overview', label: '概览看板', icon: LayoutDashboard, group: '导航' },
  { key: 'go.recycle', label: '回收站', icon: Trash2, group: '导航' },
  { key: 'go.extension', label: '下载中心', icon: Puzzle, group: '导航' },
]
const filteredCommands = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) {
    // 空输入时最近使用的命令排前面
    const recent = COMMANDS.filter(c => recentCommands.value.includes(c.key))
    const others = COMMANDS.filter(c => !recentCommands.value.includes(c.key))
    return [...recent, ...others]
  }
  const scored = COMMANDS
    .map(c => {
      const label = c.label.toLowerCase()
      const key = c.key.toLowerCase()
      let score = 0
      if (label.startsWith(q) || key.startsWith(q)) score = 2
      else if (label.includes(q) || key.includes(q)) score = 1
      return { c, score }
    })
    .filter(x => x.score > 0)
    .sort((a, b) => b.score - a.score)
  return scored.map(x => x.c)
})
const runCommand = (key: string) => {
  recordCommand(key)
  emit('close')
  emit('action', key)
}

/* ─── 键盘导航：↑↓ 选择、Enter 执行、Esc 关闭 ─── */
const activeIndex = ref(-1)

interface SearchNavItem {
  key: string
  label: string
  group: string
  type: 'command' | 'bookmark' | 'note' | 'file' | 'vault' | 'tag' | 'track'
  run: () => void
}

const navItems = computed<SearchNavItem[]>(() => {
  const items: SearchNavItem[] = []
  for (const c of filteredCommands.value) {
    items.push({ key: c.key, label: c.label, group: c.group, type: 'command', run: () => runCommand(c.key) })
  }
  for (const b of result.value.bookmarks) {
    items.push({ key: `b-${b.id}`, label: b.title || b.url, group: '收藏', type: 'bookmark', run: () => handleJump('bookmarks', b.directoryId) })
  }
  for (const n of result.value.notes) {
    items.push({ key: `n-${n.id}`, label: n.title || '无标题', group: '便签', type: 'note', run: () => handleJump('notes', n.directoryId) })
  }
  for (const f of result.value.files) {
    items.push({ key: `f-${f.id}`, label: f.originalName, group: '云盘文件', type: 'file', run: () => handleJump('files', f.directoryId) })
  }
  for (const v of result.value.vault) {
    items.push({ key: `v-${v.id}`, label: v.name, group: '密码库', type: 'vault', run: () => handleJump('vault', v.directoryId) })
  }
  for (const t of result.value.tags) {
    items.push({ key: `t-${t.id}`, label: t.name, group: '标签', type: 'tag', run: () => handleJump('bookmarks', null) })
  }
  for (const t of result.value.tracks) {
    items.push({ key: `track-${t.trackId}`, label: t.name, group: '音乐', type: 'track', run: () => handleJump('music', null) })
  }
  return items
})

const navIndex = (key: string) => navItems.value.findIndex(i => i.key === key)

const scrollActiveIntoView = async () => {
  await nextTick()
  document.querySelector('.search-nav-active')?.scrollIntoView({ block: 'nearest' })
}

const onKeydown = async (e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (!navItems.value.length) return
    activeIndex.value = (activeIndex.value + 1) % navItems.value.length
    await scrollActiveIntoView()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (!navItems.value.length) return
    activeIndex.value = (activeIndex.value - 1 + navItems.value.length) % navItems.value.length
    await scrollActiveIntoView()
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const item = activeIndex.value >= 0 ? navItems.value[activeIndex.value] : null
    if (item) item.run()
    else doSearch()
  } else if (e.key === 'Escape') {
    emit('close')
  }
}

/** 高亮渲染：按后端返回的 {field, ranges:[[s,e]]} 把命中词包 <mark>（防注入转义） */
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
function highlightText(text: string, highlights: any[], field: string): string {
  if (!text) return ''
  const h = (highlights || []).find((x: any) => x.field === field)
  if (!h || !h.ranges?.length) return escapeHtml(text)
  let html = ''
  let last = 0
  for (const [s, e] of h.ranges) {
    html += escapeHtml(text.slice(last, s)) + '<mark class="hl-mark">' + escapeHtml(text.slice(s, e)) + '</mark>'
    last = e
  }
  html += escapeHtml(text.slice(last))
  return html
}

/** 移动端抽屉全宽（NDrawer width 支持 number 或字符串，'100%' 在窄屏生效） */
const isMobileWidth = () => (typeof window !== 'undefined' && window.innerWidth < 768 ? '100%' : 420)
</script>

<template>
  <NDrawer v-model:show="props.show" :width="isMobileWidth()" placement="right" @update:show="(v: boolean) => !v && emit('close')">
    <div class="search-panel">
      <!-- 标题（图标放渐变外：JGradientText 走 text prop，slot 拼接对转发链不可靠） -->
      <div class="search-header">
        <div class="search-title">
          <NIcon :component="Search" size="16" class="search-title-icon" />
          <JGradientText
            text="命令面板"
            :animation-speed="6"
            direction="horizontal"
            :colors="['var(--brand)', 'var(--brand-suppl)', 'var(--brand)']"
          />
        </div>
        <span class="search-hint">Ctrl / ⌘ + K</span>
      </div>

      <!-- 输入框 -->
      <NInput
        v-model:value="keyword"
        size="large"
        placeholder="搜索收藏 / 便签 / 文件 / 密码 / 音乐…"
        clearable
        @input="onInput"
        @keydown="onKeydown"
      >
        <template #prefix><NIcon :component="Search" size="16" /></template>
      </NInput>

      <!-- 搜索历史（空输入时展示） -->
      <div v-if="!keyword.trim() && history.length" class="history-section">
        <div class="history-head">
          <span class="history-title">最近搜索</span>
          <button type="button" class="history-clear" @click="clearHistory">清空</button>
        </div>
        <div class="history-chips">
          <button
            v-for="h in history" :key="h"
            type="button" class="history-chip jnclub-bouncy" @click="searchByHistory(h)"
          >{{ h }}</button>
        </div>
      </div>

      <!-- 命令区 -->
      <div v-if="filteredCommands.length" class="cmd-section">
        <div class="cmd-subtitle">快捷操作</div>
        <div class="cmd-list">
          <button
            v-for="c in filteredCommands" :key="c.key"
            type="button" :class="['cmd-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(c.key) }]" @click="runCommand(c.key)"
          >
            <NIcon :component="c.icon" size="15" class="cmd-ic" />
            <span class="cmd-label">{{ c.label }}</span>
            <span class="cmd-group">{{ c.group }}</span>
          </button>
        </div>
      </div>

      <NSpin :show="loading" class="search-spin">
        <!-- 空输入 -->
        <JEmptyState
          v-if="!keyword.trim()"
          message="搜索收藏、便签、云盘、密码库和音乐"
          hint="输入关键词开始搜索，或执行上方快捷操作"
          :show-cta="false"
          class="search-empty"
        />

        <!-- 无结果 -->
        <div v-else-if="searched && total() === 0" class="no-result">
          <JEmptyState
            message="没有找到相关内容"
            hint="换个关键词试试"
            :show-cta="false"
            class="search-empty"
          />
        </div>

        <!-- 结果 -->
        <div v-else class="search-results">
          <!-- 收藏 -->
          <div v-if="result.bookmarks.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Bookmark" size="14" /> 收藏
              <span class="group-count">{{ result.bookmarks.length }}</span>
            </div>
            <div
              v-for="(b, idx) in result.bookmarks" :key="b.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`b-${b.id}`) }]" @click="handleJump('bookmarks', b.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <img v-if="b.icon" :src="b.icon" class="item-icon" @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')" />
              <NIcon v-else :component="Bookmark" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(b.title || b.url, b.highlights, b.title ? 'title' : 'url')" />
                <NEllipsis class="item-sub">{{ b.url }}</NEllipsis>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 便签 -->
          <div v-if="result.notes.length" class="result-group">
            <div class="group-title">
              <NIcon :component="StickyNote" size="14" /> 便签
              <span class="group-count">{{ result.notes.length }}</span>
            </div>
            <div
              v-for="(n, idx) in result.notes" :key="n.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`n-${n.id}`) }]" @click="handleJump('notes', n.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="StickyNote" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(n.title || '无标题', n.highlights, 'title')" />
                <NEllipsis v-if="n.excerpt" class="item-sub">{{ n.excerpt }}</NEllipsis>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 云盘 -->
          <div v-if="result.files.length" class="result-group">
            <div class="group-title">
              <NIcon :component="FileText" size="14" /> 云盘文件
              <span class="group-count">{{ result.files.length }}</span>
            </div>
            <div
              v-for="(f, idx) in result.files" :key="f.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`f-${f.id}`) }]" @click="handleJump('files', f.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="FileText" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(f.originalName, f.highlights, 'originalName')" />
                <span class="item-size">{{ f.size ? `${(f.size / 1024 / 1024).toFixed(1)} MB` : '' }}</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 密码库（仅标题，安全） -->
          <div v-if="result.vault.length" class="result-group">
            <div class="group-title">
              <NIcon :component="KeyRound" size="14" /> 密码库
              <span class="group-count">{{ result.vault.length }}</span>
            </div>
            <div
              v-for="(v, idx) in result.vault" :key="v.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`v-${v.id}`) }]" @click="handleJump('vault', v.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="KeyRound" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(v.name, v.highlights, 'name')" />
                <span class="item-sub">密码库条目</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 标签 -->
          <div v-if="result.tags.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Tag" size="14" /> 标签
              <span class="group-count">{{ result.tags.length }}</span>
            </div>
            <div
              v-for="(t, idx) in result.tags" :key="t.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`t-${t.id}`) }]" @click="handleJump('bookmarks', null)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="Tag" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(t.name, t.highlights, 'name')" />
                <span class="item-sub">{{ t.count || 0 }} 条关联</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 音乐曲目 -->
          <div v-if="result.tracks.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Music" size="14" /> 音乐
              <span class="group-count">{{ result.tracks.length }}</span>
            </div>
            <div
              v-for="(t, idx) in result.tracks" :key="t.trackId"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`track-${t.trackId}`) }]" @click="handleJump('music', null)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="Music" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(t.name, t.highlights, 'name')" />
                <span class="item-sub">{{ t.artist }}</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>
        </div>
      </NSpin>
    </div>
  </NDrawer>
</template>

<style scoped>
.search-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}
.search-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.search-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.search-title-icon {
  color: var(--brand);
}
.search-hint {
  font-size: var(--fs-xs);
  color: var(--text-3);
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  padding: 2px 8px;
  border-radius: var(--radius-pill);
}
.search-spin {
  flex: 1;
  overflow: hidden;
}
.search-empty {
  padding-top: 80px;
}
.search-results {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.result-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-2);
}
.group-count {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  opacity: 0;
  animation: search-item-in .3s var(--ease) forwards;
}
.result-item:hover,
.result-item.search-nav-active {
  background: var(--glass-chip-bg);
}
.result-item.search-nav-active .item-arrow {
  opacity: 1;
}
@keyframes search-item-in {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
.item-icon {
  width: 18px;
  height: 18px;
  border-radius: 3px;
  flex-shrink: 0;
}
.item-fallback {
  color: var(--brand);
  flex-shrink: 0;
}
.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.item-title {
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-1);
}
.hl-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hl-mark {
  background: var(--brand-soft);
  color: var(--brand);
  border-radius: 2px;
  padding: 0 1px;
}
.item-sub {
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.item-size {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.item-arrow {
  color: var(--text-3);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}
.result-item:hover .item-arrow {
  opacity: 1;
}
.no-result {
  padding-top: 40px;
}

/* 移动端：抽屉全宽时收紧内边距 */
@media (max-width: 767px) {
  .search-panel {
    padding: 16px;
  }
  .result-item {
    padding: 10px 8px;
  }
  .item-arrow {
    opacity: 1;
  }
}
.cmd-section { display: flex; flex-direction: column; gap: 6px; }
.cmd-subtitle { font-size: var(--fs-xs); color: var(--text-3); letter-spacing: 0.05em; }

/* 搜索历史 */
.history-section { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.history-head {
  display: flex; align-items: center; justify-content: space-between;
}
.history-title { font-size: var(--fs-xs); color: var(--text-3); letter-spacing: 0.05em; }
.history-clear {
  font-size: var(--fs-xs); color: var(--text-3);
  background: none; border: none; cursor: pointer;
}
.history-clear:hover { color: var(--brand); }
.history-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.history-chip {
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-chip-border);
  background: var(--glass-chip-bg);
  color: var(--glass-chip-text);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.history-chip:hover { border-color: var(--brand); color: var(--brand); }
.cmd-list { display: flex; flex-direction: column; gap: 4px; }
.cmd-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 12px;
  border: none; border-radius: var(--radius-sm);
  background: transparent; cursor: pointer;
  font-family: inherit; font-size: var(--fs-md);
  color: var(--text-1);
  text-align: left;
  transition: background var(--dur) var(--ease);
}
.cmd-item:hover,
.cmd-item.search-nav-active { background: var(--glass-chip-bg); }
.cmd-ic { color: var(--brand); flex-shrink: 0; }
.cmd-label { flex: 1; }
.cmd-group { font-size: var(--fs-xs); color: var(--text-3); }
</style>