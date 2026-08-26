<script setup lang="ts">
/**
 * CollectionCard.vue — 卡片网格中的单张收藏卡（氛围升级版）
 * 顶部渐变装饰条 + favicon 发光底盒 + 链接色粉系
 * hover: 卡片抬升 + 品牌粉阴影
 */
import { h, ref } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag, useMessage, NCheckbox, NProgress } from 'naive-ui'
import { Pencil, Trash2, EllipsisVertical, ExternalLink, FolderInput, Link2, BookOpen, BookmarkPlus, Check, Archive } from 'lucide-vue-next'
import { openMenu } from '../../../shared/composables/useContextMenu'
import MoveItemModal from './MoveItemModal.vue'
import ShareModal from './ShareModal.vue'
import SnapshotModal from './SnapshotModal.vue'
import axios from 'axios'
import type { BookmarkItem } from './CollectionRow.vue'
import { useRecentItems } from '../../../shared/composables/useRecentItems'

const props = defineProps<{
  bookmark: BookmarkItem
  batchMode?: boolean
  selected?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
  read: [bookmark: BookmarkItem]
  'toggle-select': []
}>()

const message = useMessage()
const { record: recordRecentItem } = useRecentItems()
const imgError = ref(false)
const showMoveModal = ref(false)
const showShare = ref(false)
const showSnapshot = ref(false)

/** 归档快照：成功后标记 hasSnapshot（前端乐观更新） */
const capturing = ref(false)
const captureSnapshot = async () => {
  capturing.value = true
  try {
    const res = await axios.post(`/api/snapshots/${props.bookmark.id}`, {}, { timeout: 30000 })
    if (res.data.code === 200) {
      ;(props.bookmark as any).hasSnapshot = true
      message.success('快照已归档')
    } else {
      message.error(res.data.message || '归档失败')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || e.message || '归档失败')
  } finally { capturing.value = false }
}

const getDomain = (url: string) => {
  try { return new URL(url).hostname } catch { return url }
}

const handleOpen = () => {
  window.open(props.bookmark.url, '_blank')
  // 打开收藏 → 记入「最近打开」
  recordRecentItem({ kind: 'bookmark', id: props.bookmark.id, title: props.bookmark.title || props.bookmark.url, url: props.bookmark.url })
}
const onRootClick = () => {
  if (props.batchMode) emit('toggle-select')
  else handleOpen()
}


