<script setup lang="ts">
/**
 * DedupModal.vue — 重复数据检测
 * 两个标签页：收藏（按规范化 URL）/ 云盘（按内容 MD5）。
 * 每个重复组可「保留第一条，删除其余」或逐条勾选删除。
 */
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NSpin, NCheckbox, useMessage, NTag, NPopconfirm } from 'naive-ui'
import { ScanSearch, Bookmark, Cloud, Trash2 } from 'lucide-vue-next'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import JErrorState from '../../../shared/components/ui/JErrorState.vue'
import axios from 'axios'

const props = defineProps<{ show: boolean; initialTab?: 'bookmarks' | 'files' }>()
const emit = defineEmits<{ 'update:show': [v: boolean]; 'changed': [] }>()
const message = useMessage()

type TabKey = 'bookmarks' | 'files'
const tab = ref<TabKey>(props.initialTab || 'bookmarks')

interface DupItem { id: number; title?: string; originalName?: string; url?: string; icon?: string; size?: number; directoryId: number }
interface DupGroup { url?: string; hash?: string; normalized?: string; count: number; totalSize?: number; items: DupItem[] }

const bookmarks = ref<DupGroup[]>([])
const files = ref<DupGroup[]>([])
const loading = ref(false)
const loadError = ref(false)
const busy = ref(false)

const groups = () => (tab.value === 'bookmarks' ? bookmarks.value : files.value)
const selectedIds = ref<Set<number>>(new Set())

watch(() => props.show, (v) => {
  if (v) {
    if (props.initialTab) tab.value = props.initialTab
    selectedIds.value = new Set()
    run()
  }
})

const run = async () => {
  loading.value = true
  loadError.value = false
  try {
    if (tab.value === 'bookmarks') {
      const res = await axios.post('/api/bookmarks/dedup')
      bookmarks.value = res.data?.data || []
    } else {
      const res = await axios.post('/api/files/dedup')
      files.value = res.data?.data || []
    }
  } catch (e: any) {
    loadError.value = true
    message.error(e.response?.data?.message || e.message || '检测失败')
  } finally {
    loading.value = false
  }
}

const switchTab = (t: TabKey) => {
  if (tab.value === t) return
  tab.value = t
  selectedIds.value = new Set()
  run()
}

const fmtSize = (b?: number) => {
  if (!b) return '0 B'
  if (b >= 1 << 30) return (b / (1 << 30)).toFixed(2) + ' GB'
  if (b >= 1 << 20) return (b / (1 << 20)).toFixed(1) + ' MB'
  if (b >= 1 << 10) return (b / (1 << 10)).toFixed(1) + ' KB'
  return b + ' B'
}

const itemLabel = (i: DupItem) =>
  tab.value === 'bookmarks' ? i.title || i.url : i.originalName

const itemSub = (i: DupItem) =>
  tab.value === 'bookmarks' ? (i.url || '') : `${fmtSize(i.size)} · ${i.directoryId ? `目录 ${i.directoryId}` : ''}`

/** 组内除第一条外的 id */
const redundantIds = (g: DupGroup) => g.items.slice(1).map(i => i.id)

/** 保留第一条，删除其余 */
const mergeGroup = async (g: DupGroup) => {
  const ids = redundantIds(g)
  if (!ids.length) return
  busy.value = true
  try {
    await axios.delete(tab.value === 'bookmarks' ? '/api/bookmarks/batch' : '/api/files/batch', { data: { ids } })
    message.success(`已合并 1 组，清理 ${ids.length} 条重复`)
    emit('changed')
    await run()
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '操作失败')
  } finally {
    busy.value = false
  }
}

/** 删除勾选条目 */
const deleteSelected = async () => {
  const ids = [...selectedIds.value]
  if (!ids.length) { message.warning('请先勾选要删除的条目'); return }
  busy.value = true
  try {
    await axios.delete(tab.value === 'bookmarks' ? '/api/bookmarks/batch' : '/api/files/batch', { data: { ids } })
    message.success(`已删除 ${ids.length} 条`)
    selectedIds.value = new Set()
    emit('changed')
    await run()
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '操作失败')
  } finally {
    busy.value = false
  }
}

