<script setup lang="ts">
/**
 * ShareManagerDrawer.vue — 我的分享管理抽屉
 * 跨类型列出全部公开分享：标题/类型/有效期/访问统计，支持复制/撤销
 */
import { ref, onMounted } from 'vue'
import { NDrawer, NButton, NIcon, NSpin, NTag, useMessage } from 'naive-ui'
import { Copy, Trash2, StickyNote, Bookmark, FileText, RefreshCw } from 'lucide-vue-next'
import JEmptyState from '../components/ui/JEmptyState.vue'
import JErrorState from '../components/ui/JErrorState.vue'
import { copyText } from '../utils/clipboard'
import { formatDate, formatRelativeTime } from '../../modules/bookmark/composables/formatDate'
import axios from 'axios'

defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [v: boolean] }>()

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<any[]>([])

interface ShareRow {
  token: string
  refType: string
  refId: number
  title: string
  hasPassword: boolean
  expiresAt: string | null
  visitCount: number
  lastVisitAt: string | null
  createTime: string
  url: string
}

const TYPE_META: Record<string, { label: string; icon: any }> = {
  note: { label: '便签', icon: StickyNote },
  bookmark: { label: '收藏', icon: Bookmark },
  file: { label: '文件', icon: FileText },
}

const load = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await axios.get('/api/share/mine')
    if (res.data.code === 200) list.value = (res.data.data || []) as ShareRow[]
    else loadError.value = true
  } catch (e: any) {
    loadError.value = true
    message.error(e.response?.data?.message || '加载失败')
  } finally { loading.value = false }
}

onMounted(load)

const copyLink = async (row: ShareRow) => {
  if (await copyText(`${location.origin}${row.url}`)) message.success('链接已复制')
  else message.error('复制失败')
}

const revoke = async (row: ShareRow) => {
  try {
    await axios.delete(`/api/share/${row.token}`)
    list.value = list.value.filter(r => r.token !== row.token)
    message.success('已撤销')
  } catch (e: any) {
    message.error(e.response?.data?.message || '撤销失败')
  }
}

const isExpired = (row: ShareRow) => row.expiresAt && new Date(row.expiresAt).getTime() < Date.now()
</script>

<template>
  <NDrawer :show="show" placement="right" :width="420" @update:show="(v: boolean) => emit('update:show', v)">
    <div class="smd-head">
      <h3 class="smd-title">我的分享</h3>
      <NButton size="small" quaternary @click="load">
        <template #icon><NIcon :component="RefreshCw" size="14" /></template>
        刷新
      </NButton>
    </div>

    <div v-if="loading" class="smd-state"><NSpin size="small" /> 加载中…</div>
    <JErrorState
      v-else-if="loadError"
      message="分享列表加载失败"
      hint="请检查网络后重试"
      class="smd-empty"
      @retry="load"
    />
    <JEmptyState
      v-else-if="!list.length"
      message="还没有创建过分享"
      hint="在收藏、便签或云盘文件中生成分享后会显示在这里"
      :show-cta="false"
      class="smd-empty"
    />

    <div v-else class="smd-list">
      <div v-for="row in list" :key="row.token" class="smd-row" :class="{ expired: isExpired(row) }">
        <div class="smd-row-main">
          <div class="smd-row-title">
            <NIcon :component="(TYPE_META[row.refType] || TYPE_META.file).icon" size="14" class="smd-type-ic" />
            <span class="smd-name" :title="row.title">{{ row.title || '（无标题）' }}</span>
            <NTag v-if="row.hasPassword" size="tiny" :bordered="false" class="smd-tag">密码</NTag>
            <NTag v-if="isExpired(row)" size="tiny" type="error" :bordered="false" class="smd-tag">已过期</NTag>
          </div>
          <div class="smd-meta">
            <span>{{ TYPE_META[row.refType]?.label }}</span>
            <span v-if="row.expiresAt">有效期至 {{ formatDate(row.expiresAt) }}</span>
            <span v-else>永不过期</span>
            <span>访问 {{ row.visitCount ?? 0 }} 次</span>
            <span v-if="row.lastVisitAt">最近 {{ formatRelativeTime(row.lastVisitAt) }}</span>
          </div>
        </div>
        <div class="smd-actions">
          <NButton size="tiny" secondary @click="copyLink(row)">
            <template #icon><NIcon :component="Copy" size="13" /></template>
            复制
          </NButton>
          <NButton size="tiny" type="error" secondary @click="revoke(row)">
            <template #icon><NIcon :component="Trash2" size="13" /></template>
            撤销
          </NButton>
        </div>
      </div>
    </div>
  </NDrawer>
</template>

<style scoped>
.smd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 14px;
}
.smd-title { margin: 0; font-size: 17px; font-weight: 700; color: var(--text-1); }
.smd-state { display: flex; align-items: center; gap: 8px; color: var(--text-3); font-size: var(--fs-sm); padding: 30px 0; justify-content: center; }
.smd-empty { padding: 40px 0; }
.smd-list { display: flex; flex-direction: column; gap: 10px; }
.smd-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
}
.smd-row.expired { opacity: .55; }
.smd-row-main { min-width: 0; flex: 1; }
.smd-row-title { display: flex; align-items: center; gap: 6px; min-width: 0; }
.smd-type-ic { color: var(--brand); flex-shrink: 0; }
.smd-name {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.smd-tag { margin-left: 2px; }
.smd-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.smd-actions { display: flex; gap: 6px; flex-shrink: 0; }
</style>
