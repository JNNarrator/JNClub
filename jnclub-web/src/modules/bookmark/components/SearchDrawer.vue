<script setup lang="ts">
/**
 * SearchDrawer.vue — 全局搜索抽屉（Ctrl/Cmd+K 唤起）
 * 收藏(标题+URL) / 便签(标题+内容摘要) / 云盘(文件名) 分组展示
 * 点击结果 → 切到对应模块并选中目录
 */
import { ref, watch } from 'vue'
import { NDrawer, NInput, NIcon, NEmpty, NSpin, NEllipsis } from 'naive-ui'
import { Search, Bookmark, StickyNote, FileText, KeyRound, Tag, Music, ArrowRight } from 'lucide-vue-next'
import axios from 'axios'
import { JGradientText } from '../../../shared/components/animation'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  /** 跳转：切模块 + 选目录（music 直接开播放器，无目录） */
  'jump': [module: 'bookmarks' | 'notes' | 'files' | 'vault' | 'music', directoryId: number | null]
}>()

const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const result = ref<{
  bookmarks: any[]
  notes: any[]
  files: any[]
  vault: any[]
  tags: any[]
  tracks: any[]
}>({ bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] })

let timer: ReturnType<typeof setTimeout> | null = null

watch(() => props.show, (v) => {
  if (v) {
    keyword.value = ''
    result.value = { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
    searched.value = false
  }
})

const doSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) {
    result.value = { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
    searched.value = false
    return
  }
  loading.value = true
  try {
    const res = await axios.get('/api/search', { params: { keyword: kw, limit: 20 } })
    if (res.data.code === 200) {
      result.value = res.data.data || { bookmarks: [], notes: [], files: [], vault: [], tags: [], tracks: [] }
      searched.value = true
    }
  } catch { /* 静默 */ }
  finally { loading.value = false }
}

const onInput = () => {
  if (timer) clearTimeout(timer)
  timer = setTimeout(doSearch, 300)
}

const total = () => result.value.bookmarks.length + result.value.notes.length + result.value.files.length
  + result.value.vault.length + result.value.tags.length + result.value.tracks.length

const handleJump = (module: 'bookmarks' | 'notes' | 'files' | 'vault' | 'music', directoryId: number | null) => {
  emit('close')
  emit('jump', module, directoryId)
}

/** 高亮渲染：按后端返回的 {field, ranges:[[s,e]]} 把命中词包 <mark>（防注入转义） */
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
function highlightText(text: string, highlights: any[], field: string): string {
  if (!text) return ''
  const h = (highlights || []).find((x: any) => x.field === field)
  if (!h || !h.ranges?.length) return escapeHtml(text)
  let html = ''
  let last = 0
  for (const [s, e] of h.ranges) {
    html += escapeHtml(text.slice(last, s)) + '<mark class="hl-mark">' + escapeHtml(text.slice(s, e)) + '</mark>'
    last = e
  }
  html += escapeHtml(text.slice(last))
  return html
}

/** 移动端抽屉全宽（NDrawer width 支持 number 或字符串，'100%' 在窄屏生效） */
const isMobileWidth = () => (typeof window !== 'undefined' && window.innerWidth < 768 ? '100%' : 420)
</script>

