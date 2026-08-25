// RecycleView.vue — 回收站内容区
<script setup lang="ts">
/**
 * RecycleView.vue — 回收站内容区（软删除条目查看/恢复/永久删除/清空）
 * 现由 RecycleLayout 套用主壳渲染：顶栏/导航/TabBar 与其他模块一致
 */
import { ref, computed, onMounted, watch } from 'vue'
import {
  NButton, NIcon, NTabs, NTabPane, NModal, NInputNumber,
  useMessage, useDialog,
} from 'naive-ui'
import { Trash2, RotateCcw, Eraser, Clock, Settings2, Zap } from 'lucide-vue-next'
import axios from 'axios'
import JSkeletonList from '../../../shared/components/ui/JSkeletonList.vue'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import { formatRelativeTime, formatDate } from '../composables/formatDate'

const props = defineProps<{ refresh?: number }>()

const message = useMessage()
const dialog = useDialog()

type RecycleType = 'bookmark' | 'note' | 'file' | 'vault'
const activeType = ref<RecycleType>('bookmark')
const items = ref<any[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const page = ref(1)
const PAGE_SIZE = 50
const totalItems = ref(0)
const hasMore = computed(() => items.value.length < totalItems.value)

const typeLabels: Record<RecycleType, string> = {
  bookmark: '收藏',
  note: '便签',
  file: '云盘文件',
  vault: '密码',
}

/* ─── 自动清理配置 ─── */
const keepDays = ref(30)
const showConfigModal = ref(false)
const newKeepDays = ref<number | null>(30)
const cleaning = ref(false)

const fetchConfig = async () => {
  try {
    const res = await axios.get('/api/recycle/config')
    if (res.data.code === 200) {
      keepDays.value = res.data.data?.keepDays ?? 30
      newKeepDays.value = keepDays.value
    }
  } catch { /* 后端未升级时用默认值 */ }
}

const saveConfig = async () => {
  if (!newKeepDays.value) return
  try {
    await axios.put('/api/recycle/config', { keepDays: newKeepDays.value })
    keepDays.value = newKeepDays.value
    showConfigModal.value = false
    message.success(`已更新：超过 ${keepDays.value} 天自动清理`)
  } catch (e: any) {
    message.error(e.response?.data?.message || '保存失败')
  }
}

const manualClean = () => {
  dialog.warning({
    title: '立即清理',
    content: `立即彻底删除超过 ${keepDays.value} 天的回收站条目？此操作不可恢复。`,
    positiveText: '清理',
    negativeText: '取消',
    onPositiveClick: async () => {
      cleaning.value = true
      try {
        const res = await axios.post('/api/recycle/clean')
        if (res.data.code === 200) {
          const c = res.data.data || {}
          const parts: string[] = []
          if (c.bookmark) parts.push(`收藏 ${c.bookmark}`)
          if (c.note) parts.push(`便签 ${c.note}`)
          if (c.file) parts.push(`文件 ${c.file}`)
          if (c.vault) parts.push(`密码 ${c.vault}`)
          message.success(parts.length ? `已清理：${parts.join('、')}` : '没有需要清理的条目')
          fetchItems()
        }
      } catch (e: any) {
        message.error(e.response?.data?.message || '清理失败')
      } finally { cleaning.value = false }
    },
  })
}

/** 条目到期自动删除时间 = 删除时间 + keepDays */
const expiresAt = (item: any): Date | null => {
  const t = item.createTime
  if (t == null) return null
  let ms: number | null = null
  if (Array.isArray(t)) {
    const [y, m = 1, d = 1, h = 0, min = 0, s = 0] = t as number[]
    ms = new Date(y, m - 1, d, h, min, s).getTime()
  } else if (typeof t === 'number') {
    ms = t
  } else if (typeof t === 'string') {
    const n = Number(t)
    ms = Number.isFinite(n) && n > 0 ? n : (isNaN(Date.parse(t)) ? null : Date.parse(t))
  }
  if (ms == null) return null
  return new Date(ms + keepDays.value * 86400000)
}

const isExpired = (item: any): boolean => {
  const d = expiresAt(item)
  return !!d && d.getTime() < Date.now()
}

const fetchItems = async () => {
  page.value = 1
  loading.value = true
  try {
    const res = await axios.get('/api/recycle', { params: { type: activeType.value, page: page.value, size: PAGE_SIZE } })
    if (res.data.code === 200) {
      const data = res.data.data
      if (data && Array.isArray(data.items)) {
        items.value = data.items || []
        totalItems.value = data.total || 0
      } else {
        // 兼容旧后端直接返回数组
        items.value = data || []
        totalItems.value = items.value.length
      }
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载失败')
  } finally { loading.value = false }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  const next = page.value + 1
  try {
    const res = await axios.get('/api/recycle', { params: { type: activeType.value, page: next, size: PAGE_SIZE } })
    if (res.data.code === 200) {
      const data = res.data.data
      if (data && Array.isArray(data.items)) {
        items.value = [...items.value, ...(data.items || [])]
        totalItems.value = data.total ?? totalItems.value
        page.value = data.page ?? next
      }
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载更多失败')
  } finally { loadingMore.value = false }
}

const onTabChange = () => fetchItems()

// 顶栏刷新触发
watch(() => props.refresh, () => { if (props.refresh) fetchItems() })

const itemTitle = (item: any) => {
  if (activeType.value === 'bookmark') return item.title || item.url
  if (activeType.value === 'note') return item.title || '无标题'
  if (activeType.value === 'vault') return item.name || '未命名'
  return item.originalName
}

const itemSub = (item: any) => {
  if (activeType.value === 'bookmark') return item.url
  if (activeType.value === 'note') return (item.content || '').replace(/[#*`\[\]()!>_~\-]/g, '').trim().slice(0, 60)
  if (activeType.value === 'vault') return item.username || '--'
  return `${item.size ? (item.size / 1024 / 1024).toFixed(1) + ' MB' : ''}`
}

const restore = (item: any) => {
  dialog.warning({
    title: '恢复',
    content: `确定恢复"${itemTitle(item)}"吗？`,
    positiveText: '恢复',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.post('/api/recycle/restore', { type: activeType.value, id: item.id })
        message.success('已恢复')
        fetchItems()
      } catch (e: any) {
        message.error(e.response?.data?.message || '恢复失败')
      }
    },
  })
}

const purge = (item: any) => {
  dialog.warning({
    title: '永久删除',
    content: `永久删除"${itemTitle(item)}"？此操作不可恢复。`,
    positiveText: '永久删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete(`/api/recycle/${activeType.value}/${item.id}`)
        message.success('已永久删除')
        fetchItems()
      } catch (e: any) {
        message.error(e.response?.data?.message || '删除失败')
      }
    },
  })
}

const clearAll = () => {
  dialog.warning({
    title: '清空回收站',
    content: `确定清空全部${typeLabels[activeType.value]}回收站吗？此操作不可恢复。`,
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await axios.delete('/api/recycle/clear', { params: { type: activeType.value } })
        message.success('已清空')
        fetchItems()
      } catch (e: any) {
        message.error(e.response?.data?.message || '清空失败')
      }
    },
  })
}

