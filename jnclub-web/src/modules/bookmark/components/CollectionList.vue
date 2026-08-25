<script setup lang="ts">
/**
 * CollectionList.vue — 极简列表视图
 * 包裹多行 CollectionRow，stagger 渐入
 */
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVirtualList } from '@vueuse/core'
import { NSpin } from 'naive-ui'
import CollectionRow from './CollectionRow.vue'
import type { BookmarkItem } from './CollectionRow.vue'
import { useDraggableSort } from '../composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'

const props = defineProps<{
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

/* 大列表启用虚拟滚动；虚拟模式下拖拽排序不可用（避免只排序已渲染片段） */
const isVirtual = computed(() => props.bookmarks.length > 80)
const virtual = useVirtualList(computed(() => props.bookmarks), { itemHeight: 64, overscan: 8 })
const virtualItems = computed(() => Array.isArray(virtual.list.value) ? virtual.list.value : [])
const dragDisabled = computed(() => isVirtual.value)
const { init: initSort, destroy: destroySort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
}, dragDisabled)

onMounted(() => {
  requestAnimationFrame(() => {
    visible.value = true
  })
  if (!isVirtual.value) initSort()
})

watch(isVirtual, async (v) => {
  if (v) {
    destroySort()
  } else {
    await nextTick()
    initSort()
  }
})
</script>

<template>
  <div class="collection-list">
    <NSpin :show="loading">
      <div v-if="isVirtual" v-bind="virtual.containerProps" class="virtual-scroll">
        <div v-bind="virtual.wrapperProps">
          <div
            v-for="item in virtualItems"
            :key="item.data.id"
            :data-id="item.data.id"
            class="list-item-wrap virtual-item"
            :style="{ '--i': item.index % 20 }"
          >
            <CollectionRow :bookmark="item.data" @refresh="emit('refresh')" @edit="emit('edit', $event)" @read="emit('read', $event)" :batch-mode="batchMode" :selected="selectedIds?.includes(item.data.id)" @toggle-select="emit('toggle-select', item.data.id)" />
          </div>
        </div>
      </div>
      <div v-else ref="listRef" :class="['list-items', { visible }]">
        <div
          v-for="(bk, i) in props.bookmarks"
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

.virtual-scroll {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  padding-right: 4px;
}
.virtual-item {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
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
