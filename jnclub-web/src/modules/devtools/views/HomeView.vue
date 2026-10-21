<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDevtoolsStore } from '../stores/tools'
import { useRecentTools } from '../composables/useRecentTools'
import { ALL_TOOLS, CATEGORY_META, type CategoryId } from '../types'
import type { ToolTab } from '../types'
import { Search, Clock, Settings, Keyboard, ChevronRight } from 'lucide-vue-next'
import { HOME_CHEATSHEET } from '../shortcuts'
import { useKeyboardShortcut } from '../composables/useKeyboardShortcut'
import Kbd from '../components/Kbd.vue'

defineOptions({ name: 'DevtoolsHome' })

const tools = useDevtoolsStore()
const { recentTools, clearRecent } = useRecentTools()

/* ─── Search ─── */
const searchQuery = ref('')
const searchInput = ref<HTMLInputElement | null>(null)

/* ─── Category collapse state (localStorage) ─── */
const COLLAPSE_KEY = 'jnclub-devtools:home-collapsed'
function readCollapsed(): Record<string, boolean> {
  try { const raw = localStorage.getItem(COLLAPSE_KEY); return raw ? JSON.parse(raw) : {} }
  catch { return {} }
}
function writeCollapsed(val: Record<string, boolean>) {
  try { localStorage.setItem(COLLAPSE_KEY, JSON.stringify(val)) } catch {}
}
const collapsed = ref<Record<string, boolean>>(readCollapsed())

function toggleCategory(id: string) {
  collapsed.value[id] = !collapsed.value[id]
  writeCollapsed({ ...collapsed.value })
}

/* ─── Tool categorization ─── */
const toolCategories = computed(() => {
  const map: Record<string, ToolTab[]> = {}
  for (const t of ALL_TOOLS.filter(t => !t.isSystem)) {
    const catId = t.category || 'other'
    if (!map[catId]) map[catId] = []
    map[catId].push(t)
  }
  const cats: CategoryId[] = ['format', 'network', 'efficiency']
  return cats
    .filter(c => map[c]?.length)
    .map(c => ({ id: c, tools: (map[c] || []).sort((a, b) => (a.order ?? 99) - (b.order ?? 99)) }))
})

const systemTools = computed(() => ALL_TOOLS.filter(t => t.isSystem))

/* ─── Live data ─── */
const liveData = computed(() => {
  const toolCount = ALL_TOOLS.filter(t => !t.isSystem).length
  const catCount = toolCategories.value.length
  const recentCount = recentTools.value.length
  return { toolCount, catCount, recentCount }
})

/* ─── Search filtering ─── */
const hasActiveFilter = computed(() => searchQuery.value.trim().length > 0)

const filteredTools = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return null
  return ALL_TOOLS.filter(t => {
    const fields = [t.label, t.desc, ...(t.keywords || []), CATEGORY_META[t.category || 'other']?.label]
    return fields.some(f => f?.toLowerCase().includes(q))
  })
})

/* ─── Actions ─── */
function openTool(id: string) { tools.setActiveTab(id) }
function onSearchInput() { /* reactive enough */ }

// 搜索框占位提示写的是 ⌘K，必须真的能按（scope 限定在启动台激活时）
useKeyboardShortcut('home.search', () => searchInput.value?.focus(), { skipWhenEditing: false })

