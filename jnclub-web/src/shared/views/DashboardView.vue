<script setup lang="ts">
/**
 * DashboardView.vue — 概览数据看板
 * 统计卡片（收藏/便签/文件/密码/标签/回收站）+ 磁盘占用 + 密码库指纹健康 + 最近动态 + 快捷入口
 */
import { ref, computed, onMounted, watch } from 'vue'
import { NIcon, NButton, NDrawer, NSwitch, NDropdown } from 'naive-ui'
import {
  Bookmark, StickyNote, Cloud, KeyRound, Tag, Trash2, HardDrive,
  ShieldCheck, AlertTriangle, ArrowRight, LayoutDashboard, RefreshCw, TrendingUp,
  ListTodo, Settings2, GripVertical, Sparkles, Share2, BookOpen, MoreHorizontal,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { formatRelativeTime } from '../../modules/bookmark/composables/formatDate'
import ExportModal from '../../modules/bookmark/components/ExportModal.vue'
import ImportModal from '../../modules/bookmark/components/ImportModal.vue'
import FullBackupModal from '../../modules/bookmark/components/FullBackupModal.vue'
import DedupModal from '../../modules/bookmark/components/DedupModal.vue'
import ShareManagerDrawer from '../components/ShareManagerDrawer.vue'
import ReadingModal from '../../modules/bookmark/components/ReadingModal.vue'
import JStatCard from '../components/ui/JStatCard.vue'
import JSkeletonGrid from '../components/ui/JSkeletonGrid.vue'
import JErrorState from '../components/ui/JErrorState.vue'

interface StatsSummary {
  counts: {
    bookmarks: number
    notes: number
    files: number
    vault: number
    tags: number
    recycle: { bookmark: number; note: number; file: number; vault: number }
  }
  disk: {
    totalSize: number
    fileCount: number
    byDirectory: Array<{ directoryId: number; name: string; count: number; size: number }>
  }
  recent: {
    bookmarks: Array<{ id: number; title: string; url: string; createTime: string }>
    notes: Array<{ id: number; title: string; createTime: string }>
    files: Array<{ id: number; originalName: string; size: number; createTime: string }>
  }
  vault: { entries: number; duplicateCount: number }
  todos: { active: number; dueToday: number; overdue: number }
  readLater: {
    count: number
    list: Array<{ id: number; title: string; url: string; progress: number; readAt: string }>
  }
}

const router = useRouter()
const loading = ref(true)
const error = ref(false)
const data = ref<StatsSummary | null>(null)
const showExport = ref(false)
const showImport = ref(false)
const showBackup = ref(false)
const showDedup = ref(false)
const showShareManager = ref(false)

/** 概览低频操作统一收进「更多」 */
const moreOptions = [
  { label: '全量备份', key: 'backup' },
  { label: '数据导出', key: 'export' },
  { label: '数据导入', key: 'import' },
  { label: '查重', key: 'dedup' },
]

const handleMoreSelect = (key: string) => {
  if (key === 'backup') showBackup.value = true
  else if (key === 'export') showExport.value = true
  else if (key === 'import') showImport.value = true
  else if (key === 'dedup') showDedup.value = true
}

/* ─── 继续阅读（稍后读） ─── */
const readingShow = ref(false)
const readingUrl = ref('')
const readingId = ref<number | null>(null)
const goReadLater = (r: { id: number; title: string; url: string }) => {
  readingUrl.value = r.url
  readingId.value = r.id
  readingShow.value = true
}

const fetchSummary = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await axios.get('/api/stats/summary')
    if (res.data.code === 200) data.value = res.data.data
    else error.value = true
  } catch { error.value = true }
  finally { loading.value = false }
}

/* ─── 数据趋势 ─── */
interface TrendPoint {
  month: string
  bookmarks: number
  notes: number
  files: number
  vault: number
}
const trendData = ref<TrendPoint[]>([])

const TREND_SERIES: Array<{ key: keyof Omit<TrendPoint, 'month'>; label: string; color: string }> = [
  { key: 'bookmarks', label: '收藏', color: 'var(--module-bookmark)' },
  { key: 'notes', label: '便签', color: 'var(--module-note)' },
  { key: 'files', label: '云盘', color: 'var(--module-file)' },
  { key: 'vault', label: '密码库', color: 'var(--module-vault)' },
]

