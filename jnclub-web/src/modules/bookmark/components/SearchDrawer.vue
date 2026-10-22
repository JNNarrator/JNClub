<script setup lang="ts">
/**
 * SearchDrawer.vue — 全局搜索抽屉（Ctrl/Cmd+K 唤起）
 * 收藏(标题+URL) / 便签(标题+内容摘要) / 云盘(文件名) 分组展示
 * 点击结果 → 切到对应模块并选中目录
 */
import { ref, watch, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { NDrawer, NInput, NIcon, NSpin, NEllipsis } from 'naive-ui'
import { Search, Bookmark, StickyNote, FileText, KeyRound, Tag, Music, ArrowRight, Lock, Moon, Trash2, LayoutDashboard, Puzzle, Plus } from 'lucide-vue-next'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import axios from 'axios'
import { JGradientText } from '../../../shared/components/animation'
import { useRecentItems, type RecentItem, type RecentItemKind } from '../../../shared/composables/useRecentItems'
import type { NavJumpKey } from '../../../shared/types/nav'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  /** 跳转：切模块 + 选目录（music 直接开播放器，无目录） */
  'jump': [module: NavJumpKey, directoryId: number | null]
  'action': [key: string]
}>()

const router = useRouter()
const { items: recentItems, record: recordRecentItem, clear: clearRecent } = useRecentItems()

const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
type SearchResult = {
  bookmarks: any[]
  notes: any[]
  files: any[]
  vault: any[]
  tags: any[]
  tracks: any[]
  parsed?: any
}
/**
 * 空结果工厂：重置与「无结果」兜底共用同一来源。
 * 原先这里是 4 处内联字面量，7c7f4f9 批量删除 todos 字段时把整行赋值一并删掉，
 * 其中一处正是 doSearch 成功分支的写回 —— 导致搜索结果区恒为空（total() 恒为 0，
 * 结果列表/类型筛选/语法 chip 永不渲染，.no-result 恒显示）。
 * 收敛到工厂函数后，字段增删只需改一处，不会再出现同类回归。
 */
const emptyResult = (): SearchResult => ({ bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] })
const result = ref<SearchResult>(emptyResult())

const typeFilter = ref<'all' | 'bookmarks' | 'notes' | 'files' | 'vault' | 'tags' | 'tracks'>('all')

const typeFilterOptions: Array<{ label: string; value: typeof typeFilter.value }> = [
  { label: '全部', value: 'all' },
  { label: '收藏', value: 'bookmarks' },
  { label: '便签', value: 'notes' },
  { label: '云盘', value: 'files' },
  { label: '密码库', value: 'vault' },
  { label: '标签', value: 'tags' },
  { label: '音乐', value: 'tracks' },
]

const shouldShowGroup = (key: 'bookmarks' | 'notes' | 'files' | 'vault' | 'tags' | 'tracks') =>
  typeFilter.value === 'all' || typeFilter.value === key

/* ─── 搜索历史（服务端为主，localStorage 兜底，最多 10 条） ─── */
const HISTORY_KEY = 'jn-search-history'
const history = ref<string[]>([])
const serverHistory = ref<string[]>([])
const serverHistoryOk = ref(false)
const loadHistory = () => {
  try {
    history.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
  } catch { history.value = [] }
}
const displayedHistory = computed(() => serverHistoryOk.value ? serverHistory.value : history.value)