onMounted(() => { fetchItems(); fetchConfig() })
</script>

<template>
  <div>
    <!-- 自动清理信息条 -->
    <div class="recycle-config-bar">
      <span class="config-text">
        超过 <b class="config-days">{{ keepDays }}</b> 天的条目自动彻底删除（每日 03:40）
      </span>
      <div class="config-actions">
        <NButton size="tiny" quaternary class="config-btn" @click="showConfigModal = true">
          <template #icon><NIcon :component="Settings2" size="13" /></template>
          设置
        </NButton>
        <NButton size="tiny" class="config-btn clean-now" :loading="cleaning" @click="manualClean">
          <template #icon><NIcon :component="Zap" size="13" /></template>
          立即清理
        </NButton>
      </div>
    </div>

    <div class="recycle-toolbar">
      <NTabs v-model:value="activeType" type="line" class="recycle-tabs" @update:value="onTabChange">
        <NTabPane name="bookmark" tab="收藏" />
        <NTabPane name="note" tab="便签" />
        <NTabPane name="file" tab="云盘文件" />
        <NTabPane name="vault" tab="密码" />
      </NTabs>
      <NButton size="small" class="recycle-clear-btn" :disabled="!items.length" @click="clearAll">
        <template #icon><NIcon :component="Eraser" size="15" /></template>
        清空当前类型
      </NButton>
    </div>

    <div class="recycle-spin">
      <JSkeletonList v-if="loading" />
      <template v-else>
        <JEmptyState v-if="!items.length" message="回收站是空的" hint="删除的收藏、便签、文件或密码会出现在这里" />
        <div v-else class="recycle-list">
          <div v-for="item in items" :key="item.id" class="recycle-item">
            <div class="item-main">
              <div class="item-title">{{ itemTitle(item) }}</div>
              <div class="item-sub">
                <span v-if="itemSub(item)">{{ itemSub(item) }}</span>
                <span class="item-time">
                  <NIcon :component="Clock" size="12" />
                  {{ formatRelativeTime(item.createTime) }}
                </span>
                <span v-if="expiresAt(item)" class="item-expire" :class="{ expired: isExpired(item) }">
                  {{ isExpired(item) ? '已到期可清' : '保留至 ' + formatDate(String(expiresAt(item)!.getTime())) }}
                </span>
              </div>
            </div>
            <div class="item-actions">
              <NButton size="tiny" class="glass-primary-btn restore-btn" @click="restore(item)">
                <template #icon><NIcon :component="RotateCcw" size="13" /></template>
                恢复
              </NButton>
              <NButton size="tiny" class="recycle-danger-btn" @click="purge(item)">
                <template #icon><NIcon :component="Trash2" size="13" /></template>
                永久删除
              </NButton>
            </div>
          </div>
          <div v-if="hasMore" class="load-more-wrap">
            <NButton size="small" quaternary :loading="loadingMore" @click="loadMore">
              {{ loadingMore ? '加载中…' : `加载更多（${items.length}/${totalItems}）` }}
            </NButton>
          </div>
        </div>
      </template>
    </div>

    <!-- 自动清理设置弹窗 -->
    <NModal v-model:show="showConfigModal" preset="card" title="回收站自动清理设置" style="width: 380px" :bordered="false">
      <p class="config-tip">超过保留天数的回收站条目将被自动彻底删除（每日 03:40 执行，多实例互斥）。</p>
      <div class="config-form">
        <NInputNumber v-model:value="newKeepDays" :min="7" :max="180" class="config-input" />
        <span class="config-unit">天</span>
        <NButton type="primary" size="small" class="config-save" :disabled="!newKeepDays" @click="saveConfig">
          保存
        </NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