const fetchTrend = async () => {
  try {
    const res = await axios.get('/api/stats/trend', { params: { months: 6 } })
    if (res.data.code === 200) trendData.value = res.data.data
  } catch { /* 趋势失败不影响主看板 */ }
}

/** 趋势图最大刻度值（取 4 个系列最大值向上取整，至少 1） */
const trendMax = computed(() => {
  const all = trendData.value.flatMap((p) => TREND_SERIES.map((s) => p[s.key] ?? 0))
  const max = Math.max(...all, 1)
  // 圆整到友好刻度（1/2/5 × 10^n）
  if (max <= 1) return 1
  const pow = Math.pow(10, Math.floor(Math.log10(max)))
  const norm = max / pow
  const step = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10
  return step * pow
})

const trendTotal = computed(() => {
  const totals: Record<string, number> = { bookmarks: 0, notes: 0, files: 0, vault: 0 }
  for (const p of trendData.value) {
    for (const s of TREND_SERIES) totals[s.key] += p[s.key] ?? 0
  }
  return totals
})

/** 柱高百分比（相对最大值） */
function trendBarHeight(v: number): string {
  return `${Math.max(2, (v / trendMax.value) * 100)}%`
}

/** 月份短标签：2025-03 → 3月 */
function monthLabel(month: string): string {
  const m = month.split('-')[1]
  return m ? `${Number(m)}月` : month
}

onMounted(() => {
  fetchSummary()
  fetchTrend()
})

/* ─── 统计卡片 ─── */
const statCards = computed(() => {
  const c = data.value?.counts
  if (!c) return []
  const recycleTotal = c.recycle.bookmark + c.recycle.note + c.recycle.file + c.recycle.vault
  return [
    { key: 'bookmarks', label: '收藏', value: c.bookmarks, icon: Bookmark, to: '/?module=bookmarks' },
    { key: 'notes', label: '便签', value: c.notes, icon: StickyNote, to: '/?module=notes' },
    { key: 'files', label: '云盘文件', value: c.files, icon: Cloud, to: '/?module=files' },
    { key: 'vault', label: '密码库', value: c.vault, icon: KeyRound, to: '/?module=vault' },
    { key: 'tags', label: '标签', value: c.tags, icon: Tag, to: '/?module=bookmarks' },
    { key: 'recycle', label: '回收站', value: recycleTotal, icon: Trash2, to: '/recycle', warn: recycleTotal > 0 },
  ]
})

/* ─── 磁盘 ─── */
function fmtSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes >= 1 << 30) return (bytes / (1 << 30)).toFixed(2) + ' GB'
  if (bytes >= 1 << 20) return (bytes / (1 << 20)).toFixed(1) + ' MB'
  if (bytes >= 1 << 10) return (bytes / (1 << 10)).toFixed(1) + ' KB'
  return bytes + ' B'
}
const diskTotal = computed(() => data.value?.disk.totalSize ?? 0)
const diskMax = computed(() => Math.max(...(data.value?.disk.byDirectory.map(d => d.size) ?? [1])))

/* ─── 快捷入口 ─── */
const quickActions = [
  { label: '收藏夹', to: '/?module=bookmarks', icon: Bookmark },
  { label: '便签', to: '/?module=notes', icon: StickyNote },
  { label: '云盘', to: '/?module=files', icon: Cloud },
  { label: '密码库', to: '/?module=vault', icon: KeyRound },
  { label: '回收站', to: '/recycle', icon: Trash2 },
  { label: '音乐', to: '/music', icon: LayoutDashboard },
]

/* ─── 首页自定义布局 ─── */
import { useUserPreferences } from '../composables/useUserPreferences'
import { useDraggableSort } from '../../modules/bookmark/composables/useDraggableSort'

const prefs = useUserPreferences()

type DashSection = 'greet' | 'stat' | 'trend' | 'disk' | 'vault' | 'todo' | 'recent' | 'quick'

