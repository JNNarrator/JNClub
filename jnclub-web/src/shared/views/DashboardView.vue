<script setup lang="ts">
/**
 * DashboardView.vue — 概览数据看板
 * 统计卡片（收藏/便签/文件/密码/标签/回收站）+ 磁盘占用 + 密码库指纹健康 + 最近动态 + 快捷入口
 */
import { ref, computed, onMounted } from 'vue'
import { NIcon, NSpin, NEmpty, NButton } from 'naive-ui'
import {
  Bookmark, StickyNote, Cloud, KeyRound, Tag, Trash2, HardDrive,
  ShieldCheck, AlertTriangle, ArrowRight, LayoutDashboard, RefreshCw, Download,
} from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { formatRelativeTime } from '../../modules/bookmark/composables/formatDate'
import ExportModal from '../../modules/bookmark/components/ExportModal.vue'

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
}

const router = useRouter()
const loading = ref(true)
const error = ref(false)
const data = ref<StatsSummary | null>(null)
const showExport = ref(false)

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

onMounted(fetchSummary)

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
</script>

<template>
  <div class="dash">
    <div class="dash-head">
      <div class="dash-title">
        <NIcon :component="LayoutDashboard" size="18" class="dash-title-icon" />
        <span>数据概览</span>
      </div>
      <div class="dash-actions">
        <NButton size="tiny" quaternary class="dash-export" @click="showExport = true">
          <template #icon><NIcon :component="Download" size="13" /></template>
          数据导出
        </NButton>
        <NButton size="tiny" quaternary class="dash-refresh" :loading="loading" @click="fetchSummary">
          <template #icon><NIcon :component="RefreshCw" size="13" /></template>
          刷新
        </NButton>
      </div>
    </div>

    <ExportModal v-model:show="showExport" />

    <NSpin :show="loading" class="dash-spin">
      <div v-if="error && !data" class="dash-error">
        <NEmpty description="加载失败，请刷新重试" class="dash-empty" />
      </div>

      <template v-else-if="data">
        <!-- 统计卡片 -->
        <div class="stat-grid">
          <button
            v-for="c in statCards" :key="c.key"
            type="button" class="stat-card jnclub-bouncy" :class="{ 'stat-warn': c.warn }"
            @click="router.push(c.to)"
          >
            <div class="stat-icon"><NIcon :component="c.icon" size="20" /></div>
            <div class="stat-text">
              <div class="stat-value">{{ c.value }}</div>
              <div class="stat-label">{{ c.label }}<span v-if="c.warn" class="stat-warn-dot" /></div>
            </div>
          </button>
        </div>

        <!-- 磁盘占用 + 密码库健康 -->
        <div class="mid-grid">
          <div class="panel">
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

          <div class="panel">
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

        <!-- 最近动态 -->
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
        </div>

        <!-- 快捷入口 -->
        <div class="quick-bar">
          <NButton
            v-for="q in quickActions" :key="q.label"
            size="small" class="quick-btn jnclub-bouncy" @click="router.push(q.to)"
          >
            <template #icon><NIcon :component="q.icon" size="15" /></template>
            {{ q.label }}
          </NButton>
          <span class="quick-hint">快捷入口</span>
          <NIcon :component="ArrowRight" size="13" class="quick-arrow" />
        </div>
      </template>
    </NSpin>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 40px;
}
.dash-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dash-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}
.dash-title-icon { color: var(--brand); }
.dash-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dash-refresh { border-radius: var(--radius-pill); }
.dash-export { border-radius: var(--radius-pill); color: var(--brand); }
.dash-spin { min-height: 240px; }
.dash-error { padding-top: 60px; }

/* 统计卡片 */
/* 统计卡片：固定列数（3/2），任何宽度都不超过 3 列，避免卡片过窄拥挤；
   ≤699px 移动端降为 2 列 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 20px;
}
@media (max-width: 699px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
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
.stat-card.stat-warn { border-color: rgba(245, 72, 92, 0.45); }
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
.stat-warn .stat-icon { background: rgba(245, 72, 92, 0.16); color: #ff8a97; }
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
  grid-template-columns: 1fr 1fr;
  column-gap: 28px;
  row-gap: 32px;
}
@media (max-width: 900px) {
  .mid-grid { grid-template-columns: 1fr; }
}
.panel {
  padding: 24px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-1), var(--glass-shadow);
  display: flex;
  flex-direction: column;
  gap: 18px;
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
.health-value.health-bad { color: #ff8a97; }
.health-warn {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--fs-sm); color: #ff8a97;
  background: rgba(245, 72, 92, 0.1);
  padding: 8px 10px; border-radius: var(--radius-sm);
}
.health-ok { font-size: var(--fs-sm); color: var(--success); }

/* 最近动态 */
.recent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  column-gap: 24px;
  row-gap: 32px;
}
@media (max-width: 900px) {
  .recent-grid { grid-template-columns: 1fr; }
}
.recent-list { display: flex; flex-direction: column; gap: 8px; }
.recent-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  transition: background var(--dur) var(--ease);
}
.recent-item:hover { background: var(--glass-chip-bg); }
.recent-title {
  font-size: var(--fs-sm); color: var(--text-2);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.recent-time { font-size: var(--fs-xs); color: var(--text-3); flex-shrink: 0; }

/* 快捷入口 */
.quick-bar {
  display: flex; align-items: center; flex-wrap: wrap; gap: 10px;
  padding: 14px 18px;
  background: var(--glass-bg-trans);
  border: 1px dashed var(--glass-border);
  border-radius: var(--radius-md);
}
.quick-btn { border-radius: var(--radius-pill); }
.quick-hint {
  margin-left: auto;
  font-size: var(--fs-xs); color: var(--text-3);
}
.quick-arrow { color: var(--text-3); }
</style>