/* 自动清理信息条 */
.recycle-config-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  margin-bottom: 8px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
}
.config-text {
  font-size: var(--fs-sm);
  color: var(--glass-text-secondary);
}
.config-days { color: var(--brand); font-weight: 600; }
.config-actions { display: flex; gap: 6px; flex-shrink: 0; }
.config-btn {
  border-radius: var(--radius-pill) !important;
  height: 24px;
  font-size: var(--fs-sm);
}
.clean-now {
  background: var(--glass-bg-trans) !important;
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent) !important;
  color: var(--danger-text) !important;
}
.config-tip {
  font-size: var(--fs-sm);
  color: var(--glass-text-secondary);
  margin-bottom: 12px;
  line-height: 1.6;
}
.config-form {
  display: flex;
  align-items: center;
  gap: 8px;
}
.config-input { width: 120px; }
.config-unit { font-size: var(--fs-sm); color: var(--text-2); }
.config-save { border-radius: var(--radius-pill); }

.item-expire {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  font-size: var(--fs-sm);
  color: var(--glass-text-tertiary);
}
.item-expire.expired {
  color: var(--danger-text);
  font-weight: 500;
}

.recycle-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.recycle-tabs {
  flex: 1;
}
/* Tabs 玻璃化：选中粉色下划线/文字 */
.recycle-tabs :deep(.n-tabs-nav) {
  --n-tab-text-color: var(--glass-text-secondary);
  --n-tab-text-color-hover: var(--brand);
  --n-tab-text-color-active: var(--brand);
  --n-tab-text-color-disabled: var(--glass-text-placeholder);
  --n-bar-color: var(--brand);
}
.recycle-clear-btn {
  border-radius: var(--radius-pill) !important;
  background: transparent !important;
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent) !important;
  color: var(--danger-text) !important;
  font-weight: 500;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease), opacity var(--dur) var(--ease);
}
.recycle-clear-btn:hover {
  background: color-mix(in srgb, var(--danger) 16%, transparent) !important;
  border-color: var(--danger) !important;
}
.recycle-clear-btn[disabled] {
  opacity: 0.4;
}
.recycle-spin {
  min-height: 200px;
}
.recycle-empty {
  padding-top: 60px;
}
.recycle-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 8px;
}
.recycle-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease);
}
.recycle-item:hover { border-color: var(--brand); }
.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.item-title {
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-sub {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: var(--fs-sm);
  color: var(--glass-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.item-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
/* 恢复按钮：粉色渐变（玻璃体系） */
.restore-btn {
  height: 24px;
  padding: 0 10px;
  font-size: var(--fs-sm);
  border-radius: var(--radius-pill);
}
/* 永久删除：红色玻璃按钮 */
.recycle-danger-btn {
  border-radius: var(--radius-pill) !important;
  background: var(--glass-bg-trans) !important;
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent) !important;
  color: var(--danger-text) !important;
  font-weight: 500;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease), opacity var(--dur) var(--ease);
}
.recycle-danger-btn:hover {
  background: color-mix(in srgb, var(--danger) 16%, transparent) !important;
  border-color: var(--danger) !important;
}
.recycle-danger-btn[disabled] {
  opacity: 0.4;
}

/* 移动端（<768px） */
@media (max-width: 767px) {
  .recycle-config-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
  .config-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .recycle-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
  .recycle-item {
    flex-wrap: wrap;
    gap: 8px;
  }
  .item-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