const handleDelete = async () => {
  const id = props.bookmark.id
  try {
    await axios.delete(`/api/bookmarks/${id}`)
    message.success('', {
      duration: 6000,
      render: () => h('div', { style: 'display:flex;align-items:center;gap:12px;' }, [
        h('span', '已移入回收站'),
        h('a', {
          style: 'cursor:pointer;color:var(--brand);font-weight:600;',
          onClick: async () => {
            try {
              await axios.post('/api/recycle/restore', { type: 'bookmark', id })
              message.success('已恢复')
              emit('refresh')
            } catch {
              message.error('恢复失败')
            }
          },
        }, '撤销'),
      ]),
    })
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

const isReadLater = () => props.bookmark.readLater === 1

const toggleReadLater = async () => {
  try {
    const next = !isReadLater()
    await axios.put(`/api/bookmarks/${props.bookmark.id}/read-later`, { readLater: next })
    props.bookmark.readLater = next ? 1 : 0
    if (!next) props.bookmark.readProgress = 0
    message.success(next ? '已加入稍后读' : '已移出稍后读')
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '操作失败')
  }
}

const dropdownOptions = [
  { label: '打开', key: 'open', icon: () => h(NIcon, null, { default: () => h(ExternalLink) }) },
  { label: '阅读模式', key: 'read', icon: () => h(NIcon, null, { default: () => h(BookOpen) }) },
  {
    label: isReadLater() ? '移出稍后读' : '稍后读',
    key: 'read-later',
    icon: () => h(NIcon, null, { default: () => h(isReadLater() ? Check : BookmarkPlus) }),
  },
  { label: (props.bookmark as any).hasSnapshot ? '查看快照' : '归档快照', key: 'snapshot', icon: () => h(NIcon, null, { default: () => h(Archive) }) },
  { label: '移动到…', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
  { label: '分享', key: 'share', icon: () => h(NIcon, null, { default: () => h(Link2) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'open') handleOpen()
  else if (key === 'read') emit('read', props.bookmark)
  else if (key === 'read-later') toggleReadLater()
  else if (key === 'snapshot') {
    if ((props.bookmark as any).hasSnapshot) showSnapshot.value = true
    else captureSnapshot()
  }
  else if (key === 'move') showMoveModal.value = true
  else if (key === 'share') showShare.value = true
  else if (key === 'edit') emit('edit', props.bookmark)
  else if (key === 'delete') handleDelete()
}
</script>

<template>
  <div
    class="bookmark-card jnclub-bouncy"
    role="link"
    tabindex="0"
    @click="onRootClick"
    @keydown.enter.prevent="onRootClick"
    @keydown.space.prevent="onRootClick"
    @contextmenu.prevent="openMenu($event, dropdownOptions, handleDropdown)"
  >
      <div v-if="props.batchMode" class="batch-check" @click.stop="emit('toggle-select')">
        <NCheckbox :checked="props.selected" @update:checked="emit('toggle-select')" size="small" />
      </div>
    <!-- 顶部渐变装饰条 -->
    <div class="card-top-bar"></div>

    <div class="card-body">
      <div class="card-head">
        <!-- favicon 发光底盒 -->
        <div class="favicon-box">
          <img
            v-if="bookmark.icon && !imgError"
            :src="bookmark.icon"
            :alt="bookmark.title"
            class="favicon-img"
            loading="lazy"
            decoding="async"
            @error="imgError = true"
          />
          <div v-else class="favicon-fallback">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" stroke-width="1.5">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
          </div>
        </div>

        <!-- 操作菜单 hover 出现 -->
        <div class="card-actions" @click.stop>
          <NDropdown :options="dropdownOptions" @select="handleDropdown" placement="bottom-end">
            <NButton quaternary circle size="tiny" class="more-btn">
              <template #icon>
                <NIcon :component="EllipsisVertical" size="15" />
              </template>
            </NButton>
          </NDropdown>
        </div>
      </div>

      <!-- 标题 -->
      <div class="card-title">
        <NEllipsis :tooltip="{ width: 360 }">
          <span class="title-text">{{ bookmark.title || bookmark.url }}</span>
        </NEllipsis>
      </div>

      <!-- 标签 -->
      <div v-if="bookmark.tags?.length" class="card-tags">
        <NTag v-for="t in bookmark.tags" :key="t" size="tiny" round :bordered="false" class="card-tag">
          {{ t }}
        </NTag>
      </div>

      <!-- 链接（底部唯一域名展示，去冗余）
           注意：卡片列表不使用 JMagnet，避免每张卡挂一个全局 mousemove + getBoundingClientRect，
           这是 Windows 高 DPI/高刷下“指针不跟手”的主要来源之一。 -->
      <a :href="bookmark.url" target="_blank" class="card-link jnclub-bouncy" @click.stop>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
        {{ getDomain(bookmark.url) }}
      </a>

      <!-- 稍后读 + 阅读进度 + 快照标记 -->
      <div v-if="isReadLater() || (bookmark.readProgress ?? 0) > 0 || (bookmark as any).hasSnapshot" class="card-readlater">
        <div class="rl-badges">
          <span v-if="isReadLater()" class="rl-badge">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21l-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z" /></svg>
            稍后读
          </span>
          <span v-if="(bookmark as any).hasSnapshot" class="rl-badge snap-badge" @click.stop="showSnapshot = true">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /><polyline points="3.27 6.96 12 12.01 20.73 6.96" /><line x1="12" y1="22.08" x2="12" y2="12" /></svg>
            快照
          </span>
        </div>
        <NProgress
          v-if="(bookmark.readProgress ?? 0) > 0 && (bookmark.readProgress ?? 0) < 100"
          :percentage="bookmark.readProgress ?? 0"
          :show-indicator="false"
          :height="3"
          color="var(--module-bookmark)"
          rail-color="var(--border)"
          class="rl-progress"
        />
      </div>
    </div>

    <!-- 移动到目录弹窗 -->
    <MoveItemModal
      v-model:show="showMoveModal"
      :item-type="1"
      :targets="[{ id: bookmark.id, name: bookmark.title }]"
      :current-directory-id="bookmark.directoryId ?? null"
      @refresh="emit('refresh')"
    />

    <ShareModal
      :show="showShare"
      ref-type="bookmark"
      :ref-id="bookmark.id"
      :name="bookmark.title || bookmark.url"
      @update:show="(v: boolean) => (showShare = v)"
    />

    <!-- 网页快照查看 -->
    <SnapshotModal
      v-model:show="showSnapshot"
      :bookmark-id="bookmark.id"
      :url="bookmark.url"
      @changed="emit('refresh')"
    />
  </div>
</template>

<style scoped>
.bookmark-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  overflow: hidden;
  box-shadow: var(--shadow-1);
  transition: transform var(--dur) var(--ease-bouncy), border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}

.bookmark-card:hover {
  transform: translateY(-2px);
  border-color: var(--brand);
  box-shadow: var(--shadow-card-hover);
}

.bookmark-card:active {
  transform: translateY(0);
}

/* === 卡片主体 === */
.card-body {
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

/* === 头部 === */
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

/* favicon 底盒 */
.favicon-box {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--pink-cherry);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--glow-icon);
  flex-shrink: 0;
  transition: box-shadow var(--dur) var(--ease), transform var(--dur) var(--ease-bouncy);
}
.bookmark-card:hover .favicon-box {
  box-shadow: 0 0 0 6px var(--focus-ring), var(--glow-icon);
  transform: scale(1.06);
}

.favicon-img {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-xs);
  object-fit: cover;
  background: #fff; /* 透明/异形 favicon 垫白底，避免透出粉色底盒 */
}

.favicon-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* === 标题 === */
.card-title {
  min-width: 0;
}

.title-text {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.4;
}

/* === 标签 === */
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.card-tag {
  background: var(--brand-soft) !important;
  color: var(--brand) !important;
}

/* === 链接（底部唯一域名展示，去冗余） === */
.card-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-sm);
  color: var(--brand);
  text-decoration: none;
  margin-top: auto;
  width: fit-content;
}

.card-link:hover {
  color: var(--brand-hover);
  text-decoration: underline;
}

/* === 稍后读 === */
.card-readlater {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 2px;
}
.rl-badges { display: flex; gap: 6px; flex-wrap: wrap; }
.rl-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: fit-content;
  font-size: 11px;
  color: var(--module-bookmark);
  background: color-mix(in srgb, var(--module-bookmark) 12%, transparent);
  border-radius: var(--radius-pill);
  padding: 2px 8px;
}
.snap-badge { color: var(--success); background: color-mix(in srgb, var(--success) 12%, transparent); cursor: pointer; }
.snap-badge:hover { filter: brightness(1.1); }
.rl-progress { width: 100%; }

/* === 操作按钮 hover 显示 === */
.card-actions {
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}

.bookmark-card:hover .card-actions,
.bookmark-card:focus-within .card-actions {
  opacity: 1;
}

@media (hover: none) {
  .card-actions { opacity: 1; }
}

.more-btn {
  color: var(--text-3);
}

.more-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}
</style>
<style scoped>
.batch-check {
  position: absolute;
  top: 10px; left: 10px;
  z-index: 5;
  background: rgba(0,0,0,0.35);
  border-radius: 6px;
  padding: 2px;
}
</style>
