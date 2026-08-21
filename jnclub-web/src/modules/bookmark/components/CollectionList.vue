<script setup lang="ts">
/**
 * CollectionList.vue — 极简列表视图
 * 包裹多行 CollectionRow，stagger 渐入
 */
import { ref, onMounted } from 'vue'
import { NSpin } from 'naive-ui'
import CollectionRow from './CollectionRow.vue'
import type { BookmarkItem } from './CollectionRow.vue'
import { useDraggableSort } from '../composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'

defineProps<{
  bookmarks: BookmarkItem[]
  loading?: boolean
  batchMode?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
  read: [bookmark: BookmarkItem]
  sort: [orderedIds: number[]]
  'toggle-select': [id: number]
}>()

/* Stagger 渐入：每项延迟递增 */
const visible = ref(false)
const listRef = ref<HTMLElement | null>(null)
const { setDragging } = useItemDragContext()

const handleDragStart = (e: DragEvent, item: BookmarkItem) => {
  setDragging({
    itemId: item.id,
    module: 'bookmarks',
    currentDirectoryId: item.directoryId ?? null,
  })
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    try { e.dataTransfer.setData('text/plain', String(item.id)) } catch { /* 忽略 */ }
  }
}

const handleDragEnd = () => setDragging(null)

onMounted(() => {
  requestAnimationFrame(() => {
    visible.value = true
  })
})

const { init: initSort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
})
onMounted(() => { initSort() })
</script>

<template>
  <div class="collection-list">
    <NSpin :show="loading">
      <div ref="listRef" :class="['list-items', { visible }]">
        <div
          v-for="(bk, i) in bookmarks"
          :key="bk.id"
          :data-id="bk.id"
          class="list-item-wrap"
          :style="{ '--i': i }"
          draggable="true"
          @dragstart="handleDragStart($event, bk)"
          @dragend="handleDragEnd"
        >
          <CollectionRow :bookmark="bk" @refresh="emit('refresh')" @edit="emit('edit', $event)" @read="emit('read', $event)"  :batch-mode="batchMode" :selected="selectedIds?.includes(bk.id)" @toggle-select="emit('toggle-select', bk.id)" />
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.collection-list {
  min-height: 100px;
}

.list-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* Stagger 渐入（全局 jnclub-cardIn，统一节奏） */
.list-item-wrap {
  opacity: 0;
  transform: translateY(10px);
  animation: jnclub-cardIn 0.32s var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * 0.045s);
}

.list-items.visible .list-item-wrap {
  animation-name: jnclub-cardIn;
}

/* 拖拽排序时抑制 stagger 渐入动画重播 */
.list-items.sorting .list-item-wrap {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}
</style>
