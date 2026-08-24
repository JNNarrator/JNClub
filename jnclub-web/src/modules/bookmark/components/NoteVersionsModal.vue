<script setup lang="ts">
/**
 * NoteVersionsModal.vue — 便签历史版本弹窗
 * 版本列表（时间倒序）→ 查看详情（只读预览）/ 回滚 / 版本对比（diff 高亮）
 */
import { ref, computed, watch } from 'vue'
import {
  NModal, NButton, NIcon, NTag, NEmpty, NSpin, NSelect, useMessage, useDialog,
} from 'naive-ui'
import { History, RotateCcw, Eye, Clock, GitCompare } from 'lucide-vue-next'
import { useNoteStore } from '../stores/note'
import { formatDate } from '../composables/formatDate'

const props = defineProps<{
  show: boolean
  noteId: number | null
  noteTitle: string
}>()
const emit = defineEmits<{ 'update:show': [v: boolean]; restored: [] }>()

const message = useMessage()
const dialog = useDialog()
const noteStore = useNoteStore()

const versions = ref<any[]>([])
const loading = ref(false)
const detail = ref<any | null>(null)
const detailLoading = ref(false)

/** 对比模式状态 */
const compareMode = ref(false)
const compareAId = ref<number | null>(null)
const compareBId = ref<number | null>(null)
const compareA = ref<any | null>(null)
const compareB = ref<any | null>(null)
const compareLoading = ref(false)

const load = async () => {
  if (!props.noteId) return
  loading.value = true
  detail.value = null
  compareMode.value = false
  try {
    versions.value = await noteStore.fetchVersions(props.noteId)
  } catch (e: any) {
    message.error(e.message || '获取版本失败')
  } finally {
    loading.value = false
  }
}

watch(() => props.show, (v) => { if (v) load() })

const viewVersion = async (v: any) => {
  if (!props.noteId) return
  detailLoading.value = true
  try {
    detail.value = await noteStore.fetchVersionDetail(props.noteId, v.id)
  } catch (e: any) {
    message.error(e.message || '获取版本详情失败')
  } finally {
    detailLoading.value = false
  }
}

const restore = (v: any) => {
  if (!props.noteId) return
  dialog.warning({
    title: '回滚到该版本',
    content: `确定将便签内容回滚到「版本 ${v.versionNo}」吗？当前内容会先保存为一个新版本，不会丢失。`,
    positiveText: '回滚',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await noteStore.restoreVersion(props.noteId!, v.id)
        message.success('已回滚到该版本')
        emit('restored')
        emit('update:show', false)
      } catch (e: any) {
        message.error(e.message || '回滚失败')
      }
    },
  })
}

// ============================================================
// 版本对比（diff）
// ============================================================

/** 版本选择下拉选项（版本号倒序展示） */
const versionOptions = computed(() =>
  versions.value.map(v => ({ label: `版本 ${v.versionNo} · ${formatDate(v.createTime)}`, value: v.id }))
)

/** 进入对比模式：默认 A=最早版本，B=最新版本 */
const openCompare = () => {
  if (versions.value.length < 2) { message.warning('至少需要 2 个版本才能对比'); return }
  compareMode.value = true
  detail.value = null
  const sorted = [...versions.value].sort((x, y) => x.versionNo - y.versionNo)
  compareAId.value = sorted[0].id
  compareBId.value = sorted[sorted.length - 1].id
  doCompare()
}

const exitCompare = () => {
  compareMode.value = false
  compareA.value = null
  compareB.value = null
}

const doCompare = async () => {
  if (!props.noteId || !compareAId.value || !compareBId.value) return
  if (compareAId.value === compareBId.value) {
    message.warning('请选择两个不同的版本')
    return
  }
  compareLoading.value = true
  try {
    const [a, b] = await Promise.all([
      noteStore.fetchVersionDetail(props.noteId, compareAId.value),
      noteStore.fetchVersionDetail(props.noteId, compareBId.value),
    ])
    compareA.value = a
    compareB.value = b
  } catch (e: any) {
    message.error(e.message || '对比失败')
  } finally {
    compareLoading.value = false
  }
}

