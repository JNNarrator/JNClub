<script setup lang="ts">
/**
 * SnapshotModal.vue — 收藏网页快照查看
 * 通过 /api/snapshots/{bookmarkId}/content 加载快照 HTML（登录鉴权），iframe 渲染；
 * 附带元信息（抓取时间/大小）+「打开原文」兜底 + 删除快照
 */
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NSpin, NEmpty, useMessage, useDialog } from 'naive-ui'
import { Archive, ExternalLink, X, Trash2, RefreshCw } from 'lucide-vue-next'
import { formatDate } from '../composables/formatDate'
import axios from 'axios'

const props = defineProps<{
  show: boolean
  bookmarkId: number | null
  url?: string
}>()
const emit = defineEmits<{ 'update:show': [v: boolean]; changed: [] }>()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const error = ref('')
const meta = ref<{ title: string; url: string; size: number; capturedAt: string } | null>(null)
const contentUrl = ref('')

const load = async () => {
  if (!props.bookmarkId) return
  loading.value = true
  error.value = ''
  meta.value = null
  try {
    const res = await axios.get(`/api/snapshots/${props.bookmarkId}`)
    if (res.data.code === 200) {
      const d = res.data.data
      meta.value = d
      // 时间戳加 ?t= 防 iframe 缓存旧快照
      contentUrl.value = `/api/snapshots/${props.bookmarkId}/content?t=${Date.now()}`
    } else {
      error.value = res.data.message || '快照加载失败'
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || '快照加载失败'
  } finally { loading.value = false }
}

watch(() => props.show, (v) => { if (v) load() })

const deleteSnapshot = () => {
  if (!props.bookmarkId) return
  dialog.warning({
    title: '删除快照',
    content: '将删除该收藏的网页快照（dufs 对象 + 记录），原收藏不受影响。确定继续吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await axios.delete(`/api/snapshots/${props.bookmarkId}`)
        if (res.data.code === 200) {
          message.success('快照已删除')
          emit('changed')
          emit('update:show', false)
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch (e: any) {
        message.error(e.response?.data?.message || e.message || '删除失败')
      }
    },
  })
}

const fmtSize = (n: number) => n > 1024 * 1024 ? (n / 1024 / 1024).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB'
</script>

<template>
  <NModal
    :show="show"
    @update:show="(v: boolean) => emit('update:show', v)"
    :style="{ width: 'min(880px, 94vw)' }"
    class="snap-modal"
  >
    <div class="snap-shell">
      <div class="snap-head">
        <div class="snap-title-wrap">
          <NIcon :component="Archive" size="16" class="snap-title-icon" />
          <span class="snap-title">网页快照 · {{ meta?.title || '加载中…' }}</span>
        </div>
        <div class="snap-actions">
          <span v-if="meta" class="snap-meta">
            {{ formatDate(meta.capturedAt) }} · {{ fmtSize(meta.size) }}
          </span>
          <a v-if="meta?.url || props.url" :href="meta?.url || props.url" target="_blank" rel="noopener" class="snap-open">
            <NIcon :component="ExternalLink" size="14" /> 打开原文
          </a>
          <NButton quaternary circle size="small" title="重新加载" @click="load">
            <template #icon><NIcon :component="RefreshCw" size="14" /></template>
          </NButton>
          <NButton quaternary circle size="small" type="error" title="删除快照" @click="deleteSnapshot">
            <template #icon><NIcon :component="Trash2" size="14" /></template>
          </NButton>
          <NButton quaternary circle size="small" @click="emit('update:show', false)">
            <template #icon><NIcon :component="X" size="16" /></template>
          </NButton>
        </div>
      </div>

      <div class="snap-body">
        <NSpin :show="loading">
          <div v-if="loading" class="snap-hint">正在加载快照…</div>
          <div v-else-if="error" class="snap-error">
            <NEmpty :description="error" class="snap-empty" />
          </div>
          <iframe v-else-if="contentUrl" :src="contentUrl" class="snap-frame" sandbox="allow-same-origin" referrerpolicy="no-referrer" />
        </NSpin>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.snap-shell {
  display: flex;
  flex-direction: column;
  max-height: 86vh;
  border-radius: var(--radius-md);
  background: var(--glass-bg-solid);
  overflow: hidden;
}
.snap-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.snap-title-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; }
.snap-title-icon { color: var(--brand); flex-shrink: 0; }
.snap-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.snap-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.snap-meta { font-size: var(--fs-xs); color: var(--text-3); white-space: nowrap; }
.snap-open {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-sm);
  color: var(--brand);
  text-decoration: none;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--brand-soft);
  white-space: nowrap;
}
.snap-open:hover { filter: brightness(1.05); }
.snap-body { flex: 1; min-height: 0; }
.snap-hint {
  padding: 60px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.snap-error { padding: 40px 0; }
.snap-frame {
  width: 100%;
  height: 72vh;
  border: none;
  background: #fff;
}
</style>