const DASH_SECTIONS: Array<{ key: DashSection; label: string; icon: any }> = [
  { key: 'greet', label: '问候与日期', icon: Sparkles },
  { key: 'stat', label: '统计卡片', icon: LayoutDashboard },
  { key: 'trend', label: '数据趋势', icon: TrendingUp },
  { key: 'disk', label: '云盘占用', icon: HardDrive },
  { key: 'vault', label: '密码库健康', icon: ShieldCheck },
  { key: 'todo', label: '今日待办', icon: ListTodo },
  { key: 'recent', label: '最近动态', icon: Bookmark },
  { key: 'quick', label: '快捷入口', icon: ArrowRight },
]

const DEFAULT_DASH_ORDER: DashSection[] = DASH_SECTIONS.map(s => s.key)

const dashOrder = ref<DashSection[]>(prefs.get<DashSection[]>('dash.order', DEFAULT_DASH_ORDER))
const dashHidden = ref<DashSection[]>(prefs.get<DashSection[]>('dash.hidden', []))

const orderOf = (key: DashSection) => {
  const idx = dashOrder.value.indexOf(key)
  return idx < 0 ? DEFAULT_DASH_ORDER.indexOf(key) : idx
}
const visible = (key: DashSection) => !dashHidden.value.includes(key)

/** 布局编辑器 */
const showLayout = ref(false)
const layoutListRef = ref<HTMLElement | null>(null)
const { init: initLayoutSort } = useDraggableSort(layoutListRef, (ordered) => {
  dashOrder.value = ordered as DashSection[]
  prefs.set('dash.order', dashOrder.value)
})

watch(showLayout, (v) => {
  if (v) {
    // 等待 DOM 渲染后再初始化拖拽
    setTimeout(() => initLayoutSort(), 50)
  }
})

const toggleSection = (key: DashSection) => {
  const set = new Set(dashHidden.value)
  if (set.has(key)) set.delete(key)
  else set.add(key)
  dashHidden.value = [...set]
  prefs.set('dash.hidden', dashHidden.value)
}

const resetLayout = () => {
  dashOrder.value = [...DEFAULT_DASH_ORDER]
  dashHidden.value = []
  prefs.set('dash.order', dashOrder.value)
  prefs.set('dash.hidden', dashHidden.value)
}