/** 行级 diff（LCS 回溯）：返回 same/del/add 标记行 */
function diffLines(aText: string, bText: string): { type: 'same' | 'del' | 'add'; text: string }[] {
  const a = (aText || '').split('\n')
  const b = (bText || '').split('\n')
  const m = a.length, n = b.length
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }
  const out: { type: 'same' | 'del' | 'add'; text: string }[] = []
  let i = 0, j = 0
  while (i < m && j < n) {
    if (a[i] === b[j]) { out.push({ type: 'same', text: a[i] }); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { out.push({ type: 'del', text: a[i] }); i++ }
    else { out.push({ type: 'add', text: b[j] }); j++ }
  }
  while (i < m) { out.push({ type: 'del', text: a[i] }); i++ }
  while (j < n) { out.push({ type: 'add', text: b[j] }); j++ }
  return out
}

const diffResult = computed(() => {
  if (!compareA.value || !compareB.value) return []
  return diffLines(compareA.value.content || '', compareB.value.content || '')
})

const diffStat = computed(() => {
  if (!diffResult.value.length) return { add: 0, del: 0 }
  let add = 0, del = 0
  for (const l of diffResult.value) {
    if (l.type === 'add') add++
    else if (l.type === 'del') del++
  }
  return { add, del }
})
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    preset="card"
    :style="{ width: '700px', maxWidth: '94vw' }"
    title="历史版本"
  >
    <div class="ver-title">
      <NIcon :component="History" size="15" />
      <span class="ver-title-text">{{ props.noteTitle || '未命名便签' }}</span>
      <NTag v-if="versions.length" size="tiny" round :bordered="false" class="ver-count">{{ versions.length }} 个版本</NTag>
      <NButton
        v-if="!compareMode && versions.length >= 2"
        size="tiny" quaternary class="compare-btn" title="对比两个版本的内容差异" @click="openCompare"
      >
        <template #icon><NIcon :component="GitCompare" size="14" /></template>
        对比版本
      </NButton>
    </div>

    <NSpin :show="loading" class="ver-spin">
      <NEmpty v-if="!loading && !versions.length" description="暂无历史版本（编辑保存后自动生成）" class="ver-empty" />

      <!-- 对比模式 -->
      <div v-else-if="compareMode" class="compare-panel">
        <div class="compare-toolbar">
          <NButton size="tiny" quaternary @click="exitCompare">
            <template #icon><NIcon :component="RotateCcw" size="13" /></template>
            返回列表
          </NButton>
          <div class="compare-selects">
            <span class="compare-label">旧版</span>
            <NSelect
              :value="compareAId" :options="versionOptions" size="tiny" class="compare-select"
              @update:value="(v: number | null) => { compareAId = v; doCompare() }"
            />
            <span class="compare-arrow">→</span>
            <span class="compare-label">新版</span>
            <NSelect
              :value="compareBId" :options="versionOptions" size="tiny" class="compare-select"
              @update:value="(v: number | null) => { compareBId = v; doCompare() }"
            />
          </div>
          <NTag v-if="diffResult.length" size="tiny" round :bordered="false" class="diff-stat">
            <span class="diff-add">+{{ diffStat.add }}</span>
            <span class="diff-del">−{{ diffStat.del }}</span>
          </NTag>
        </div>

        <NSpin :show="compareLoading">
          <div class="diff-body">
            <template v-if="diffResult.length">
              <div
                v-for="(l, idx) in diffResult" :key="idx"
                class="diff-line"
                :class="`diff-${l.type}`"
              >
                <span class="diff-marker">{{ l.type === 'add' ? '+' : l.type === 'del' ? '−' : ' ' }}</span>
                <span class="diff-text">{{ l.text || '（空行）' }}</span>
              </div>
            </template>
            <NEmpty v-else description="两个版本内容完全一致" class="ver-empty" />
          </div>
        </NSpin>
      </div>

      <!-- 详情模式 -->
      <div v-else-if="detail" class="ver-detail">
        <div class="ver-detail-head">
          <NButton size="tiny" quaternary @click="detail = null">
            <template #icon><NIcon :component="RotateCcw" size="13" /></template>
            返回列表
          </NButton>
          <span class="ver-detail-no">版本 {{ detail.versionNo }} · {{ formatDate(detail.createTime) }}</span>
          <NButton size="tiny" type="primary" secondary @click="restore(detail)">
            <template #icon><NIcon :component="RotateCcw" size="13" /></template>
            回滚到此版本
          </NButton>
        </div>
        <NSpin :show="detailLoading">
          <div class="ver-detail-body">
            <pre class="ver-detail-content">{{ detail.content || '（空内容）' }}</pre>
          </div>
        </NSpin>
      </div>

      <!-- 列表模式 -->
      <div v-else class="ver-list">
        <div
          v-for="v in versions" :key="v.id"
          class="ver-item jnclub-bouncy"
          @click="viewVersion(v)"
        >
          <div class="ver-item-icon"><NIcon :component="Clock" size="15" /></div>
          <div class="ver-item-main">
            <div class="ver-item-head">
              <span class="ver-item-no">版本 {{ v.versionNo }}</span>
              <span class="ver-item-time">{{ formatDate(v.createTime) }}</span>
            </div>
            <div class="ver-item-title">{{ v.title || '（无标题）' }}</div>
          </div>
          <NButton size="tiny" quaternary circle title="查看" @click.stop="viewVersion(v)">
            <template #icon><NIcon :component="Eye" size="14" /></template>
          </NButton>
        </div>
      </div>
    </NSpin>

    <template #footer>
      <NButton size="small" quaternary @click="emit('update:show', false)">关闭</NButton>
    </template>
  </NModal>