/* ─── 服务端搜索建议 ─── */
const suggest = ref<{
  history: string[]
  groups: Array<{ key: string; label: string }>
  commands: Array<{ key: string; label: string }>
}>({ history: [], groups: [], commands: [] })
const fetchServerHistory = async () => {
  try {
    const res = await axios.get('/api/search/history', { params: { limit: 20 } })
    if (res.data.code === 200) {
      serverHistory.value = (res.data.data || []).map((h: any) => h.keyword).filter(Boolean)
      serverHistoryOk.value = true
    }
  } catch { /* 服务端不可用时继续用 localStorage 兜底 */ }
}
const fetchSuggest = async () => {
  try {
    const res = await axios.get('/api/search/suggest', { params: { keyword: keyword.value.trim() } })
    if (res.data.code === 200) {
      const d = res.data.data || {}
      suggest.value = {
        history: (d.history || []).map((h: any) => h.keyword).filter(Boolean),
        groups: d.groups || [],
        commands: d.commands || [],
      }
      if (serverHistoryOk.value) serverHistory.value = suggest.value.history
    }
  } catch { /* 静默 */ }
}
const refreshHistory = () => {
  loadHistory()
  fetchServerHistory()
  fetchSuggest()
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
  try { axios.post('/api/search/history', null, { params: { keyword: kw } }) } catch { /* 静默 */ }
}
const clearHistory = async () => {
  history.value = []
  serverHistory.value = []
  try { localStorage.removeItem(HISTORY_KEY) } catch { /* 忽略 */ }
  try {
    const res = await axios.delete('/api/search/history')
    if (res.data.code === 200) serverHistoryOk.value = true
  } catch { /* 服务端失败也保持本地已清空 */ }
}
refreshHistory()
loadRecentCommands()

let timer: ReturnType<typeof setTimeout> | null = null
/** 搜索结果 AbortController：新输入/关闭抽屉时取消未决请求，避免竞态覆盖 */
let searchCtl: AbortController | null = null

watch(() => props.show, (v) => {
  if (v) {
    keyword.value = ''
    result.value = emptyResult()
    typeFilter.value = 'all'
    searched.value = false
    activeIndex.value = -1
    refreshHistory()
    loadRecentCommands()
  } else {
    searchCtl?.abort()
    searchCtl = null
  }
})

const doSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) {
    result.value = emptyResult()
    searched.value = false
    return
  }
  loading.value = true
  searchCtl?.abort()
  const ctl = new AbortController()
  searchCtl = ctl
  try {
    const res = await axios.get('/api/search', { params: { keyword: kw, limit: 20 }, signal: ctl.signal })
    if (res.data.code === 200) {
      // 必须写回结果：这一行曾随 todos 字段一并被误删，导致结果区永不渲染
      result.value = res.data.data || emptyResult()
      searched.value = true
      activeIndex.value = -1
      pushHistory(kw)
    }
  } catch { /* 吞掉（含 AbortError：新请求已接管） */ }
  finally {
    if (searchCtl === ctl) { searchCtl = null; loading.value = false }
  }
}

const searchByHistory = (kw: string) => {
  keyword.value = kw
  doSearch()
}

const onInput = () => {
  activeIndex.value = -1
  if (timer) clearTimeout(timer)
  if (!keyword.value.trim()) {
    refreshHistory()
    return
  }
  fetchSuggest()
  timer = setTimeout(doSearch, 300)
}

const total = () => result.value.bookmarks.length + result.value.notes.length + result.value.files.length
  + result.value.vault.length + result.value.tags.length + result.value.tracks.length

/**
 * 当前类型筛选下的可见条数。
 * 空态判断必须用它而非 total()：total() 是各类型之和，筛到「本类型为空」时仍 > 0，
 * 会导致所有结果分组被 shouldShowGroup 过滤掉、却又不满足 total()===0，
 * 结果区渲染成一片空白（既无结果也无「没有找到」提示）。
 */
const visibleTotal = computed(() => {
  if (typeFilter.value === 'all') return total()
  return result.value[typeFilter.value].length
})

const handleJump = (module: NavJumpKey, directoryId: number | null) => {
  emit('close')
  emit('jump', module, directoryId)
}

/* ─── 结果直接动作：跳路由 / 打开外链 / 下载文件 ─── */
const goRoute = (path: string) => {
  emit('close')
  router.push(path)
}
const openUrl = (url?: string) => {
  if (!url) return
  emit('close')
  window.open(url, '_blank', 'noopener,noreferrer')
}
const downloadFile = (id: number | string) => {
  emit('close')
  window.open(`/api/clouddisk/files/${id}/download`, '_blank')
}

