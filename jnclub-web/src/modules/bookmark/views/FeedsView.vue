<script setup lang="ts">
/**
 * FeedsView.vue — RSS 阅读器三栏视图
 * 左：订阅源列表（未读角标/增删/刷新/全部已读）
 * 中：条目列表（标题/摘要/时间/星标，未读加粗）
 * 右：阅读窗格（content 渲染，打开即已读；收藏到收藏夹）
 */
import { ref, computed, watch, onMounted } from 'vue'
import {
  NButton, NIcon, NInput, NTag, useMessage,
  NModal, NSelect, NScrollbar, NBadge,
} from 'naive-ui'
import {
  Plus, Trash2, RefreshCw, CheckCheck, Star, Bookmark, Rss, ExternalLink, Clock,
} from 'lucide-vue-next'
import axios from 'axios'
import JSkeletonList from '../../../shared/components/ui/JSkeletonList.vue'
import JEmptyState from '../../../shared/components/ui/JEmptyState.vue'
import JErrorState from '../../../shared/components/ui/JErrorState.vue'
import { formatRelativeTime } from '../composables/formatDate'
import { useDirectoryStore } from '../stores/directory'

const props = defineProps<{ refresh: number }>()
const message = useMessage()
const dirStore = useDirectoryStore()

interface Feed {
  id: number
  url: string
  title: string
  siteUrl: string
  icon: string
  lastFetchedAt: string | null
  fetchIntervalMin: number
  unread: number
}
interface FeedItemRow {
  id: number
  feedId: number
  title: string
  link: string
  author: string
  summary: string
  content: string
  publishedAt: string | null
  readFlag: number
  starred: number
}

const feeds = ref<Feed[]>([])
const items = ref<FeedItemRow[]>([])
const total = ref(0)
const loadingFeeds = ref(false)
const loadingItems = ref(false)
const feedsError = ref(false)
const itemsError = ref(false)

const activeFeedId = ref<number | null>(null) // null = 全部
const filter = ref<'all' | 'unread' | 'starred'>('all')
const page = ref(1)
const PAGE_SIZE = 50
const loadingMore = ref(false)

const selected = ref<FeedItemRow | null>(null)
const reading = ref<FeedItemRow | null>(null)

/* ─── 加载订阅源 ─── */
const fetchFeeds = async () => {
  loadingFeeds.value = true
  feedsError.value = false
  try {
    const res = await axios.get('/api/feeds')
    if (res.data.code === 200) feeds.value = res.data.data || []
    else feedsError.value = true
  } catch (e: any) {
    feedsError.value = true
    message.error(e.response?.data?.message || '加载订阅源失败')
  } finally { loadingFeeds.value = false }
}

/* ─── 加载条目（每次重新拉第一页） ─── */
const fetchItems = async () => {
  page.value = 1
  loadingItems.value = true
  itemsError.value = false
  try {
    const res = await axios.get('/api/feeds/items', {
      params: { feedId: activeFeedId.value, filter: filter.value, page: page.value, size: PAGE_SIZE },
    })
    if (res.data.code === 200) {
      items.value = res.data.data?.items || []
      total.value = res.data.data?.total || 0
    } else itemsError.value = true
  } catch (e: any) {
    itemsError.value = true
    message.error(e.response?.data?.message || '加载条目失败')
  } finally { loadingItems.value = false }
}

const hasMore = computed(() => items.value.length < total.value)

/** 加载下一页并追加到当前列表 */
const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  const next = page.value + 1
  try {
    const res = await axios.get('/api/feeds/items', {
      params: { feedId: activeFeedId.value, filter: filter.value, page: next, size: PAGE_SIZE },
    })
    if (res.data.code === 200) {
      const list = res.data.data?.items || []
      items.value = [...items.value, ...list]
      total.value = res.data.data?.total ?? total.value
      page.value = next
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载更多失败')
  } finally { loadingMore.value = false }
}

const reload = () => { fetchFeeds(); fetchItems() }
watch(() => props.refresh, reload)
onMounted(async () => { fetchFeeds(); fetchItems(); dirStore.fetchDirectories(1) })