const toggle = (id: number) => {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    :style="{ width: 'min(680px, 94vw)' }"
    class="dedup-modal"
  >
    <div class="dedup-shell">
      <div class="dedup-head">
        <div class="dedup-title">
          <NIcon :component="ScanSearch" size="17" class="dedup-title-icon" />
          重复数据检测
        </div>
        <div class="dedup-tabs">
          <button
            :class="['dedup-tab', 'jnclub-bouncy', { active: tab === 'bookmarks' }]"
            @click="switchTab('bookmarks')"
          >
            <NIcon :component="Bookmark" size="14" /> 收藏
          </button>
          <button
            :class="['dedup-tab', 'jnclub-bouncy', { active: tab === 'files' }]"
            @click="switchTab('files')"
          >
            <NIcon :component="Cloud" size="14" /> 云盘
          </button>
        </div>
      </div>

      <div class="dedup-body">
        <NSpin :show="loading">
          <JErrorState
            v-if="loadError && !loading"
            message="检测失败"
            hint="请检查网络后重试"
            class="dedup-empty"
            @retry="run"
          />
          <div v-else-if="!groups().length && !loading" class="dedup-empty">
            <JEmptyState
              message="没有发现重复数据"
              hint="很干净 👌"
              :show-cta="false"
            />
          </div>
          <template v-else>
            <!-- 批量操作条 -->
            <div v-if="selectedIds.size" class="dedup-bulk">
              <span class="dedup-bulk-count">已选 {{ selectedIds.size }} 条</span>
              <NPopconfirm @positive-click="deleteSelected">
                <template #trigger>
                  <NButton size="tiny" type="error" secondary :loading="busy">
                    <template #icon><NIcon :component="Trash2" size="13" /></template>
                    删除选中
                  </NButton>
                </template>
                确定删除选中的 {{ selectedIds.size }} 条？将移入回收站。
              </NPopconfirm>
            </div>

            <div class="dedup-groups">
              <div v-for="(g, gi) in groups()" :key="gi" class="dedup-group">
                <div class="dedup-group-head">
                  <div class="dedup-group-title">
                    <NIcon :component="tab === 'bookmarks' ? Bookmark : Cloud" size="13" />
                    <span class="dedup-group-url">
                      {{ tab === 'bookmarks' ? (g.url || g.normalized) : `MD5 ${(g.hash || '').slice(0, 12)}…` }}
                    </span>
                    <NTag size="tiny" round :bordered="false" class="dedup-count-tag">{{ g.count }} 份</NTag>
                  </div>
                  <NPopconfirm @positive-click="mergeGroup(g)">
                    <template #trigger>
                      <NButton size="tiny" type="primary" secondary :loading="busy" :disabled="g.items.length < 2">
                        保留第一条，删除其余
                      </NButton>
                    </template>
                    将删除该组除第一条外的 {{ g.items.length - 1 }} 条（移入回收站），确定？
                  </NPopconfirm>
                </div>
                <div class="dedup-items">
                  <div
                    v-for="(it, ii) in g.items" :key="it.id"
                    :class="['dedup-item', { 'dedup-keep': tab === 'bookmarks' ? ii === 0 : false }]"
                  >
                    <NCheckbox :checked="selectedIds.has(it.id)" @update:checked="toggle(it.id)" size="small" />
                    <img v-if="tab === 'bookmarks' && it.icon" :src="it.icon" class="dedup-favicon" alt="" loading="lazy" decoding="async" @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')" />
                    <div class="dedup-item-main">
                      <span class="dedup-item-title">{{ itemLabel(it) || '(无标题)' }}</span>
                      <span class="dedup-item-sub">{{ itemSub(it) }}</span>
                    </div>
                    <NTag v-if="tab === 'bookmarks' && ii === 0" size="tiny" round :bordered="false" type="success" class="dedup-keep-tag">保留</NTag>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </NSpin>
      </div>

      <div class="dedup-foot">
        <span class="dedup-hint">收藏按 URL 规范化查重；云盘按文件内容 MD5 查重（新上传自动计算，旧文件检测时懒计算）</span>
        <NButton size="small" type="primary" secondary :loading="loading" @click="run">
          <template #icon><NIcon :component="ScanSearch" size="14" /></template>
          重新检测
        </NButton>
        <NButton size="small" quaternary @click="emit('update:show', false)">关闭</NButton>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.dedup-shell {
  display: flex;
  flex-direction: column;
  max-height: 86vh;
  border-radius: var(--radius-md);
  background: var(--glass-bg-solid);
  overflow: hidden;
}
.dedup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.dedup-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.dedup-title-icon { color: var(--brand); }
.dedup-tabs { display: flex; gap: 6px; }
.dedup-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-chip-border);
  background: var(--glass-chip-bg);
  color: var(--glass-chip-text);
  font-size: var(--fs-sm);
  cursor: pointer;
}
.dedup-tab.active {
  background: var(--brand-soft);
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 600;
}
.dedup-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 18px;
}
.dedup-empty { padding: 50px 0; }
.dedup-bulk {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 10px;
  background: var(--brand-soft);
  border: 1px solid var(--brand);
  border-radius: var(--radius-sm);
}
.dedup-bulk-count { font-size: var(--fs-sm); color: var(--brand); font-weight: 600; flex: 1; }
.dedup-groups { display: flex; flex-direction: column; gap: 14px; }
.dedup-group {
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-md);
  background: var(--glass-bg-trans);
  overflow: hidden;
}
.dedup-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  background: var(--glass-chip-bg);
  border-bottom: 1px solid var(--glass-chip-border);
}
.dedup-group-title {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--text-2);
}
.dedup-group-url {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dedup-count-tag { flex-shrink: 0; }
.dedup-items { display: flex; flex-direction: column; }
.dedup-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--glass-border);
}
.dedup-item:last-child { border-bottom: none; }
.dedup-favicon { width: 16px; height: 16px; border-radius: 4px; flex-shrink: 0; }
.dedup-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.dedup-item-title {
  font-size: var(--fs-md);
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dedup-item-sub {
  font-size: var(--fs-xs);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dedup-keep-tag { flex-shrink: 0; }
.dedup-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.dedup-hint {
  flex: 1;
  font-size: var(--fs-xs);
  color: var(--text-3);
}
</style>