</template>

<style scoped>
.ver-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--text-2);
}
.ver-title-text {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ver-count {
  background: var(--brand-soft) !important;
  color: var(--brand) !important;
}
.compare-btn {
  margin-left: auto;
  color: var(--brand);
}
.ver-spin {
  min-height: 160px;
}
.ver-empty {
  padding: 40px 0;
}
.ver-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 46vh;
  overflow-y: auto;
}
.ver-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), background var(--dur) var(--ease);
}
.ver-item:hover {
  border-color: var(--brand);
  background: var(--hover-bg);
}
.ver-item-icon {
  width: 32px; height: 32px;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.ver-item-main {
  flex: 1;
  min-width: 0;
}
.ver-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ver-item-no {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-1);
}
.ver-item-time {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.ver-item-title {
  font-size: var(--fs-sm);
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}
.ver-detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.ver-detail-no {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--text-2);
}
.ver-detail-body {
  max-height: 46vh;
  overflow-y: auto;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
  padding: 14px;
}
.ver-detail-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: 1.6;
}

/* === 对比模式 === */
.compare-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.compare-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.compare-selects {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}
.compare-label {
  font-size: var(--fs-xs);
  color: var(--text-3);
  flex-shrink: 0;
}
.compare-arrow {
  color: var(--brand);
  font-size: var(--fs-sm);
  flex-shrink: 0;
}
.compare-select {
  width: 160px;
}
.diff-stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-page) !important;
}
.diff-add { color: var(--success); font-weight: 700; }
.diff-del { color: var(--danger-text); font-weight: 700; }
.diff-body {
  max-height: 46vh;
  overflow-y: auto;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
  font-family: var(--font-mono, monospace);
  font-size: var(--fs-sm);
  line-height: 1.6;
}
.diff-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 1px 10px;
  white-space: pre-wrap;
  word-break: break-word;
}
.diff-marker {
  flex-shrink: 0;
  width: 14px;
  text-align: center;
  color: var(--text-3);
  user-select: none;
}
.diff-text {
  flex: 1;
  min-width: 0;
}
.diff-add {
  background: color-mix(in srgb, var(--success) 16%, transparent);
  color: var(--text-1);
}
.diff-add .diff-marker { color: var(--success); }
.diff-del {
  background: color-mix(in srgb, var(--state-error) 16%, transparent);
  color: var(--text-1);
}
.diff-del .diff-marker { color: var(--danger-text); }
</style>