/* ─── 最近打开（本地记录，空输入时展示）─── */
const RECENT_KIND_META: Record<RecentItemKind, { label: string; icon: any }> = {
  note: { label: '便签', icon: StickyNote },
  bookmark: { label: '收藏', icon: Bookmark },
  file: { label: '文件', icon: FileText },
}
const recentRelativeTime = (at: number) => {
  const diff = Date.now() - at
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d} 天前`
  return new Date(at).toLocaleDateString()
}
const recentSub = (r: RecentItem) => `${RECENT_KIND_META[r.kind].label} · ${recentRelativeTime(r.at)}`
/** 打开最近条目：再次打开 → 去重置顶刷新时间戳，然后按类型复用既有跳转 */
const openRecent = (r: Omit<RecentItem, 'key' | 'at'>) => {
  recordRecentItem(r)
  if (r.kind === 'note') goRoute(`/notes/${r.id}`)
  else if (r.kind === 'bookmark') openUrl(r.url)
  else if (r.kind === 'file') downloadFile(r.id)
}

/* ─── 搜索语法 chips ─── */
const syntaxChips = computed(() => {
  const p = result.value.parsed || {}
  const chips: Array<{ kind: 'type' | 'date' | 'tag'; label: string }> = []
  if (p.type) {
    const opt = typeFilterOptions.find(o => o.value === p.type)
    chips.push({ kind: 'type', label: `类型：${opt?.label || p.type}` })
  }
  if (p.date) chips.push({ kind: 'date', label: `日期：${p.date}` })
  if (p.tag) chips.push({ kind: 'tag', label: `标签：#${p.tag}` })
  return chips
})
const removeSyntax = (kind: 'type' | 'date' | 'tag') => {
  const parts = keyword.value.trim().split(/\s+/).filter(Boolean)
  const kept = parts.filter(part => {
    const lower = part.toLowerCase()
    if (kind === 'type' && lower.startsWith('type:')) return false
    if (kind === 'date' && lower.startsWith('date:')) return false
    if (kind === 'tag' && part.startsWith('#')) return false
    return true
  })
  keyword.value = kept.join(' ')
  if (keyword.value.trim()) {
    doSearch()
  } else {
    result.value = emptyResult()
    searched.value = false
    refreshHistory()
  }
}

const searchByGroup = (key: string) => {
  const valid = typeFilterOptions.some(o => o.value === key)
  typeFilter.value = valid ? (key as typeof typeFilter.value) : 'all'
  keyword.value = valid ? `type:${key} ` : ''
  doSearch()
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
  // 空输入时“最近打开”同样可参与键盘导航
  if (!keyword.value.trim()) {
    for (const r of recentItems.value) {
      items.push({ key: `recent-${r.key}`, label: r.title || '未命名', group: '最近打开', type: r.kind, run: () => openRecent(r) })
    }
  }
  if (shouldShowGroup('bookmarks')) {
    for (const b of result.value.bookmarks) {
      items.push({ key: `b-${b.id}`, label: b.title || b.url, group: '收藏', type: 'bookmark', run: () => openRecent({ kind: 'bookmark', id: b.id, title: b.title || b.url, url: b.url }) })
    }
  }
  if (shouldShowGroup('notes')) {
    for (const n of result.value.notes) {
      items.push({ key: `n-${n.id}`, label: n.title || '无标题', group: '便签', type: 'note', run: () => openRecent({ kind: 'note', id: n.id, title: n.title || '无标题' }) })
    }
  }
  if (shouldShowGroup('files')) {
    for (const f of result.value.files) {
      items.push({ key: `f-${f.id}`, label: f.originalName, group: '云盘文件', type: 'file', run: () => openRecent({ kind: 'file', id: f.id, title: f.originalName }) })
    }
  }
  if (shouldShowGroup('vault')) {
    for (const v of result.value.vault) {
      items.push({ key: `v-${v.id}`, label: v.name, group: '密码库', type: 'vault', run: () => handleJump('vault', v.directoryId) })
    }
  }
  if (shouldShowGroup('tags')) {
    for (const t of result.value.tags) {
      items.push({ key: `t-${t.id}`, label: t.name, group: '标签', type: 'tag', run: () => handleJump('bookmarks', null) })
    }
  }
  if (shouldShowGroup('tracks')) {
    for (const t of result.value.tracks) {
      items.push({ key: `track-${t.trackId}`, label: t.name, group: '音乐', type: 'track', run: () => handleJump('music', null) })
    }
  }
  return items
})

