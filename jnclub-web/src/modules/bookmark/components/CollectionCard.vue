<script setup lang="ts">
/**
 * CollectionCard.vue — 卡片网格中的单张收藏卡（氛围升级版）
 * 顶部渐变装饰条 + favicon 发光底盒 + 链接色粉系
 * hover: 卡片抬升 + 品牌粉阴影
 */
import { h, ref } from 'vue'
import { NButton, NIcon, NDropdown, NEllipsis, NTag, useMessage } from 'naive-ui'
import { Pencil, Trash2, EllipsisVertical, ExternalLink, FolderInput } from 'lucide-vue-next'
import { openMenu } from '../../../shared/composables/useContextMenu'
import { JMagnet } from '../../../shared/components/animation'
import MoveItemModal from './MoveItemModal.vue'
import axios from 'axios'
import type { BookmarkItem } from './CollectionRow.vue'

const props = defineProps<{
  bookmark: BookmarkItem
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
}>()

const message = useMessage()
const imgError = ref(false)
const showMoveModal = ref(false)

const getDomain = (url: string) => {
  try { return new URL(url).hostname } catch { return url }
}

const handleOpen = () => {
  window.open(props.bookmark.url, '_blank')
}

const handleDelete = async () => {
  try {
    await axios.delete(`/api/bookmarks/${props.bookmark.id}`)
    message.success('删除成功')
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '删除失败')
  }
}

const dropdownOptions = [
  { label: '打开', key: 'open', icon: () => h(NIcon, null, { default: () => h(ExternalLink) }) },
  { label: '移动到…', key: 'move', icon: () => h(NIcon, null, { default: () => h(FolderInput) }) },
  { label: '编辑', key: 'edit', icon: () => h(NIcon, null, { default: () => h(Pencil) }) },
  { label: '删除', key: 'delete', icon: () => h(NIcon, null, { default: () => h(Trash2) }) },
]

const handleDropdown = (key: string) => {
  if (key === 'open') handleOpen()
  else if (key === 'move') showMoveModal.value = true
  else if (key === 'edit') emit('edit', props.bookmark)
  else if (key === 'delete') handleDelete()
}
</script>

<template>
  <div
    class="bookmark-card jnclub-bouncy"
    @click="handleOpen"
    @contextmenu.prevent="openMenu($event, dropdownOptions, handleDropdown)"
  >
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

      <!-- 链接（底部唯一域名展示，去冗余，磁吸点缀） -->
      <JMagnet :magnet-strength="4" :padding="90">
        <a :href="bookmark.url" target="_blank" class="card-link jnclub-bouncy" @click.stop>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
          </svg>
          {{ getDomain(bookmark.url) }}
        </a>
      </JMagnet>
    </div>

    <!-- 移动到目录弹窗 -->
    <MoveItemModal
      v-model:show="showMoveModal"
      :item-type="1"
      :targets="[{ id: bookmark.id, name: bookmark.title }]"
      :current-directory-id="bookmark.directoryId ?? null"
      @refresh="emit('refresh')"
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

/* === 操作按钮 hover 显示 === */
.card-actions {
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}

.bookmark-card:hover .card-actions {
  opacity: 1;
}

.more-btn {
  color: var(--text-3);
}

.more-btn:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}
</style>