<template>
  <NDrawer v-model:show="props.show" :width="isMobileWidth()" placement="right" @update:show="(v: boolean) => !v && emit('close')">
    <div class="search-panel">
      <!-- 标题（图标放渐变外：JGradientText 走 text prop，slot 拼接对转发链不可靠） -->
      <div class="search-header">
        <div class="search-title">
          <NIcon :component="Search" size="16" class="search-title-icon" />
          <JGradientText
            text="全局搜索"
            :animation-speed="6"
            direction="horizontal"
            :colors="['var(--brand)', 'var(--brand-suppl)', 'var(--brand)']"
          />
        </div>
        <span class="search-hint">Ctrl / ⌘ + K</span>
      </div>

      <!-- 输入框 -->
      <NInput
        v-model:value="keyword"
        size="large"
        placeholder="搜索收藏 / 便签 / 文件 / 密码 / 音乐…"
        clearable
        @input="onInput"
        @keyup.enter="doSearch"
      >
        <template #prefix><NIcon :component="Search" size="16" /></template>
      </NInput>

      <NSpin :show="loading" class="search-spin">
        <!-- 空输入 -->
        <NEmpty v-if="!keyword.trim()" description="输入关键词搜索收藏 / 便签 / 文件 / 密码 / 音乐" class="search-empty" />

        <!-- 无结果 -->
        <div v-else-if="searched && total() === 0" class="no-result">
          <NEmpty description="没有找到相关内容" class="search-empty" />
        </div>

        <!-- 结果 -->
        <div v-else class="search-results">
          <!-- 收藏 -->
          <div v-if="result.bookmarks.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Bookmark" size="14" /> 收藏
              <span class="group-count">{{ result.bookmarks.length }}</span>
            </div>
            <div
              v-for="(b, idx) in result.bookmarks" :key="b.id"
              class="result-item jnclub-bouncy" @click="handleJump('bookmarks', b.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <img v-if="b.icon" :src="b.icon" class="item-icon" @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')" />
              <NIcon v-else :component="Bookmark" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(b.title || b.url, b.highlights, b.title ? 'title' : 'url')" />
                <NEllipsis class="item-sub">{{ b.url }}</NEllipsis>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 便签 -->
          <div v-if="result.notes.length" class="result-group">
            <div class="group-title">
              <NIcon :component="StickyNote" size="14" /> 便签
              <span class="group-count">{{ result.notes.length }}</span>
            </div>
            <div
              v-for="(n, idx) in result.notes" :key="n.id"
              class="result-item jnclub-bouncy" @click="handleJump('notes', n.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="StickyNote" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(n.title || '无标题', n.highlights, 'title')" />
                <NEllipsis v-if="n.excerpt" class="item-sub">{{ n.excerpt }}</NEllipsis>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 云盘 -->
          <div v-if="result.files.length" class="result-group">
            <div class="group-title">
              <NIcon :component="FileText" size="14" /> 云盘文件
              <span class="group-count">{{ result.files.length }}</span>
            </div>
            <div
              v-for="(f, idx) in result.files" :key="f.id"
              class="result-item jnclub-bouncy" @click="handleJump('files', f.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="FileText" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(f.originalName, f.highlights, 'originalName')" />
                <span class="item-size">{{ f.size ? `${(f.size / 1024 / 1024).toFixed(1)} MB` : '' }}</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 密码库（仅标题，安全） -->
          <div v-if="result.vault.length" class="result-group">
            <div class="group-title">
              <NIcon :component="KeyRound" size="14" /> 密码库
              <span class="group-count">{{ result.vault.length }}</span>
            </div>
            <div
              v-for="(v, idx) in result.vault" :key="v.id"
              class="result-item jnclub-bouncy" @click="handleJump('vault', v.directoryId)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="KeyRound" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(v.name, v.highlights, 'name')" />
                <span class="item-sub">密码库条目</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 标签 -->
          <div v-if="result.tags.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Tag" size="14" /> 标签
              <span class="group-count">{{ result.tags.length }}</span>
            </div>
            <div
              v-for="(t, idx) in result.tags" :key="t.id"
              class="result-item jnclub-bouncy" @click="handleJump('bookmarks', null)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="Tag" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(t.name, t.highlights, 'name')" />
                <span class="item-sub">{{ t.count || 0 }} 条关联</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>

          <!-- 音乐曲目 -->
          <div v-if="result.tracks.length" class="result-group">
            <div class="group-title">
              <NIcon :component="Music" size="14" /> 音乐
              <span class="group-count">{{ result.tracks.length }}</span>
            </div>
            <div
              v-for="(t, idx) in result.tracks" :key="t.trackId"
              class="result-item jnclub-bouncy" @click="handleJump('music', null)"
              :style="{ animationDelay: `${Math.min(idx * 35, 300)}ms` }"
            >
              <NIcon :component="Music" size="15" class="item-fallback" />
              <div class="item-main">
                <div class="item-title hl-text" v-html="highlightText(t.name, t.highlights, 'name')" />
                <span class="item-sub">{{ t.artist }}</span>
              </div>
              <ArrowRight :size="14" class="item-arrow" />
            </div>
          </div>
        </div>
      </NSpin>
    </div>
  </NDrawer>
</template>

<style scoped>
.search-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}
.search-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.search-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.search-title-icon {
  color: var(--brand);
}
.search-hint {
  font-size: var(--fs-xs);
  color: var(--text-3);
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  padding: 2px 8px;
  border-radius: var(--radius-pill);
}
.search-spin {
  flex: 1;
  overflow: hidden;
}
.search-empty {
  padding-top: 80px;
}
.search-results {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.result-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-2);
}
.group-count {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  opacity: 0;
  animation: search-item-in .3s var(--ease) forwards;
}
.result-item:hover {
  background: var(--glass-chip-bg);
}
@keyframes search-item-in {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
.item-icon {
  width: 18px;
  height: 18px;
  border-radius: 3px;
  flex-shrink: 0;
}
.item-fallback {
  color: var(--brand);
  flex-shrink: 0;
}
.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.item-title {
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-1);
}
.hl-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hl-mark {
  background: var(--brand-soft);
  color: var(--brand);
  border-radius: 2px;
  padding: 0 1px;
}
.item-sub {
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.item-size {
  font-size: var(--fs-xs);
  color: var(--text-3);
}
.item-arrow {
  color: var(--text-3);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}
.result-item:hover .item-arrow {
  opacity: 1;
}
.no-result {
  padding-top: 40px;
}

/* 移动端：抽屉全宽时收紧内边距 */
@media (max-width: 767px) {
  .search-panel {
    padding: 16px;
  }
  .result-item {
    padding: 10px 8px;
  }
  .item-arrow {
    opacity: 1;
  }
}
</style>