const navIndex = (key: string) => navItems.value.findIndex(i => i.key === key)

const navGroups = computed(() => {
  const seen = new Set<string>()
  const groups: string[] = []
  for (const item of navItems.value) {
    if (!seen.has(item.group)) {
      seen.add(item.group)
      groups.push(item.group)
    }
  }
  return groups
})
const cycleGroup = (forward: boolean) => {
  const groups = navGroups.value
  if (!groups.length) return
  const current = activeIndex.value >= 0 ? navItems.value[activeIndex.value]?.group : groups[groups.length - 1]
  const idx = Math.max(0, groups.indexOf(current || ''))
  const step = forward ? 1 : -1
  const nextGroup = groups[(idx + step + groups.length) % groups.length]
  const firstIdx = navItems.value.findIndex(i => i.group === nextGroup)
  if (firstIdx >= 0) {
    activeIndex.value = firstIdx
    void scrollActiveIntoView()
  }
}

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
  } else if (e.key === 'Tab') {
    e.preventDefault()
    cycleGroup(!e.shiftKey)
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
      <!-- 标题（图标放渐变外：JGradientText 走 text prop，slot 拼接对转发链不可靠）
           标题用「搜索」而非「命令面板」（报告 IA-4）：抽屉的主体是跨模块搜索
           （placeholder 与有输入时的结果区都是搜索），命令列表只在空输入时作为回退出现；
           且本项目面向个人工作台用户，不预设其熟悉 Command Palette 这一开发者术语。 -->
      <div class="search-header">
        <div class="search-title">
          <NIcon :component="Search" size="16" class="search-title-icon" />
          <JGradientText
            text="搜索"
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

      <!-- 结果类型筛选（命令区始终展示） -->
      <div v-if="searched && total() > 0" class="type-filter">
        <button
          v-for="opt in typeFilterOptions" :key="opt.value"
          type="button" :class="['type-filter-chip', 'jnclub-bouncy', { active: typeFilter === opt.value }]"
          @click="typeFilter = opt.value"
        >{{ opt.label }}</button>
      </div>

      <!-- 已识别的搜索语法 -->
      <div v-if="syntaxChips.length" class="syntax-chips">
        <button
          v-for="chip in syntaxChips" :key="chip.kind"
          type="button" class="syntax-chip jnclub-bouncy" :title="`移除${chip.label}`" @click="removeSyntax(chip.kind)"
        >{{ chip.label }} <span class="syntax-close">×</span></button>
      </div>

      <!-- 搜索历史（空输入时展示） -->
      <div v-if="!keyword.trim() && displayedHistory.length" class="history-section">
        <div class="history-head">
          <span class="history-title">最近搜索</span>
          <button type="button" class="history-clear" @click="clearHistory">清空</button>
        </div>
        <div class="history-chips">
          <button
            v-for="h in displayedHistory" :key="h"
            type="button" class="history-chip jnclub-bouncy" @click="searchByHistory(h)"
          >{{ h }}</button>
        </div>
      </div>

      <!-- 最近打开（空输入时展示，本地记录） -->
      <div v-if="!keyword.trim() && recentItems.length" class="history-section">
        <div class="history-head">
          <span class="history-title">最近打开</span>
          <button type="button" class="history-clear" @click="clearRecent">清空</button>
        </div>
        <div class="recent-list">
          <div
            v-for="r in recentItems" :key="r.key"
            :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`recent-${r.key}`) }]" @click="openRecent(r)"
          >
            <NIcon :component="RECENT_KIND_META[r.kind].icon" size="15" class="item-fallback" />
            <div class="item-main">
              <div class="item-title hl-text">{{ r.title || '未命名' }}</div>
              <span class="recent-sub">{{ recentSub(r) }}</span>
            </div>
            <ArrowRight :size="14" class="item-arrow" />
          </div>
        </div>
      </div>

      <!-- 服务端搜索建议（空输入时展示快捷入口） -->
      <div v-if="!keyword.trim() && (suggest.groups.length || suggest.commands.length)" class="suggest-section">
        <div v-if="suggest.groups.length" class="suggest-block">
          <span class="history-title">搜索范围</span>
          <div class="history-chips">
            <button
              v-for="g in suggest.groups" :key="g.key"
              type="button" class="history-chip jnclub-bouncy" @click="searchByGroup(g.key)"
            >{{ g.label }}</button>
          </div>
        </div>
        <div v-if="suggest.commands.length" class="suggest-block">
          <span class="history-title">快捷命令</span>
          <div class="history-chips">
            <button
              v-for="c in suggest.commands" :key="c.key"
              type="button" class="history-chip jnclub-bouncy" @click="runCommand(c.key)"
            >{{ c.label }}</button>
          </div>
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

        <!-- 无结果（含「当前类型筛选下为空」） -->
        <div v-else-if="searched && visibleTotal === 0" class="no-result">
          <JEmptyState
            variant="search"
            :message="total() === 0 ? '没有找到相关内容' : '此类型下没有匹配内容'"
            :hint="total() === 0 ? '换个关键词试试' : '切换到其他类型，或换个关键词'"
            :show-cta="false"
            class="search-empty"
          />
        </div>

        <!-- 结果 -->
        <div v-else class="search-results">
          <!-- 收藏 -->
          <div v-if="shouldShowGroup('bookmarks') && result.bookmarks.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Bookmark" size="14" /> 收藏
              <span class="group-count">{{ result.bookmarks.length }}</span>
            </div>
            <div
              v-for="(b, idx) in result.bookmarks" :key="b.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`b-${b.id}`) }]" @click="openRecent({ kind: 'bookmark', id: b.id, title: b.title || b.url, url: b.url })"
              :style="{ animationDelay: `calc(min(${idx} * var(--dur-stagger), var(--dur-slow)))` }"
            >
              <img v-if="b.icon" :src="b.icon" class="item-icon" loading="lazy" decoding="async" @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')" />
              <NIcon v-else :component="Bookmark" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(b.title || b.url, b.highlights, b.title ? 'title' : 'url')" />
                <NEllipsis class="item-sub">{{ b.url }}</NEllipsis>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 便签 -->
          <div v-if="shouldShowGroup('notes') && result.notes.length" class="result-group">
            <div class="group-title">
              <NIcon :component="StickyNote" size="14" /> 便签
              <span class="group-count">{{ result.notes.length }}</span>
            </div>
            <div
              v-for="(n, idx) in result.notes" :key="n.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`n-${n.id}`) }]" @click="openRecent({ kind: 'note', id: n.id, title: n.title || '无标题' })"
              :style="{ animationDelay: `calc(min(${idx} * var(--dur-stagger), var(--dur-slow)))` }"
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
          <div v-if="shouldShowGroup('files') && result.files.length" class="result-group">
            <div class="group-title">
              <NIcon :component="FileText" size="14" /> 云盘文件
              <span class="group-count">{{ result.files.length }}</span>
            </div>
            <div
              v-for="(f, idx) in result.files" :key="f.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`f-${f.id}`) }]" @click="openRecent({ kind: 'file', id: f.id, title: f.originalName })"
              :style="{ animationDelay: `calc(min(${idx} * var(--dur-stagger), var(--dur-slow)))` }"
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
          <div v-if="shouldShowGroup('vault') && result.vault.length" class="result-group">
            <div class="group-title">
              <NIcon :component="KeyRound" size="14" /> 密码库
              <span class="group-count">{{ result.vault.length }}</span>
            </div>
            <div
              v-for="(v, idx) in result.vault" :key="v.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`v-${v.id}`) }]" @click="handleJump('vault', v.directoryId)"
              :style="{ animationDelay: `calc(min(${idx} * var(--dur-stagger), var(--dur-slow)))` }"
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
          <div v-if="shouldShowGroup('tags') && result.tags.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Tag" size="14" /> 标签
              <span class="group-count">{{ result.tags.length }}</span>
            </div>
            <div
              v-for="(t, idx) in result.tags" :key="t.id"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`t-${t.id}`) }]" @click="handleJump('bookmarks', null)"
              :style="{ animationDelay: `calc(min(${idx} * var(--dur-stagger), var(--dur-slow)))` }"
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
          <div v-if="shouldShowGroup('tracks') && result.tracks.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Music" size="14" /> 音乐
              <span class="group-count">{{ result.tracks.length }}</span>
            </div>
            <div
              v-for="(t, idx) in result.tracks" :key="t.trackId"
              :class="['result-item', 'jnclub-bouncy', { 'search-nav-active': activeIndex === navIndex(`track-${t.trackId}`) }]" @click="handleJump('music', null)"
              :style="{ animationDelay: `calc(min(${idx} * var(--dur-stagger), var(--dur-slow)))` }"
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
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--text-1);
}
.search-title-icon {
  color: var(--brand-text);
}
.search-hint {
  font-size: var(--fs-xs);
  color: var(--text-3);
  background: var(--glass-chip-bg);
  border: 1px solid var(--surface-border);
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
  animation: search-item-in var(--dur-slow) var(--ease) forwards;
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
  border-radius: var(--radius-2xs);
  flex-shrink: 0;
}
.item-fallback {
  color: var(--brand-text);
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
  color: var(--brand-text);
  border-radius: var(--radius-2xs);
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
.history-clear:hover { color: var(--brand-text); }
.history-chips { display: flex; flex-wrap: wrap; gap: 8px; }
/* 抽屉内 chip 的统一档：4px 12px / --fs-sm（另见下方 .type-filter-chip 与 .syntax-chip 的说明，P1-10） */
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
.history-chip:hover { border-color: var(--brand-text); color: var(--brand-text); }
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
.cmd-ic { color: var(--brand-text); flex-shrink: 0; }
.cmd-label { flex: 1; }
.cmd-group { font-size: var(--fs-xs); color: var(--text-3); }

/* 结果类型筛选 */
.type-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
/* chip 规格只保留两级（P1-10）：
     抽屉内（本文件三个 chip，均为 4px 12px / --fs-sm）—— .type-filter-chip / .syntax-chip / .history-chip
     全局主筛选（5px 14px / --fs-sm）—— .j-filter-chip（JFilterBar.vue:63）与 .chip（assets/main.css）
   改前这里是 3px 10px / --fs-xs(11px)，与同屏的 .history-chip（4px 12px / --fs-sm）并排时
   高度差 2px、字号差 1px，读起来像两个不相干的控件；且 11px 是本项目留给元信息（如 .item-meta）
   的字号，用在可点击控件上偏小。 */
.type-filter-chip {
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-chip-border);
  background: var(--glass-chip-bg);
  color: var(--glass-chip-text);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), color var(--dur) var(--ease), background var(--dur) var(--ease);
}
.type-filter-chip:hover,
.type-filter-chip.active {
  border-color: var(--brand-text);
  color: var(--brand-text);
}
.type-filter-chip.active {
  background: var(--brand-soft);
}

/* 已识别搜索语法标签。规格与 .type-filter-chip / .history-chip 一致（见上，P1-10）；
   三者共用几何、只靠色相区分语义：本 chip 常驻品牌色（--brand 描边 + --brand-soft 底）
   表示「这是你已输入并解析成功的条件，点 × 移除」，另两个是中性档「可选的筛选 / 建议」。 */
.syntax-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.syntax-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--brand);
  background: var(--brand-soft);
  color: var(--brand-text);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}
.syntax-chip:hover {
  background: var(--brand);
  color: var(--text-on-brand);
}
.syntax-close {
  font-size: var(--fs-base);
  line-height: 1;
}

/* 服务端搜索建议（空输入快捷入口） */
.suggest-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}
.suggest-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 新增类型结果的辅助信息 */
.item-meta {
  font-size: var(--fs-xs);
  color: var(--text-3);
  background: var(--glass-chip-bg);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-pill);
  padding: 0 6px;
  white-space: nowrap;
}
.item-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

/* 最近打开 */
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.recent-sub {
  font-size: var(--fs-sm);
  color: var(--text-3);
}
</style>