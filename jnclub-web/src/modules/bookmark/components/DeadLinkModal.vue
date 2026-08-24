<script setup lang="ts">
/**
 * DeadLinkModal.vue — 收藏失效检测结果弹窗
 * 显示检测统计 + 失效收藏列表 + 一键清理（真正删除）
 */
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NEmpty, useMessage, useDialog, NSpin, NTag } from 'naive-ui'
import { AlertTriangle, Trash2, Link2Off, ExternalLink, RefreshCw, Archive } from 'lucide-vue-next'
import axios from 'axios'
import SnapshotModal from './SnapshotModal.vue'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [v: boolean]; cleaned: [] }>()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const result = ref<{ total: number; ok: number; dead: number; error: number; deadList: any[] } | null>(null)

/** 快照查看 */
const snapShow = ref(false)
const snapBookmarkId = ref<number | null>(null)
const snapUrl = ref('')
const viewSnapshot = (d: any) => {
  snapBookmarkId.value = d.id
  snapUrl.value = d.url
  snapShow.value = true
}

const startCheck = async () => {
  loading.value = true
  result.value = null
  try {
    const res = await axios.post('/api/bookmarks/check-dead', {}, { timeout: 300000 })
    if (res.data.code === 200) {
      result.value = res.data.data
      if (res.data.data?.dead === 0) {
        message.success(`检测完成：${res.data.data.total} 个链接全部正常`)
      }
    } else {
      message.error(res.data.message || '检测失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '检测失败')
  } finally {
    loading.value = false
  }
}

// 弹窗打开时自动开始检测
watch(() => props.show, (v) => { if (v) startCheck() })

const cleanAll = () => {
  if (!result.value?.deadList.length) return
  dialog.warning({
    title: '删除失效收藏',
    content: `将彻底删除 ${result.value.deadList.length} 个失效收藏（不进入回收站）。确定继续吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const ids = result.value!.deadList.map(d => d.id)
        const res = await axios.post('/api/bookmarks/delete-dead', { ids })
        if (res.data.code === 200) {
          message.success(`已删除 ${res.data.data?.deleted ?? 0} 个失效收藏`)
          emit('cleaned')
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
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    preset="card"
    :style="{ width: '640px', maxWidth: '94vw' }"
    title="收藏失效检测"
  >
    <NSpin :show="loading">
      <template v-if="!result">
        <NEmpty v-if="!loading" description="正在准备检测…" class="dead-empty" />
        <div v-else class="dead-checking">
          <NIcon :component="RefreshCw" size="22" class="spin-icon" />
          <p>正在逐个检测链接可用性（约 {{ '每链接 0.3s' }}，请稍候）…</p>
        </div>
      </template>

      <template v-else>
        <!-- 统计条 -->
        <div class="dead-stats">
          <div class="stat-item"><span class="stat-num">{{ result.total }}</span><span class="stat-label">总数</span></div>
          <div class="stat-item ok"><span class="stat-num">{{ result.ok }}</span><span class="stat-label">正常</span></div>
          <div class="stat-item bad"><span class="stat-num">{{ result.dead }}</span><span class="stat-label">失效</span></div>
          <div class="stat-item err"><span class="stat-num">{{ result.error }}</span><span class="stat-label">无法判断</span></div>
          <NButton size="tiny" quaternary class="recheck-btn" @click="startCheck">
            <template #icon><NIcon :component="RefreshCw" size="13" /></template>
            重新检测
          </NButton>
        </div>

        <!-- 失效列表 -->
        <div v-if="result.deadList.length" class="dead-list">
          <div class="dead-list-head">
            <NIcon :component="AlertTriangle" size="14" />
            <span>发现 {{ result.deadList.length }} 个失效收藏</span>
            <NButton size="tiny" type="error" secondary @click="cleanAll">
              <template #icon><NIcon :component="Trash2" size="13" /></template>
              一键清理
            </NButton>
          </div>
          <div v-for="d in result.deadList" :key="d.id" class="dead-item">
            <div class="dead-item-icon"><NIcon :component="Link2Off" size="14" /></div>
            <div class="dead-item-main">
              <div class="dead-item-title">
                {{ d.title || '（无标题）' }}
                <NTag v-if="d.hasSnapshot" size="tiny" :bordered="false" class="snap-tag">
                  <NIcon :component="Archive" size="11" /> 有快照
                </NTag>
              </div>
              <div class="dead-item-url">{{ d.url }}</div>
            </div>
            <NButton v-if="d.hasSnapshot" size="tiny" secondary class="dead-item-snap" @click="viewSnapshot(d)">
              <template #icon><NIcon :component="Archive" size="13" /></template>
              查看快照
            </NButton>
            <a :href="d.url" target="_blank" rel="noopener noreferrer" class="dead-item-open" title="在新窗口打开确认">
              <NIcon :component="ExternalLink" size="14" />
            </a>
          </div>
        </div>
        <NEmpty v-else description="全部链接正常，没有失效收藏 🎉" class="dead-empty" />
      </template>
    </NSpin>

    <template #footer>
      <NButton size="small" quaternary @click="emit('update:show', false)">关闭</NButton>
    </template>
  </NModal>

  <!-- 失效收藏快照查看 -->
  <SnapshotModal
    v-model:show="snapShow"
    :bookmark-id="snapBookmarkId"
    :url="snapUrl"
    @changed="startCheck"
  />
</template>

<style scoped>
.dead-empty {
  padding: 32px 0;
}
.dead-checking {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 36px 0;
  color: var(--text-3);
  font-size: var(--fs-sm);
}
.spin-icon {
  animation: dead-spin 1s linear infinite;
  color: var(--brand);
}
@keyframes dead-spin {
  to { transform: rotate(360deg); }
}
.dead-stats {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 14px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}
.stat-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.stat-num {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-1);
}
.stat-item.ok .stat-num { color: var(--success); }
.stat-item.bad .stat-num { color: var(--danger-text); }
.stat-item.err .stat-num { color: var(--text-3); }
.stat-label {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.recheck-btn {
  margin-left: auto;
}
.dead-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 42vh;
  overflow-y: auto;
}
.dead-list-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--danger-text);
  margin-bottom: 4px;
}
.dead-list-head .n-button {
  margin-left: auto;
}
.dead-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid color-mix(in srgb, var(--danger) 25%, transparent);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--danger) 5%, transparent);
}
.dead-item-icon {
  width: 28px; height: 28px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  color: var(--danger-text);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.dead-item-main {
  flex: 1;
  min-width: 0;
}
.dead-item-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dead-item-url {
  font-size: var(--fs-xs);
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dead-item-open {
  color: var(--text-3);
  flex-shrink: 0;
  display: flex;
}
.dead-item-open:hover {
  color: var(--brand);
}
.snap-tag { margin-left: 6px; }
.dead-item-snap { flex-shrink: 0; }
</style>
