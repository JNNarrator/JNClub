<script setup lang="ts">
/**
 * CollectionCard.vue — 卡片网格中的单张收藏卡（氛围升级版）
 * 顶部渐变装饰条 + favicon 发光底盒 + 链接色粉系
 * hover: 卡片抬升 + 品牌粉阴影
 */
import { h, ref } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag, useMessage, NCheckbox } from 'naive-ui'
import { Pencil, Trash2, EllipsisVertical, ExternalLink, FolderInput, Link2, BookOpen } from 'lucide-vue-next'
import { openMenu, openMenuAt } from '../../../shared/composables/useContextMenu'
import { useLongPress } from '../../../shared/composables/useLongPress'
import MoveItemModal from './MoveItemModal.vue'
import ShareModal from './ShareModal.vue'
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
const longPress = useLongPress()
const { record: recordRecentItem } = useRecentItems()
const imgError = ref(false)
const showMoveModal = ref(false)
const showShare = ref(false)

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

const dropdownOptions = [
  { label: '打开', key: 'open', icon: () => h(NIcon, null, { default: () => h(ExternalLink) }) },
  { label: '阅读模式', key: 'read', icon: () => h(NIcon, null, { default: () => h(BookOpen) }) },
  { label: '移动到…', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
  { label: '分享', key: 'share', icon: () => h(NIcon, null, { default: () => h(Link2) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'open') handleOpen()
  else if (key === 'read') emit('read', props.bookmark)
  else if (key === 'move') showMoveModal.value = true
  else if (key === 'share') showShare.value = true
  else if (key === 'edit') emit('edit', props.bookmark)
  else if (key === 'delete') handleDelete()
}
</script>

<template>
  <div
    :class="['bookmark-card jnclub-bouncy jnclub-longpress', { 'is-selected': props.selected }]"
    role="link"
    tabindex="0"
    @click="onRootClick"
    @keydown.enter.prevent="onRootClick"
    @keydown.space.prevent="onRootClick"
    @contextmenu.prevent="openMenu($event, dropdownOptions, handleDropdown)"
    v-on="longPress((ev) => openMenuAt(ev.clientX, ev.clientY, dropdownOptions, handleDropdown))"
  >
      <div v-if="props.batchMode" class="batch-check" @click.stop="emit('toggle-select')">
        <NCheckbox :checked="props.selected" @update:checked="emit('toggle-select')" size="small" />
      </div>
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
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--module-bookmark-icon)" stroke-width="1.5">
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

      <!-- 标签
           始终渲染（即使无标签）—— 标签区是网格卡片唯一的高度变量，
           空卡片少这一行会让同屏卡片高度参差 24px，进而让虚拟滚动的
           行高无法用常量表达（技术债①）。标签区固定单行高度，卡片定高。 -->
      <div class="card-tags">
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
  </div>
</template>

<style scoped>
.bookmark-card {
  position: relative;
  display: flex;
  flex-direction: column;
  /* L1 结构面：--surface-1 填充 + --surface-border 描边（不用控件描边 --border，
     后者语义是输入框/表格线，混用会牵动表单观感） */
  background: var(--surface-1);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  overflow: hidden;
  box-shadow: var(--shadow-1);
  transition: transform var(--dur) var(--ease-bouncy), border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}

.bookmark-card:hover {
  /* hover 统一到 L2 语言：描边加深一档 + 卡片阴影 + 抬升 1px（原 -2px 偏跳）。
     2026-10 起不再变粉边 —— 粉色配额只留给 .is-selected（选中态）。 */
  transform: translateY(-1px);
  border-color: var(--surface-border-strong);
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
  /* 图标盒 = 模块色 soft 底（12% 混入 L1）+ 模块图标色。
     ⚠ 不能写成 background: var(--module-*-icon)：那会变成大面积模块色填充，
     直接违反 §3.5 的 3% 面积上限。soft 底视为中性面，不占模块色配额。 */
  background: color-mix(in srgb, var(--module-bookmark) 12%, var(--surface-1));
  color: var(--module-bookmark-icon);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: box-shadow var(--dur) var(--ease), transform var(--dur) var(--ease-bouncy);
}
.bookmark-card:hover .favicon-box {
  /* hover 抬升改用中性卡片阴影：原来的 6px 粉色柔光环会盖住 favicon 本身的色彩 */
  box-shadow: var(--shadow-card-hover);
  transform: scale(1.06);
}

.favicon-img {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-xs);
  object-fit: cover;
  background: var(--favicon-scrim); /* 透明/异形 favicon 垫白底，避免透出粉色底盒 */
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
/* 标签区固定单行高度（16px = NTag tiny 高度），这是卡片定高的前提：
   原先 flex-wrap: wrap 会让 2 行标签的卡片高出 44px，网格里同屏卡片高度参差，
   虚拟滚动的 itemHeight 也只能写死一个偏大的估值（见 CollectionGrid.vue 注释）。
   现在不换行、超宽渐隐，卡片高度恒定。 */
.card-tags {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  min-height: 16px;
  overflow: hidden;
  /* 溢出时右缘渐隐，作为「还有更多标签」的信号；未溢出时不影响观感 */
  -webkit-mask-image: linear-gradient(to right, #000 calc(100% - 20px), transparent);
  mask-image: linear-gradient(to right, #000 calc(100% - 20px), transparent);
}
.card-tag {
  flex-shrink: 0;
  /* chip 语言：透明底 + 1px 淡粉描边 + 粉字。
     NTag 的 :bordered="false" 不画边框，故用 inset box-shadow 补 1px 细线，
     避免再加 padding 影响卡片定高。 */
  background: transparent !important;
  color: var(--brand-text) !important;
  box-shadow: inset 0 0 0 1px var(--glass-chip-border);
}

/* === 链接（底部唯一域名展示，去冗余） === */
.card-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-sm);
  /* 域名是元信息，不是链接入口（整卡已可点）→ 降到 --text-3，
     把「粉色 = 可交互指示」的语义收回到真正需要强调的地方。 */
  color: var(--text-3);
  text-decoration: none;
  margin-top: auto;
  width: fit-content;
}

.card-link:hover {
  color: var(--brand-text);
  text-decoration: underline;
}

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
<style scoped></style>
