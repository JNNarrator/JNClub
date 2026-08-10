<script setup lang="ts">
/**
 * SearchDrawer.vue — 全局搜索抽屉（Ctrl/Cmd+K 唤起）
 * 收藏(标题+URL) / 便签(标题+内容摘要) / 云盘(文件名) 分组展示
 * 点击结果 → 切到对应模块并选中目录
 */
import { ref, watch } from 'vue'
import { NDrawer, NInput, NIcon, NEmpty, NSpin, NEllipsis } from 'naive-ui'
import { Search, Bookmark, StickyNote, FileText, ArrowRight } from 'lucide-vue-next'
import axios from 'axios'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  /** 跳转：切模块 + 选目录 */
  'jump': [module: 'bookmarks' | 'notes' | 'files', directoryId: number | null]
}>()

const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const result = ref<{
  bookmarks: any[]
  notes: any[]
  files: any[]
}>({ bookmarks: [], notes: [], files: [] })

let timer: ReturnType<typeof setTimeout> | null = null

watch(() => props.show, (v) => {
  if (v) {
    keyword.value = ''
    result.value = { bookmarks: [], notes: [], files: [] }
    searched.value = false
  }
})

const doSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) {
    result.value = { bookmarks: [], notes: [], files: [] }
    searched.value = false
    return
  }
  loading.value = true
  try {
    const res = await axios.get('/api/search', { params: { keyword: kw, limit: 20 } })
    if (res.data.code === 200) {
      result.value = res.data.data || { bookmarks: [], notes: [], files: [] }
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

const handleJump = (module: 'bookmarks' | 'notes' | 'files', directoryId: number | null) => {
  emit('close')
  emit('jump', module, directoryId)
}
</script>

<template>
  <NDrawer v-model:show="props.show" :width="420" placement="right" @update:show="(v: boolean) => !v && emit('close')">
    <div class="search-panel">
      <!-- 标题 -->
      <div class="search-header">
        <div class="search-title">
          <NIcon :component="Search" size="16" />
          全局搜索
        </div>
        <span class="search-hint">Ctrl / ⌘ + K</span>
      </div>

      <!-- 输入框 -->
      <NInput
        v-model:value="keyword"
        size="large"
        placeholder="搜索收藏、便签、文件…"
        clearable
        @input="onInput"
        @keyup.enter="doSearch"
      >
        <template #prefix><NIcon :component="Search" size="16" /></template>
      </NInput>

      <NSpin :show="loading" class="search-spin">
        <!-- 空输入 -->
        <NEmpty v-if="!keyword.trim()" description="输入关键词搜索收藏 / 便签 / 文件" class="search-empty" />

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
              v-for="b in result.bookmarks" :key="b.id"
              class="result-item jnclub-bouncy" @click="handleJump('bookmarks', b.directoryId)"
            >
              <img v-if="b.icon" :src="b.icon" class="item-icon" @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')" />
              <NIcon v-else :component="Bookmark" size="15" class="item-fallback" />
              <div class="item-main">
                <NEllipsis class="item-title">{{ b.title || b.url }}</NEllipsis>
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
              v-for="n in result.notes" :key="n.id"
              class="result-item jnclub-bouncy" @click="handleJump('notes', n.directoryId)"
            >
              <NIcon :component="StickyNote" size="15" class="item-fallback" />
              <div class="item-main">
                <NEllipsis class="item-title">{{ n.title || '无标题' }}</NEllipsis>
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
              v-for="f in result.files" :key="f.id"
              class="result-item jnclub-bouncy" @click="handleJump('files', f.directoryId)"
            >
              <NIcon :component="FileText" size="15" class="item-fallback" />
              <div class="item-main">
                <NEllipsis class="item-title">{{ f.originalName }}</NEllipsis>
                <span class="item-size">{{ f.size ? `${(f.size / 1024 / 1024).toFixed(1)} MB` : '' }}</span>
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
.search-hint {
  font-size: 11px;
  color: var(--text-3);
  background: var(--hover-bg);
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
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}
.group-count {
  font-size: 11px;
  color: var(--text-3);
}
.result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.result-item:hover {
  background: var(--hover-bg);
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
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
}
.item-sub {
  font-size: 12px;
  color: var(--text-3);
}
.item-size {
  font-size: 11px;
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
</style>