/* ─── 问候 ─── */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 5) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const dateText = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1}月${d.getDate()}日 星期${WEEKDAYS[d.getDay()]}`
})
const QUOTES = [
  '日拱一卒，功不唐捐。',
  '种一棵树最好的时间是十年前，其次是现在。',
  '少即是多，慢即是快。',
  '把今天过好，就是对未来最好的准备。',
  '志之所趋，无远弗届。',
  '不积跬步，无以至千里。',
]
const quoteText = computed(() => QUOTES[new Date().getDate() % QUOTES.length])

/* 今日待办跳转 */
const goTodos = () => router.push('/todos')
</script>

<template>
  <div class="dash">
    <!-- 概览操作区：低频操作收进“更多”，避免和 JPageHeader 重复 -->
    <div class="dash-toolbar">
      <NDropdown :options="moreOptions" placement="bottom-end" trigger="click" @select="handleMoreSelect">
        <NButton size="tiny" quaternary class="dash-more">
          <template #icon><NIcon :component="MoreHorizontal" size="14" /></template>
          更多
        </NButton>
      </NDropdown>
      <NButton size="tiny" quaternary class="dash-layout" @click="showLayout = true">
        <template #icon><NIcon :component="Settings2" size="13" /></template>
        布局
      </NButton>
      <NButton size="tiny" quaternary class="dash-refresh" :loading="loading" @click="fetchSummary; fetchTrend()">
        <template #icon><NIcon :component="RefreshCw" size="13" /></template>
        刷新
      </NButton>
    </div>

    <ExportModal v-model:show="showExport" />
    <ImportModal v-model:show="showImport" @imported="fetchSummary" />
    <FullBackupModal v-model:show="showBackup" @imported="fetchSummary" />
    <DedupModal v-model:show="showDedup" @changed="fetchSummary" />

    <div class="dash-spin">
      <JSkeletonGrid v-if="loading" />

      <div v-else-if="error && !data" class="dash-error">
        <JErrorState
          message="概览加载失败"
          hint="请检查网络后重试"
          @retry="fetchSummary(); fetchTrend()"
        />
      </div>

      <template v-else-if="data">
        <!-- 问候与日期 -->
        <div v-if="visible('greet')" class="dash-section" :style="{ order: orderOf('greet') }">
          <div class="greet-card glass-card--modal">
            <div class="greet-main">
              <span class="greet-emoji">{{ greeting === '夜深了' ? '🌙' : '👋' }}</span>
              <div class="greet-text">
                <div class="greet-title">{{ greeting }}，欢迎回来</div>
                <div class="greet-date">{{ dateText }}</div>
              </div>
            </div>
            <div class="greet-quote">「{{ quoteText }}」</div>
          </div>
        </div>

        <!-- 统计卡片 -->
        <div v-if="visible('stat')" class="dash-section" :style="{ order: orderOf('stat') }">
          <div class="dash-group-title">数据总览</div>
          <div class="stat-grid">
            <JStatCard
              v-for="c in statCards" :key="c.key"
              :label="c.label" :value="c.value" :icon="c.icon" :warn="c.warn"
              @click="router.push(c.to)"
            />
          </div>
        </div>

        <!-- 今日待办 -->
        <div v-if="visible('todo')" class="dash-section" :style="{ order: orderOf('todo') }">
          <div class="dash-group-title">状态与效率</div>
          <div class="panel todo-panel" @click="goTodos">
            <div class="panel-title">
              <NIcon :component="ListTodo" size="15" class="panel-title-icon" /> 今日待办
              <span class="panel-sub" v-if="data.todos">进行中 {{ data.todos.active }} · 今日到期 {{ data.todos.dueToday }} · 已逾期 {{ data.todos.overdue }}</span>
            </div>
            <div class="todo-summary">
              <div class="todo-summary-item" :class="{ 'todo-summary-warn': data.todos.dueToday > 0 }">
                <b>{{ data.todos.dueToday }}</b><span>今日到期</span>
              </div>
              <div class="todo-summary-item" :class="{ 'todo-summary-danger': data.todos.overdue > 0 }">
                <b>{{ data.todos.overdue }}</b><span>已逾期</span>
              </div>
              <div class="todo-summary-item">
                <b>{{ data.todos.active }}</b><span>进行中</span>
              </div>
              <div class="todo-summary-go">
                去处理 <NIcon :component="ArrowRight" size="13" />
              </div>
            </div>
          </div>
        </div>

        <!-- 数据趋势 -->
        <div v-if="visible('trend')" class="dash-section" :style="{ order: orderOf('trend') }">
          <div class="panel trend-panel">
          <div class="panel-title">
            <NIcon :component="TrendingUp" size="15" class="panel-title-icon" /> 数据趋势 · 近 6 个月
            <span class="trend-legend">
              <span v-for="s in TREND_SERIES" :key="s.key" class="trend-legend-item">
                <i class="trend-dot" :style="{ background: s.color }" />
                {{ s.label }} <b>{{ trendTotal[s.key] }}</b>
              </span>
            </span>
          </div>
          <div v-if="!trendData.length" class="panel-empty">暂无数据</div>
          <div v-else class="trend-chart">
            <!-- 网格线（5 档） -->
            <div class="trend-grid-lines">
              <div v-for="i in 5" :key="i" class="trend-grid-line" :style="{ bottom: `${((i - 1) / 4) * 100}%` }" />
            </div>
            <!-- 柱组 -->
            <div class="trend-bars">
              <div v-for="p in trendData" :key="p.month" class="trend-group">
                <div class="trend-group-bars">
                  <div
                    v-for="s in TREND_SERIES" :key="s.key"
                    class="trend-bar"
                    :style="{ height: trendBarHeight(p[s.key] ?? 0), background: s.color }"
                    :title="`${p.month} ${s.label}：${p[s.key] ?? 0}`"
                  />
                </div>
                <span class="trend-month">{{ monthLabel(p.month) }}</span>
              </div>
            </div>
            <!-- Y 轴刻度 -->
            <div class="trend-y-axis">
              <span v-for="i in 5" :key="i" class="trend-y-label" :style="{ bottom: `${((i - 1) / 4) * 100}%` }">
                {{ Math.round((trendMax * (i - 1)) / 4) }}
              </span>
            </div>
          </div>
        </div>
        </div>

        <!-- 磁盘占用 + 密码库健康 -->
        <div v-if="visible('disk') || visible('vault')" class="dash-section" :style="{ order: orderOf('disk') }">
          <div class="mid-grid">
            <div v-if="visible('disk')" class="panel">
            <div class="panel-title">
              <NIcon :component="HardDrive" size="15" class="panel-title-icon" /> 云盘占用
              <span class="panel-sub">{{ data.disk.fileCount }} 个文件 · {{ fmtSize(diskTotal) }}</span>
            </div>
            <div v-if="!data.disk.byDirectory.length" class="panel-empty">云盘还没有文件</div>
            <div v-else class="disk-list">
              <div v-for="d in data.disk.byDirectory" :key="d.directoryId" class="disk-row">
                <div class="disk-info">
                  <span class="disk-name">{{ d.name }}</span>
                  <span class="disk-meta">{{ d.count }} 个 · {{ fmtSize(d.size) }}</span>
                </div>
                <div class="disk-bar-wrap">
                  <div class="disk-bar" :style="{ width: `${Math.max(2, (d.size / diskMax) * 100)}%` }" />
                </div>
              </div>
            </div>
          </div>

          <div v-if="visible('vault')" class="panel">
            <div class="panel-title">
              <NIcon :component="ShieldCheck" size="15" class="panel-title-icon" /> 密码库健康
            </div>
            <div class="vault-health">
              <div class="health-row">
                <span class="health-label">条目数</span>
                <span class="health-value">{{ data.vault.entries }}</span>
              </div>
              <div class="health-row">
                <span class="health-label">重复密码</span>
                <span class="health-value" :class="{ 'health-bad': data.vault.duplicateCount > 0 }">
                  {{ data.vault.duplicateCount }}
                </span>
              </div>
              <div v-if="data.vault.duplicateCount > 0" class="health-warn">
                <NIcon :component="AlertTriangle" size="13" /> 检测到重复密码，建议前往密码库体检
              </div>
              <div v-else class="health-ok">状态良好，未发现重复密码</div>
            </div>
          </div>
        </div>
        </div>

        <!-- 最近动态 -->
        <div v-if="visible('recent')" class="dash-section" :style="{ order: orderOf('recent') }">
          <div class="dash-group-title">最近与快捷</div>
          <div class="recent-grid">
            <div class="panel">
              <div class="panel-title"><NIcon :component="Bookmark" size="15" class="panel-title-icon" /> 最近收藏</div>
              <div v-if="!data.recent.bookmarks.length" class="panel-empty">暂无收藏</div>
              <div v-else class="recent-list">
                <div v-for="b in data.recent.bookmarks" :key="b.id" class="recent-item">
                  <span class="recent-title">{{ b.title || b.url }}</span>
                  <span class="recent-time">{{ formatRelativeTime(b.createTime) }}</span>
                </div>
              </div>
            </div>
            <div class="panel">
              <div class="panel-title"><NIcon :component="StickyNote" size="15" class="panel-title-icon" /> 最近便签</div>
              <div v-if="!data.recent.notes.length" class="panel-empty">暂无便签</div>
              <div v-else class="recent-list">
                <div v-for="n in data.recent.notes" :key="n.id" class="recent-item">
                  <span class="recent-title">{{ n.title || '无标题' }}</span>
                  <span class="recent-time">{{ formatRelativeTime(n.createTime) }}</span>
                </div>
              </div>
            </div>
            <div class="panel">
              <div class="panel-title"><NIcon :component="Cloud" size="15" class="panel-title-icon" /> 最近文件</div>
              <div v-if="!data.recent.files.length" class="panel-empty">暂无文件</div>
              <div v-else class="recent-list">
                <div v-for="f in data.recent.files" :key="f.id" class="recent-item">
                  <span class="recent-title">{{ f.originalName }}</span>
                  <span class="recent-time">{{ formatRelativeTime(f.createTime) }}</span>
                </div>
              </div>
            </div>
            <div class="panel">
              <div class="panel-title">
                <NIcon :component="BookOpen" size="15" class="panel-title-icon" /> 继续阅读
                <span v-if="data.readLater.count" class="panel-sub">{{ data.readLater.count }} 篇待读</span>
              </div>
              <div v-if="!data.readLater.list.length" class="panel-empty">没有稍后读的内容</div>
              <div v-else class="recent-list">
                <div
                  v-for="r in data.readLater.list" :key="r.id"
                  class="recent-item recent-link"
                  @click="goReadLater(r)"
                >
                  <span class="recent-title">{{ r.title || r.url }}</span>
                  <span class="recent-meta">
                    <span v-if="r.progress > 0" class="rl-pct">{{ r.progress }}%</span>
                    <span v-else class="rl-new">未开始</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div v-if="visible('quick')" class="dash-section" :style="{ order: orderOf('quick') }">
          <div class="quick-bar">
            <NButton
              v-for="q in quickActions" :key="q.label"
              size="small" class="quick-btn jnclub-bouncy" @click="router.push(q.to)"
            >
              <template #icon><NIcon :component="q.icon" size="15" /></template>
              {{ q.label }}
            </NButton>
            <NButton size="small" class="quick-btn jnclub-bouncy" @click="showShareManager = true">
              <template #icon><NIcon :component="Share2" size="15" /></template>
              我的分享
            </NButton>
          </div>
        </div>
      </template>
    </div>

    <!-- 我的分享管理 -->
    <ShareManagerDrawer v-model:show="showShareManager" />

    <!-- 继续阅读（阅读模式，跟踪进度） -->
    <ReadingModal v-model:show="readingShow" :url="readingUrl" :bookmark-id="readingId" />

    <!-- 布局编辑器 -->
    <NDrawer v-model:show="showLayout" placement="right" :width="320">
      <div class="layout-editor">
        <div class="layout-title">首页布局</div>
        <p class="layout-hint">拖拽排序，开关控制显示/隐藏，偏好会自动保存到云端。</p>
        <div ref="layoutListRef" class="layout-list">
          <div v-for="s in DASH_SECTIONS" :key="s.key" :data-id="s.key" class="layout-item">
            <NIcon :component="GripVertical" size="15" class="layout-grip" />
            <NIcon :component="s.icon" size="15" class="layout-item-icon" />
            <span class="layout-item-label">{{ s.label }}</span>
            <NSwitch :value="!dashHidden.includes(s.key)" size="small" @update:value="() => toggleSection(s.key)" />
          </div>
        </div>
        <div class="layout-foot">
          <NButton size="small" quaternary @click="resetLayout">恢复默认</NButton>
          <NButton size="small" type="primary" secondary @click="showLayout = false">完成</NButton>
        </div>
      </div>
    </NDrawer>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.dash-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.dash-more,
.dash-layout,
.dash-refresh {
  border-radius: var(--radius-pill);
}
.dash-spin { min-height: 240px; }
.dash-error { padding-top: 60px; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.dash-retry { border-radius: var(--radius-pill); }

/* 分组标题：轻量 overline，强化信息层级 */
.dash-group-title {
  font-size: var(--fs-sm);
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--text-3);
  text-transform: uppercase;
  margin-bottom: 12px;
}

/* 统计卡片：宽屏 6 列一排，常规 3 列，移动端 2 列，极窄 1 列 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 16px;
  row-gap: 16px;
}
@media (min-width: 1400px) {
  .stat-grid { grid-template-columns: repeat(6, minmax(0, 1fr)); }
}
@media (max-width: 699px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 419px) {
  .stat-grid { grid-template-columns: 1fr; }
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 112px;
  padding: 24px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1), var(--glass-shadow);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
  text-align: left;
}
.stat-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-2), var(--glass-shadow);
  transform: translateY(-2px);
}
.stat-card.stat-warn { border-color: color-mix(in srgb, var(--danger) 45%, transparent); }
.stat-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.stat-icon {
  width: 44px; height: 44px; border-radius: 14px;
  flex-shrink: 0;
  background: var(--brand-soft); color: var(--brand);
  display: flex; align-items: center; justify-content: center;
}
.stat-warn .stat-icon { background: var(--danger-soft); color: var(--danger-text); }
.stat-value {
  font-size: 32px; font-weight: 800; color: var(--text-1); line-height: 1.1;
  white-space: nowrap;
}
.stat-label {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--fs-md); color: var(--glass-text-secondary);
}
.stat-warn-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--danger); animation: dash-blink 1.2s infinite;
}
@keyframes dash-blink {
  50% { opacity: 0.3; }
}
@media (max-width: 699px) {
  /* 移动端窄卡：改回纵向排布，避免横向挤压 */
  .stat-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    min-height: 124px;
  }
  .stat-icon { width: 36px; height: 36px; border-radius: 10px; }
  .stat-value { font-size: 26px; }
}

/* 中排双栏 */
.mid-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 20px;
  align-items: stretch;
}
.mid-grid .panel {
  height: 100%;
}
@media (max-width: 900px) {
  .mid-grid { grid-template-columns: 1fr; }
}
.panel {
  padding: 20px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1), var(--glass-shadow);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}
.panel-title-icon { color: var(--brand); }
.panel-sub {
  margin-left: auto;
  font-size: var(--fs-sm);
  font-weight: 400;
  color: var(--text-3);
}
.panel-empty {
  padding: 20px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
}

/* 磁盘条 */
.disk-list { display: flex; flex-direction: column; gap: 12px; }
.disk-row { display: flex; flex-direction: column; gap: 6px; }
.disk-info {
  display: flex; align-items: center; justify-content: space-between;
  font-size: var(--fs-sm);
}
.disk-name {
  color: var(--text-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.disk-meta { color: var(--text-3); flex-shrink: 0; margin-left: 8px; }
.disk-bar-wrap {
  height: 6px; border-radius: 3px;
  background: var(--glass-chip-bg);
  overflow: hidden;
}
.disk-bar {
  height: 100%; border-radius: 3px;
  background: linear-gradient(90deg, var(--brand), var(--brand-suppl, var(--brand)));
  transition: width .4s var(--ease);
}

/* 密码库健康 */
.vault-health { display: flex; flex-direction: column; gap: 12px; }
.health-row {
  display: flex; align-items: center; justify-content: space-between;
  font-size: var(--fs-sm);
}
.health-label { color: var(--text-2); }
.health-value { font-weight: 600; color: var(--text-1); }
.health-value.health-bad { color: var(--danger-text); }
.health-warn {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--fs-sm); color: var(--danger-text);
  background: var(--danger-soft);
  padding: 8px 10px; border-radius: var(--radius-sm);
}
.health-ok { font-size: var(--fs-sm); color: var(--success); }

/* 最近动态：桌面 4 列一排，平板 2 列，手机 1 列 */
.recent-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 20px;
  grid-auto-rows: 1fr;
}
.recent-grid .panel {
  min-height: 0;
  height: 100%;
}
@media (max-width: 1199px) {
  .recent-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 699px) {
  .recent-grid { grid-template-columns: 1fr; }
}
.recent-list { display: flex; flex-direction: column; gap: 8px; }
.recent-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  transition: background var(--dur) var(--ease);
}
.recent-item:hover { background: var(--glass-chip-bg); }
.recent-link { cursor: pointer; }
.recent-title {
  font-size: var(--fs-sm); color: var(--text-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.recent-time { font-size: var(--fs-xs); color: var(--text-3); flex-shrink: 0; }
.recent-meta { display: flex; align-items: center; flex-shrink: 0; }
.rl-pct {
  font-size: var(--fs-xs); color: var(--brand); font-weight: 600;
  background: var(--brand-soft); border-radius: var(--radius-pill); padding: 1px 8px;
}
.rl-new { font-size: var(--fs-xs); color: var(--text-3); }

/* 快捷入口 */
.quick-bar {
  display: flex; align-items: center; flex-wrap: wrap; gap: 8px;
  padding: 12px 14px;
  background: var(--glass-bg-trans);
  border: 1px dashed var(--glass-border);
  border-radius: var(--radius-md);
}
.quick-btn { border-radius: var(--radius-pill); }

/* 数据趋势 */
.trend-panel { min-height: 200px; }
.trend-legend {
  margin-left: auto;
  display: flex; align-items: center; flex-wrap: wrap; gap: 12px;
  font-size: var(--fs-xs);
  color: var(--text-3);
  font-weight: 400;
}
.trend-legend-item {
  display: inline-flex; align-items: center; gap: 4px;
  white-space: nowrap;
}
.trend-legend-item b { color: var(--text-2); font-weight: 600; }
.trend-dot {
  width: 8px; height: 8px; border-radius: 50%;
  display: inline-block;
}
.trend-chart {
  position: relative;
  height: 180px;
  padding-left: 34px;
}
.trend-grid-lines {
  position: absolute;
  inset: 0 0 0 34px;
  pointer-events: none;
}
.trend-grid-line {
  position: absolute;
  left: 0; right: 0;
  border-top: 1px dashed var(--glass-border);
}
.trend-bars {
  position: absolute;
  inset: 0 0 0 34px;
  display: flex;
  align-items: stretch;
  gap: 12px;
}
.trend-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  min-width: 0;
}
.trend-group-bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
}
.trend-bar {
  width: 9px;
  max-width: 14px;
  border-radius: 3px 3px 1px 1px;
  min-height: 2px;
  transition: height 400ms var(--ease-bouncy);
  opacity: 0.92;
}
.trend-bar:hover { opacity: 1; }
.trend-month {
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--text-3);
  white-space: nowrap;
}
.trend-y-axis {
  position: absolute;
  inset: 0 auto 0 0;
  width: 26px;
  pointer-events: none;
}
.trend-y-label {
  position: absolute;
  right: 0;
  transform: translateY(50%);
  font-size: var(--fs-xs);
  color: var(--text-3);
}
@media (max-width: 699px) {
  .trend-bars { gap: 6px; }
  .trend-bar { width: 7px; }
  .dash-toolbar { gap: 4px; }
  .panel-title {
    flex-wrap: wrap;
    row-gap: 4px;
  }
  .panel-sub,
  .trend-legend {
    margin-left: 0;
    width: 100%;
  }
}

/* 问候卡片 */
.greet-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border-radius: var(--radius-md);
  background: linear-gradient(120deg, var(--brand-soft), transparent 60%), var(--glass-bg-trans);
  border: 1px solid var(--glass-chip-border);
  box-shadow: var(--shadow-1), var(--glass-shadow);
  flex-wrap: wrap;
}
.greet-main {
  display: flex;
  align-items: center;
  gap: 14px;
}
.greet-emoji { font-size: 30px; }
.greet-text { display: flex; flex-direction: column; gap: 2px; }
.greet-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}
.greet-date { font-size: var(--fs-sm); color: var(--glass-text-secondary); }
.greet-quote {
  font-size: var(--fs-sm);
  color: var(--glass-text-secondary);
  font-style: italic;
  text-align: right;
}

/* 今日待办面板 */
.todo-panel { cursor: pointer; transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease); }
.todo-panel:hover { border-color: var(--brand); box-shadow: var(--shadow-2), var(--glass-shadow); }
.todo-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}
.todo-summary-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.todo-summary-item b {
  font-size: 24px;
  font-weight: 800;
  color: var(--text-1);
}
.todo-summary-item.todo-summary-warn b { color: var(--warning-text); }
.todo-summary-item.todo-summary-danger b { color: var(--danger); }
.todo-summary-go {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
  color: var(--brand);
  font-weight: 600;
}

/* 布局编辑器 */
.layout-editor { display: flex; flex-direction: column; gap: 14px; padding: 16px; }
.layout-title { font-size: 16px; font-weight: 700; color: var(--text-1); }
.layout-hint { font-size: var(--fs-sm); color: var(--text-3); line-height: 1.6; }
.layout-list { display: flex; flex-direction: column; gap: 6px; }
.layout-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-sm);
  cursor: grab;
}
.layout-item:active { cursor: grabbing; }
.layout-grip { color: var(--text-3); }
.layout-item-icon { color: var(--brand); }
.layout-item-label { flex: 1; font-size: var(--fs-md); color: var(--text-1); }
.layout-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
}
</style>