const selectFeed = (id: number | null) => { activeFeedId.value = id; page.value = 1; selected.value = null; reading.value = null; fetchItems() }
const setFilter = (f: 'all' | 'unread' | 'starred') => { filter.value = f; page.value = 1; fetchItems() }

/* ─── 添加订阅源 ─── */
const addShow = ref(false)
const addUrl = ref('')
const adding = ref(false)
const submitAdd = async () => {
  if (!addUrl.value.trim()) { message.warning('请输入订阅地址'); return }
  adding.value = true
  try {
    const res = await axios.post('/api/feeds', { url: addUrl.value.trim() })
    if (res.data.code === 200) {
      message.success('订阅成功')
      addShow.value = false
      addUrl.value = ''
      fetchFeeds()
      // 跳到新源
      if (res.data.data?.id) { activeFeedId.value = res.data.data.id; fetchItems() }
    } else {
      message.error(res.data.message || '订阅失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '订阅失败')
  } finally { adding.value = false }
}

/* ─── 删除订阅源 ─── */
const removeFeed = async (f: Feed) => {
  try {
    const res = await axios.delete(`/api/feeds/${f.id}`)
    if (res.data.code === 200) {
      message.success('已删除订阅源')
      if (activeFeedId.value === f.id) { activeFeedId.value = null; fetchItems() }
      fetchFeeds()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

/* ─── 刷新 / 全部已读 ─── */
const refreshingId = ref<number | null>(null)
const refreshFeed = async (f: Feed) => {
  refreshingId.value = f.id
  try {
    const res = await axios.post(`/api/feeds/${f.id}/fetch`)
    if (res.data.code === 200) {
      message.success(`刷新完成，新增 ${res.data.data?.newItems ?? 0} 条`)
      fetchFeeds()
      fetchItems()
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '刷新失败')
  } finally { refreshingId.value = null }
}

const markAllRead = async () => {
  try {
    const res = await axios.put('/api/feeds/read-all', { feedId: activeFeedId.value })
    if (res.data.code === 200) {
      message.success(`已将 ${res.data.data?.marked ?? 0} 条标记为已读`)
      fetchFeeds()
      fetchItems()
      if (reading.value) reading.value.readFlag = 1
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

/* ─── 条目操作 ─── */
const openItem = async (item: FeedItemRow) => {
  selected.value = item
  reading.value = item
  if (item.readFlag !== 1) {
    item.readFlag = 1
    try {
      await axios.put(`/api/feeds/items/${item.id}/read`)
      // 更新源未读角标
      const f = feeds.value.find(x => x.id === item.feedId)
      if (f && f.unread > 0) f.unread--
    } catch { /* 静默 */ }
  }
}

const handleItemKeydown = (e: KeyboardEvent, item: FeedItemRow) => {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    openItem(item)
  }
}

const toggleStar = async (item: FeedItemRow) => {
  item.starred = item.starred === 1 ? 0 : 1
  try {
    await axios.put(`/api/feeds/items/${item.id}/star`)
  } catch (e: any) {
    item.starred = item.starred === 1 ? 0 : 1
    message.error(e.response?.data?.message || '操作失败')
  }
}

/* ─── 收藏到收藏夹 ─── */
const bmShow = ref(false)
const bmItem = ref<FeedItemRow | null>(null)
const bmDirectoryId = ref<number | null>(null)
const bmSaving = ref(false)

const flatDirs = computed(() => {
  const walk = (list: any[], depth: number): any[] => {
    const out: any[] = []
    for (const d of list) {
      if (d.type !== undefined && d.type !== 1) continue
      out.push({ label: '　'.repeat(depth) + d.name, value: d.id })
      if (d.children?.length) out.push(...walk(d.children, depth + 1))
    }
    return out
  }
  return walk(dirStore.directories, 0)
})

const openToBookmark = (item: FeedItemRow) => {
  bmItem.value = item
  bmDirectoryId.value = flatDirs.value[0]?.value ?? null
  bmShow.value = true
}
const submitBookmark = async () => {
  if (!bmItem.value) return
  if (!bmDirectoryId.value) { message.warning('请选择目标目录'); return }
  bmSaving.value = true
  try {
    const res = await axios.post(`/api/feeds/items/${bmItem.value.id}/to-bookmark`, { directoryId: bmDirectoryId.value })
    if (res.data.code === 200) {
      message.success('已收藏到收藏夹')
      bmShow.value = false
    } else {
      message.error(res.data.message || '收藏失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '收藏失败')
  } finally { bmSaving.value = false }
}

const unreadBadge = (f: Feed) => f.unread > 0 ? f.unread : undefined

/* 阅读窗格：内容已含 HTML；无全文时回退摘要 */
const readingHtml = computed(() => {
  if (!reading.value) return ''
  return reading.value.content || reading.value.summary || ''
})
const hasFullContent = computed(() => !!reading.value?.content)

const totalUnread = computed(() => feeds.value.reduce((s, f) => s + (f.unread || 0), 0))
</script>

<template>
  <div class="feeds-wrap">
    <div class="feeds-toolbar">
      <div class="toolbar-left">
        <NTag v-if="activeFeedId" size="small" :bordered="false" class="active-feed-tag">
          <NIcon :component="Rss" size="13" /> {{ feeds.find(f => f.id === activeFeedId)?.title || '订阅源' }}
        </NTag>
        <span v-else class="toolbar-title">全部订阅</span>
        <span class="toolbar-sub">{{ totalUnread }} 篇未读</span>
      </div>
      <div class="toolbar-right">
        <NButton size="small" secondary @click="addShow = true">
          <template #icon><NIcon :component="Plus" size="14" /></template>
          添加订阅
        </NButton>
        <NButton size="small" quaternary :disabled="!totalUnread" @click="markAllRead">
          <template #icon><NIcon :component="CheckCheck" size="14" /></template>
          全部已读
        </NButton>
      </div>
    </div>

    <div class="feeds-main">
      <!-- 左：订阅源 -->
      <div class="feed-col">
        <div class="col-head">
          <span class="col-title">订阅源</span>
          <NButton quaternary circle size="tiny" title="刷新订阅源列表" @click="fetchFeeds">
            <template #icon><NIcon :component="RefreshCw" size="13" /></template>
          </NButton>
        </div>
        <div class="feed-filters">
          <div
            class="feed-filter-item"
            :class="{ active: activeFeedId === null && filter === 'all' }"
            @click="activeFeedId = null; setFilter('all')"
          >
            <NIcon :component="Rss" size="14" class="ff-ic" /> 全部
          </div>
          <div
            class="feed-filter-item"
            :class="{ active: filter === 'unread' }"
            @click="setFilter('unread')"
          >
            <NIcon :component="Clock" size="14" class="ff-ic" /> 未读
            <span v-if="totalUnread" class="ff-badge">{{ totalUnread }}</span>
          </div>
          <div
            class="feed-filter-item"
            :class="{ active: filter === 'starred' }"
            @click="setFilter('starred')"
          >
            <NIcon :component="Star" size="14" class="ff-ic" /> 星标
          </div>
        </div>
        <NScrollbar class="feed-scroll">
          <JSkeletonList v-if="loadingFeeds && !feeds.length" :count="4" />
          <JErrorState
            v-else-if="feedsError"
            message="订阅源加载失败"
            hint="请检查网络后重试"
            class="col-state"
            @retry="fetchFeeds"
          />
          <JEmptyState
            v-else-if="!feeds.length"
            message="还没有订阅源"
            hint="添加一个 RSS / Atom 地址开始订阅"
            class="col-state"
          />
          <div
            v-for="f in feeds" :key="f.id"
            class="feed-row"
            :class="{ active: activeFeedId === f.id }"
            role="button"
            tabindex="0"
            :aria-label="`订阅源 ${f.title || f.url}`"
            @click="selectFeed(f.id)"
            @keydown.enter.prevent="selectFeed(f.id)"
            @keydown.space.prevent="selectFeed(f.id)"
          >
            <NBadge :value="unreadBadge(f)" :max="99" :show-zero="false" class="feed-badge-wrap">
              <div class="feed-row-icon">
                <img v-if="f.icon" :src="f.icon" alt="" class="feed-favicon" loading="lazy" decoding="async" @error="($event.target as HTMLImageElement).style.display = 'none'" />
                <NIcon v-else :component="Rss" size="15" class="feed-fallback-ic" />
              </div>
            </NBadge>
            <div class="feed-row-main">
              <div class="feed-row-title" :title="f.title">{{ f.title || f.url }}</div>
              <div class="feed-row-url">{{ f.url.replace(/^https?:\/\//, '').slice(0, 40) }}</div>
            </div>
            <div class="feed-row-actions" @click.stop>
              <NButton quaternary circle size="tiny" title="刷新" :loading="refreshingId === f.id" @click="refreshFeed(f)">
                <template #icon><NIcon :component="RefreshCw" size="12" /></template>
              </NButton>
              <NButton quaternary circle size="tiny" type="error" title="删除" @click="removeFeed(f)">
                <template #icon><NIcon :component="Trash2" size="12" /></template>
              </NButton>
            </div>
          </div>
        </NScrollbar>
      </div>

      <!-- 中：条目列表 -->
      <div class="item-col">
        <div class="col-head">
          <span class="col-title">{{ filter === 'starred' ? '星标' : filter === 'unread' ? '未读' : '全部条目' }}（{{ total }}）</span>
        </div>
        <NScrollbar class="item-scroll">
          <JSkeletonList v-if="loadingItems" :count="5" />
          <JErrorState
            v-else-if="itemsError"
            message="条目加载失败"
            hint="请检查网络后重试"
            class="col-state"
            @retry="fetchItems"
          />
          <JEmptyState v-else-if="!items.length" message="暂无条目" hint="换个筛选或刷新试试" class="col-state" />
          <div
            v-for="it in items" :key="it.id"
            class="item-row"
            :class="{ unread: it.readFlag !== 1, selected: selected?.id === it.id }"
            role="button"
            tabindex="0"
            :aria-label="it.title || '订阅条目'"
            @click="openItem(it)"
            @keydown="handleItemKeydown($event, it)"
          >
            <div class="item-row-title" :title="it.title">
              <span v-if="it.readFlag !== 1" class="unread-dot" />
              {{ it.title || '（无标题）' }}
            </div>
            <div v-if="it.summary" class="item-row-summary">{{ it.summary.replace(/<[^>]+>/g, '').slice(0, 80) }}</div>
            <div class="item-row-foot">
              <span class="item-time">{{ it.publishedAt ? formatRelativeTime(it.publishedAt) : '' }}</span>
              <span v-if="it.author" class="item-author">{{ it.author }}</span>
              <NButton quaternary circle size="tiny" class="item-star" :class="{ starred: it.starred === 1 }" @click.stop="toggleStar(it)">
                <template #icon><NIcon :component="Star" size="13" /></template>
              </NButton>
            </div>
          </div>
          <div v-if="hasMore" class="load-more-wrap">
            <NButton size="small" quaternary :loading="loadingMore" @click="loadMore">
              {{ loadingMore ? '加载中…' : `加载更多（${items.length}/${total}）` }}
            </NButton>
          </div>
        </NScrollbar>
      </div>

      <!-- 右：阅读窗格 -->
      <div class="read-col">
        <template v-if="reading">
          <div class="read-head">
            <h2 class="read-title">{{ reading.title }}</h2>
            <div class="read-meta">
              <span v-if="reading.author" class="read-author">{{ reading.author }}</span>
              <span v-if="reading.publishedAt" class="read-time">{{ formatRelativeTime(reading.publishedAt) }}</span>
            </div>
            <div class="read-actions">
              <NButton size="tiny" secondary @click="toggleStar(reading)">
                <template #icon><NIcon :component="Star" size="13" /></template>
                {{ reading.starred === 1 ? '取消星标' : '星标' }}
              </NButton>
              <NButton size="tiny" type="primary" secondary @click="openToBookmark(reading)">
                <template #icon><NIcon :component="Bookmark" size="13" /></template>
                收藏到收藏夹
              </NButton>
              <NButton v-if="reading.link" size="tiny" quaternary tag="a" :href="reading.link" target="_blank" rel="noopener">
                <template #icon><NIcon :component="ExternalLink" size="13" /></template>
                原文
              </NButton>
            </div>
          </div>
          <NScrollbar class="read-scroll">
            <div class="read-content">
              <div v-if="hasFullContent" class="rich-content" v-html="readingHtml" />
              <div v-else-if="reading.summary" class="rich-content" v-html="readingHtml" />
              <JEmptyState v-else message="该条目没有正文内容" hint="可打开原文阅读" />
            </div>
          </NScrollbar>
        </template>
        <div v-else class="read-empty">
          <NIcon :component="Rss" size="40" class="read-empty-ic" />
          <p>选择左侧条目开始阅读</p>
        </div>
      </div>
    </div>

    <!-- 添加订阅源 -->
    <NModal v-model:show="addShow" preset="card" title="添加订阅源" style="width: 420px" :bordered="false">
      <div class="add-form">
        <NInput v-model:value="addUrl" placeholder="https://example.com/feed.xml" autofocus @keyup.enter="submitAdd" />
        <p class="add-hint">支持 RSS 2.0 / Atom；添加成功后会立即拉取最近条目。</p>
        <NButton type="primary" block :loading="adding" @click="submitAdd">订阅</NButton>
      </div>
    </NModal>

    <!-- 收藏到收藏夹 -->
    <NModal v-model:show="bmShow" preset="card" title="收藏到收藏夹" style="width: 380px" :bordered="false">
      <div class="add-form">
        <p class="bm-title">{{ bmItem?.title }}</p>
        <NSelect v-model:value="bmDirectoryId" :options="flatDirs" placeholder="选择目录" filterable />
        <NButton type="primary" block :loading="bmSaving" @click="submitBookmark">收藏</NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.feeds-wrap {
  display: flex; flex-direction: column; height: 100%; gap: 10px;
  padding-bottom: env(safe-area-inset-bottom);
}
.feeds-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 4px 4px 0;
  flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.toolbar-title { font-size: 16px; font-weight: 800; color: var(--text-1); }
.toolbar-sub { font-size: var(--fs-xs); color: var(--text-3); }
.toolbar-right { display: flex; gap: 8px; flex-shrink: 0; }
.active-feed-tag { font-weight: 600; }

.feeds-main { display: flex; flex: 1; min-height: 0; gap: 10px; }
.feed-col, .item-col, .read-col {
  display: flex; flex-direction: column; min-width: 0;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.feed-col { width: 220px; flex-shrink: 0; }
.item-col { width: 300px; flex-shrink: 0; }
.read-col { flex: 1; }

.col-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.col-title { font-size: var(--fs-sm); font-weight: 700; color: var(--text-2); }

.feed-filters { display: flex; flex-direction: column; padding: 6px; gap: 2px; border-bottom: 1px solid var(--glass-border); flex-shrink: 0; }
.feed-filter-item {
  display: flex; align-items: center; gap: 6px;
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  color: var(--text-2);
  cursor: pointer;
}
.feed-filter-item:hover { background: var(--hover-bg); }
.feed-filter-item.active { background: var(--brand-soft); color: var(--brand); font-weight: 600; }
.ff-ic { flex-shrink: 0; }
.ff-badge {
  margin-left: auto;
  background: var(--brand);
  color: #fff;
  font-size: 10px;
  border-radius: var(--radius-pill);
  padding: 0 6px;
  line-height: 16px;
}

.feed-scroll, .item-scroll, .read-scroll { flex: 1; min-height: 0; }
.col-state { display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--text-3); font-size: var(--fs-sm); padding: 30px 0; }
.col-empty { padding: 30px 0; }
.col-state {
  min-height: 160px;
  padding: 24px 12px;
  border-radius: var(--radius-sm);
}

.feed-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--glass-border);
}
.feed-row:hover { background: var(--hover-bg); }
.feed-row.active { background: var(--brand-soft); }
.feed-badge-wrap { flex-shrink: 0; }
.feed-row-icon {
  width: 28px; height: 28px;
  border-radius: 8px;
  background: var(--glass-bg-trans);
  display: flex; align-items: center; justify-content: center;
}
.feed-favicon { width: 16px; height: 16px; border-radius: 4px; object-fit: cover; }
.feed-fallback-ic { color: var(--brand); }
.feed-row-main { flex: 1; min-width: 0; }
.feed-row-title {
  font-size: var(--fs-sm); font-weight: 600; color: var(--text-1);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.feed-row-url { font-size: 10px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.feed-row-actions { display: flex; gap: 2px; opacity: 0; flex-shrink: 0; }
.feed-row:hover .feed-row-actions { opacity: 1; }

.item-row { padding: 10px 12px; cursor: pointer; border-bottom: 1px solid var(--glass-border); }
.item-row:hover { background: var(--hover-bg); }
.item-row.selected { background: var(--brand-soft); }
.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 8px;
}
.item-row.unread .item-row-title { font-weight: 700; color: var(--text-1); }
.item-row-title {
  display: flex; align-items: flex-start; gap: 6px;
  font-size: var(--fs-sm); color: var(--text-2);
  line-height: 1.45;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.unread-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--brand); flex-shrink: 0; margin-top: 6px; }
.item-row-summary {
  font-size: var(--fs-xs); color: var(--text-3);
  margin-top: 4px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.item-row-foot { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.item-time { font-size: 10px; color: var(--text-3); }
.item-author { font-size: 10px; color: var(--text-3); max-width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-star { margin-left: auto; color: var(--text-3); }
.item-star.starred { color: var(--warning-text); }

.read-head {
  padding: 16px 20px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.read-title { font-size: 20px; font-weight: 800; color: var(--text-1); margin: 0 0 8px; line-height: 1.4; }
.read-meta { display: flex; gap: 12px; font-size: var(--fs-xs); color: var(--text-3); margin-bottom: 12px; }
.read-actions { display: flex; gap: 8px; flex-wrap: wrap; }

.read-scroll { }
.read-content { max-width: 760px; margin: 0 auto; padding: 20px 28px calc(48px + env(safe-area-inset-bottom)); }
.rich-content { font-size: 15px; line-height: 1.85; color: var(--text-1); word-break: break-word; }
.rich-content :deep(h1), .rich-content :deep(h2), .rich-content :deep(h3), .rich-content :deep(h4) {
  margin: 1.3em 0 0.5em; line-height: 1.4; font-weight: 700; color: var(--text-1);
}
.rich-content :deep(h1) { font-size: 1.5em; }
.rich-content :deep(h2) { font-size: 1.3em; }
.rich-content :deep(p) { margin: 0.8em 0; }
.rich-content :deep(img) { max-width: 100%; height: auto; border-radius: 8px; margin: 10px 0; }
.rich-content :deep(a) { color: var(--brand); text-decoration: none; }
.rich-content :deep(a):hover { text-decoration: underline; }
.rich-content :deep(blockquote) {
  margin: 10px 0; padding: 8px 14px;
  border-left: 3px solid var(--brand);
  color: var(--text-2); background: var(--glass-chip-bg);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.rich-content :deep(pre) {
  background: var(--glass-chip-bg); border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-sm); padding: 12px; overflow-x: auto; font-size: 13px;
}
.rich-content :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: var(--glass-chip-bg); border-radius: 4px; padding: 1px 5px; font-size: 0.92em;
}
.rich-content :deep(pre code) { background: none; padding: 0; }
.rich-content :deep(ul), .rich-content :deep(ol) { margin: 0.8em 0; padding-left: 1.6em; }
.rich-content :deep(li) { margin: 0.3em 0; }
.rich-content :deep(table) { border-collapse: collapse; margin: 12px 0; width: 100%; font-size: 14px; }
.rich-content :deep(th), .rich-content :deep(td) { border: 1px solid var(--glass-chip-border); padding: 7px 10px; text-align: left; }
.rich-content :deep(th) { background: var(--glass-chip-bg); font-weight: 600; }

.read-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  color: var(--text-3); font-size: var(--fs-sm);
}
.read-empty-ic { color: var(--text-3); opacity: .5; }

.add-form { display: flex; flex-direction: column; gap: 10px; }
.add-hint { font-size: var(--fs-xs); color: var(--text-3); margin: 0; }
.bm-title { font-size: var(--fs-sm); font-weight: 600; color: var(--text-1); margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 900px) {
  .feeds-main { flex-direction: column; }
  .feed-col, .item-col { width: 100%; max-height: 180px; }
  .read-col { min-height: 320px; }
  /* 触屏没有 hover，操作入口常显 */
  .feed-row-actions { opacity: 1; }
}
</style>
