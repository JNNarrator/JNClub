// RecycleView.vue — 回收站内容区
<script setup lang="ts">
/**
 * RecycleView.vue — 回收站内容区（软删除条目查看/恢复/永久删除/清空）
 * 现由 RecycleLayout 套用主壳渲染：顶栏/导航/TabBar 与其他模块一致
 */
import { ref, onMounted, watch } from 'vue'
import {
  NButton, NIcon, NSpin, NEmpty, NTabs, NTabPane,
  useMessage, useDialog,
} from 'naive-ui'
import { Trash2, RotateCcw, Eraser, Clock } from 'lucide-vue-next'
import axios from 'axios'
import { formatRelativeTime } from '../composables/formatDate'

const props = defineProps<{ refresh?: number }>()

const message = useMessage()
const dialog = useDialog()

type RecycleType = 'bookmark' | 'note' | 'file' | 'vault'
const activeType = ref<RecycleType>('bookmark')
const items = ref<any[]>([])
const loading = ref(false)

const typeLabels: Record<RecycleType, string> = {
  bookmark: '收藏',
  note: '便签',
  file: '云盘文件',
  vault: '密码',
}

const fetchItems = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/recycle', { params: { type: activeType.value } })
    if (res.data.code === 200) {
      items.value = res.data.data || []
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载失败')
  } finally { loading.value = false }
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

onMounted(fetchItems)
</script>

<template>
  <div>
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

    <NSpin :show="loading" class="recycle-spin">
      <NEmpty v-if="!loading && !items.length" description="回收站是空的" class="recycle-empty" />
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
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
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
  border: 1px solid rgba(245, 72, 92, 0.4) !important;
  color: #ff8a97 !important;
  font-weight: 500;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease), opacity var(--dur) var(--ease);
}
.recycle-clear-btn:hover {
  background: rgba(245, 72, 92, 0.16) !important;
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
  border: 1px solid rgba(245, 72, 92, 0.4) !important;
  color: #ff8a97 !important;
  font-weight: 500;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease), opacity var(--dur) var(--ease);
}
.recycle-danger-btn:hover {
  background: rgba(245, 72, 92, 0.16) !important;
  border-color: var(--danger) !important;
}
.recycle-danger-btn[disabled] {
  opacity: 0.4;
}

/* 移动端（<768px） */
@media (max-width: 767px) {
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