/* ─── Relative time for recent tools ─── */
function relativeTime(ts: number): string {
  const diff = Date.now() - ts
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`
  return `${Math.floor(diff / 604800000)} 周前`
}

/* onUnmounted: no timers to clean */

/* ─── Stagger animation helpers ─── */
function cardDelay(index: number): string {
  return `${Math.min(index * 30, 200)}ms`
}
</script>

<template>
  <div class="home-view">
    <!-- Ambient background -->
    <div class="bg-grid"></div>
    <div class="bg-glow"></div>

    <div class="home-content">
      <!-- ─── Hero: left-aligned + live data + search integrated ─── -->
      <div class="hero-area">
        <div class="hero-top">
          <h1 class="hero-title">
            <span class="hero-logo">✦</span>
            <span>开发者工具</span>
          </h1>
          <p class="hero-subtitle">JSON、格式互转、HTTP、Cron —— 随手可用</p>
          <p class="hero-data">
            <span class="data-count">{{ liveData.toolCount }}</span> 个工具 ·
            <span class="data-count">{{ liveData.catCount }}</span> 个分类 ·
            <span v-if="liveData.recentCount > 0" class="data-count">{{ liveData.recentCount }}</span>
            <span v-else>0</span> 最近使用
          </p>
        </div>

        <!-- Search bar (integrated into hero block, same width as grid) -->
        <div class="search-bar" @click="searchInput?.focus()">
          <span class="search-icon"><Search :size="18" /></span>
          <input
            ref="searchInput"
            v-model="searchQuery"
            class="search-input"
            placeholder="搜索工具…"
            @input="onSearchInput"
          />
          <kbd v-if="!searchQuery" class="search-hint">⌘K</kbd>
          <button v-else class="search-clear" @click.stop="searchQuery = ''">✕</button>
        </div>
      </div>

      <!-- ─── Search results ─── -->
      <div v-if="hasActiveFilter && filteredTools" class="search-results">
        <div
          v-for="t in filteredTools"
          :key="t.id"
          class="search-result-item"
          @click="openTool(t.id)"
        >
          <span class="sr-icon"><component :is="t.icon" :size="18" /></span>
          <span class="sr-label">{{ t.label }}</span>
          <span class="sr-desc">{{ t.desc }}</span>
        </div>
        <div v-if="filteredTools.length === 0" class="search-empty">无匹配结果</div>
      </div>

      <!-- ─── Recent tools (搜索时隐藏) ─── -->
      <div v-if="!hasActiveFilter" class="section recent-section">
        <div v-if="recentTools.length > 0">
          <div class="section-header">
            <span class="section-icon"><Clock :size="16" /></span>
            <span class="section-title">继续上次</span>
            <button class="section-action" @click="clearRecent">清除记录</button>
          </div>
          <div class="recent-row">
            <button
              v-for="rt in recentTools.slice(0, 6)"
              :key="rt.toolId"
              class="recent-chip"
              @click="openTool(rt.toolId)"
            >
              <span class="recent-chip-icon"><component :is="rt.tool?.icon || Clock" :size="14" /></span>
              <span class="recent-chip-label">{{ rt.tool?.label || rt.toolId }}</span>
              <span class="recent-chip-time">{{ relativeTime(rt.lastUsedAt) }}</span>
            </button>
          </div>
        </div>
        <!-- 空态占位：不要整段隐藏留空白 -->
        <div v-else class="recent-empty">
          <span class="section-icon"><Clock :size="16" /></span>
          <span class="recent-empty-text">还没有最近使用 · 打开任意工具即记录于此</span>
        </div>
      </div>

      <!-- ─── 分类工具网格 ─── -->
      <div v-if="!hasActiveFilter" class="categories-area">
        <div
          v-for="cat in toolCategories"
          :key="cat.id"
          class="category-section"
        >
          <div class="section-header collapsible" @click="toggleCategory(cat.id)">
            <span class="section-icon" :style="{ color: CATEGORY_META[cat.id].color }">
              <component :is="CATEGORY_META[cat.id].icon" :size="16" />
            </span>
            <span class="section-title">{{ CATEGORY_META[cat.id].label }}</span>
            <span class="section-count">{{ cat.tools.length }}</span>
            <span class="collapse-arrow" :class="{ open: !collapsed[cat.id] }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M9 18l6-6-6-6"/></svg>
            </span>
          </div>
          <div v-if="!collapsed[cat.id]" class="category-grid">
            <div
              v-for="(t, idx) in cat.tools"
              :key="t.id"
              class="tool-card"
              :class="{ hero: t.pinned }"
              @click="openTool(t.id)"
              :style="{ '--card-delay': cardDelay(idx) }"
            >
              <div class="card-icon-wrap" :style="{ background: CATEGORY_META[cat.id].color }">
                <span class="card-icon"><component :is="t.icon" :size="20" /></span>
              </div>
              <div class="card-body">
                <div class="card-title">{{ t.label }}</div>
                <div class="card-desc">{{ t.desc }}</div>
              </div>
              <!-- chevron on right, slides right on hover -->
              <span class="card-chevron"><ChevronRight :size="14" /></span>
            </div>
          </div>
        </div>
      </div>

      <!-- ─── 系统工具 ─── -->
      <div v-if="!hasActiveFilter" class="section system-section">
        <div class="section-header">
          <span class="section-icon"><Settings :size="16" /></span>
          <span class="section-title">系统工具</span>
        </div>
        <div class="system-row">
          <button
            v-for="t in systemTools"
            :key="t.id"
            class="system-chip"
            @click="openTool(t.id)"
          >
            <span class="system-chip-icon"><component :is="t.icon" :size="14" /></span>
            <span class="system-chip-label">{{ t.label }}</span>
          </button>
        </div>
      </div>

      <!-- ─── 快捷键速查 ─── -->
      <div v-if="!hasActiveFilter" class="section shortcuts-section">
        <div class="section-header">
          <span class="section-icon"><Keyboard :size="16" /></span>
          <span class="section-title">快捷键速查</span>
        </div>
        <div class="shortcuts-list">
          <div v-for="s in HOME_CHEATSHEET" :key="s.action" class="shortcut-item">
            <Kbd :action="s.action" />
            <span class="shortcut-desc">{{ s.desc }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ═══ Home view root ═══ */
.home-view {
  height: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  position: relative;
  overflow-y: auto;
  /* 网格/光晕用 absolute 限定在本面板内；jnx 原来是 fixed（整窗），
     迁进 JNClub 后 fixed 会盖到左侧导航栏上 */
  overflow-x: hidden;
  padding: 28px 24px 40px;
}

/* ─── Ambient background ─── */
.bg-grid {
  position: absolute; inset: 0; pointer-events: none;
  mask-image: linear-gradient(to bottom, #000 0%, transparent 70%);
  -webkit-mask-image: linear-gradient(to bottom, #000 0%, transparent 70%);
  background-image:
    repeating-linear-gradient(0deg, transparent, transparent 39px, var(--border) 40px),
    repeating-linear-gradient(90deg, transparent, transparent 39px, var(--border) 40px);
}
.bg-glow {
  position: absolute; top: 0; left: 20%; /* shifted left to follow the left-aligned hero */
  width: 600px; height: 450px; pointer-events: none;
  background: radial-gradient(ellipse at center, var(--brand-soft) 0%, transparent 60%);
}

/* ─── Content container (unified max-width, left-aligned) ─── */
.home-content {
  position: relative; z-index: 1;
  display: flex; flex-direction: column;
  width: 100%; max-width: 700px; /* wider than before for better grid */
}

/* ═══ Hero area (left-aligned) ═══ */
.hero-area {
  display: flex; flex-direction: column;
  gap: 14px;
  padding-bottom: 8px;
}
.hero-top {
  display: flex; flex-direction: column;
  align-items: flex-start; /* left-aligned */
}
.hero-title {
  font-size: 26px; font-weight: 800; line-height: 1.2;
  display: flex; align-items: center; gap: 6px;
  color: var(--text-1);
}
.hero-logo {
  background: linear-gradient(135deg, var(--brand), var(--brand-hover));
  -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
  font-size: 28px;
}
.hero-subtitle {
  font-size: 14px; color: var(--text-2); font-weight: 400;
  margin: 2px 0 0 0;
}
.hero-data {
  font-size: 12px; color: var(--text-3); margin: 4px 0 0 0;
  letter-spacing: 0.2px;
}
.data-count {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--brand);
}

/* ═══ Search bar ═══ */
.search-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: text;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.search-bar:focus-within {
  border-color: var(--brand-hover);
  box-shadow: 0 0 0 3px var(--brand-soft);
}
.search-icon { width: 18px; height: 18px; flex-shrink: 0; color: var(--text-3); display: flex; }
.search-icon :deep(svg) { width: 100%; height: 100%; }
.search-input {
  flex: 1; border: none; outline: none; background: transparent;
  font-size: 14px; color: var(--text-1); font-family: inherit;
}
.search-input::placeholder { color: var(--text-3); }
.search-hint {
  font-size: 11px; padding: 2px 7px; border-radius: 4px;
  background: var(--border); color: var(--text-3); font-family: inherit;
}
.search-clear {
  border: none; background: transparent; color: var(--text-3);
  cursor: pointer; font-size: 14px; padding: 2px 6px; border-radius: 4px;
}
.search-clear:hover { background: var(--hover-bg); color: var(--text-1); }

/* ─── Search results ─── */
.search-results {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px; overflow: hidden;
}
.search-result-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; cursor: pointer;
  transition: background 0.1s;
}
.search-result-item:hover { background: var(--hover-bg); }
.sr-icon { width: 18px; height: 18px; flex-shrink: 0; color: var(--brand); display: flex; }
.sr-icon :deep(svg) { width: 100%; height: 100%; }
.sr-label { font-size: 14px; font-weight: 600; color: var(--text-1); white-space: nowrap; }
.sr-desc { font-size: 12px; color: var(--text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.search-empty { text-align: center; padding: 24px; color: var(--text-3); font-size: 14px; }

/* ═══ Section common ═══ */
.section { margin-top: 18px; }

.section-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 10px;
}
.section-header.collapsible { cursor: pointer; user-select: none; }
.section-icon { width: 16px; height: 16px; flex-shrink: 0; display: flex; }
.section-icon :deep(svg) { width: 100%; height: 100%; }
.section-title {
  font-size: 12px; font-weight: 600; color: var(--text-2);
  text-transform: uppercase; letter-spacing: 0.4px; flex: 1;
}
.section-count {
  font-size: 11px; font-family: var(--font-mono);
  color: var(--text-3); background: var(--hover-bg);
  padding: 1px 6px; border-radius: 6px;
}
.section-action {
  font-size: 11px; color: var(--text-3); background: none; border: none;
  cursor: pointer; padding: 2px 6px; border-radius: 4px;
}
.section-action:hover { color: var(--brand); background: var(--hover-bg); }

.collapse-arrow {
  width: 14px; height: 14px; color: var(--text-3); display: flex;
  transition: transform 0.2s ease;
}
.collapse-arrow.open { transform: rotate(90deg); }

/* ═══ Recent tools ═══ */
.recent-row {
  display: flex; gap: 8px; flex-wrap: wrap;
}
.recent-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer; font-family: inherit; font-size: 13px;
  color: var(--text-1);
  transition: all 0.15s ease;
  white-space: nowrap; /* prevent chip break */
}
.recent-chip:hover {
  border-color: var(--brand-hover);
  background: var(--hover-bg);
}
.recent-chip-icon { width: 14px; height: 14px; display: flex; color: var(--brand); }
.recent-chip-icon :deep(svg) { width: 100%; height: 100%; }
.recent-chip-label { font-weight: 500; }
.recent-chip-time {
  font-size: 11px; color: var(--text-3); margin-left: 2px;
}
.recent-empty {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 0; color: var(--text-3);
}
.recent-empty .section-icon { opacity: 0.5; }
.recent-empty-text { font-size: 13px; }

/* ═══ Category tool grid (UNIFIED: hero span2 / normal span1 / auto-fill) ═══ */
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}

/* ─── Tool card base ─── */
.tool-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
  position: relative;
  overflow: hidden;

  /* Stagger entrance animation */
  animation: card-enter 0.35s ease both;
  animation-delay: var(--card-delay, 0ms);
}
@keyframes card-enter {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ─── Tool card hero (pinned tools) ─── */
.tool-card.hero {
  grid-column: span 2;
  padding: 18px 18px;
  border-radius: 12px;
}
.tool-card.hero .card-title { font-size: 17px; }
.tool-card.hero .card-icon-wrap { width: 48px; height: 48px; border-radius: 14px; }
.tool-card.hero .card-icon { width: 24px; height: 24px; }
.tool-card.hero .card-chevron { width: 18px; height: 18px; }

/* ─── Tool card hover ─── */
.tool-card:hover {
  border-color: var(--brand-hover);
  box-shadow: 0 4px 16px var(--shadow-2);
  transform: translateY(-2px);
}
.tool-card:hover .card-icon-wrap {
  transform: translateY(-1px) scale(1.05);
}
.tool-card:hover .card-chevron {
  transform: translateX(4px);
  opacity: 1;
}

/* ─── Card icon wrap (size + category color) ─── */
.card-icon-wrap {
  width: 40px; height: 40px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 10px;
  transition: transform 0.2s ease;
}
.card-icon { width: 20px; height: 20px; color: #FFF; display: flex; }
.card-icon :deep(svg) { width: 100%; height: 100%; }

/* ─── Card body ─── */
.card-body { flex: 1; min-width: 0; }
.card-title { font-size: 15px; font-weight: 700; color: var(--text-1); margin-bottom: 2px; line-height: 1.3; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-desc { font-size: 12px; color: var(--text-2); line-height: 1.4; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; }

/* ─── Card chevron (slides right on hover) ─── */
.card-chevron {
  width: 14px; height: 14px; flex-shrink: 0;
  color: var(--text-3);
  opacity: 0.3;
  transition: transform 0.2s ease, opacity 0.2s ease;
  display: flex;
}
.card-chevron :deep(svg) { width: 100%; height: 100%; }

/* ═══ System tools ─── */
.system-section {
  padding: 16px 0;
  border-top: 1px solid var(--border);
}
.system-row {
  display: flex; gap: 10px; flex-wrap: wrap;
}
.system-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer; font-family: inherit; font-size: 13px;
  color: var(--text-2);
  transition: all 0.15s ease;
}
.system-chip:hover {
  border-color: var(--brand-hover);
  color: var(--text-1);
  background: var(--hover-bg);
}
.system-chip-icon { width: 14px; height: 14px; display: flex; }
.system-chip-icon :deep(svg) { width: 100%; height: 100%; }
.system-chip-label { font-weight: 500; }

/* ═══ Shortcuts ─── */
.shortcuts-section {
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.shortcuts-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}
.shortcut-item { display: flex; align-items: center; gap: 8px; }
.shortcut-desc { font-size: 13px; color: var(--text-2); }

/* ═══ Mobile ═══ */
@media (max-width: 767px) {
  .home-view { padding: 18px 14px 32px; }
  .hero-title { font-size: 21px; }
  .hero-logo { font-size: 23px; }
  .hero-subtitle { font-size: 13px; }
  .bg-glow { width: 320px; height: 260px; left: 0; }
  .category-grid { grid-template-columns: 1fr; }
  .tool-card.hero { grid-column: span 1; }
  .shortcuts-list { grid-template-columns: 1fr; }
}

/* ═══ Reduced motion ═══ */
@media (prefers-reduced-motion: reduce) {
  .tool-card { animation: none; opacity: 1; transform: none; }
  .tool-card:hover { transform: none; }
  .card-icon-wrap { transition: none; }
  .tool-card:hover .card-icon-wrap { transform: none; }
  .card-chevron { transition: none; }
  .tool-card:hover .card-chevron { transform: none; }
}
</style>
