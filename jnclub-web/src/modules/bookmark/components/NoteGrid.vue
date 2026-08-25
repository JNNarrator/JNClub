<script setup lang="ts">
/**
 * NoteGrid.vue — 便签卡片网格
 * grid-template-columns: repeat(auto-fill, minmax(220px,1fr)) + gap
 * stagger 渐入
 */
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVirtualList, useElementSize } from '@vueuse/core'
import { NSpin } from 'naive-ui'
import NoteCard from './NoteCard.vue'
import type { Note } from '../stores/note'
import { useDraggableSort } from '../composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'

const props = defineProps<{
  notes: Note[]
  loading?: boolean
  batchMode?: boolean
  selectedIds?: number[]
}>()

const emit = defineEmits<{
  preview: [note: Note]
  edit: [note: Note]
  delete: [note: Note]
  refresh: []
  sort: [orderedIds: number[]]
  'toggle-select': [id: number]
}>()

const visible = ref(false)
const gridRef = ref<HTMLElement | null>(null)
const { setDragging } = useItemDragContext()

const handleDragStart = (e: DragEvent, item: Note) => {
  setDragging({
    itemId: item.id,
    module: 'notes',
    currentDirectoryId: item.directoryId ?? null,
  })
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    try { e.dataTransfer.setData('text/plain', String(item.id)) } catch { /* 忽略 */ }
  }
}

const handleDragEnd = () => setDragging(null)

/* 大列表网格虚拟滚动：按容器宽度把卡片分组为行，只渲染可视行 */
const rootRef = ref<HTMLElement | null>(null)
const { width: gridWidth } = useElementSize(rootRef)
const columns = computed(() => {
  const w = gridWidth.value
  if (!w) return 3
  return Math.max(1, Math.floor(w / 220))
})
const rows = computed(() => {
  const cols = columns.value
  const result: Note[][] = []
  for (let i = 0; i < props.notes.length; i += cols) {
    result.push(props.notes.slice(i, i + cols))
  }
  return result
})
const isVirtual = computed(() => props.notes.length > 80)
const virtual = useVirtualList(rows, { itemHeight: 220, overscan: 2 })
const virtualRows = computed(() => Array.isArray(virtual.list.value) ? virtual.list.value : [])
const dragDisabled = computed(() => isVirtual.value)
const { init: initSort, destroy: destroySort } = useDraggableSort(gridRef, (ids) => {
  emit('sort', ids.map(Number))
}, dragDisabled)

onMounted(() => {
  requestAnimationFrame(() => { visible.value = true })
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
  <div ref="rootRef" class="note-grid">
    <NSpin :show="loading">
      <div v-if="isVirtual" v-bind="virtual.containerProps" class="virtual-scroll">
        <div v-bind="virtual.wrapperProps">
          <div
            v-for="row in virtualRows"
            :key="row.index"
            class="virtual-row"
            :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }"
          >
            <div
              v-for="note in row.data"
              :key="note.id"
              :data-id="note.id"
              class="grid-item-wrap virtual-item"
              draggable="true"
              @dragstart="handleDragStart($event, note)"
              @dragend="handleDragEnd"
            >
              <NoteCard
                :note="note"
                :batch-mode="batchMode"
                :selected="selectedIds?.includes(note.id)"
                @toggle-select="emit('toggle-select', note.id)"
                @preview="(n: Note) => emit('preview', n)"
                @edit="(n: Note) => emit('edit', n)"
                @delete="(n: Note) => emit('delete', n)"
                @refresh="emit('refresh')"
              />
            </div>
          </div>
        </div>
      </div>
      <div v-else ref="gridRef" :class="['grid-cards', { visible }]">
        <div
          v-for="(note, i) in props.notes"
          :key="note.id"
          :data-id="note.id"
          class="grid-item-wrap"
          :style="{ '--i': i }"
          draggable="true"
          @dragstart="handleDragStart($event, note)"
          @dragend="handleDragEnd"
        >
          <NoteCard
            :note="note"
            :batch-mode="batchMode"
            :selected="selectedIds?.includes(note.id)"
            @toggle-select="emit('toggle-select', note.id)"
            @preview="(n: Note) => emit('preview', n)"
            @edit="(n: Note) => emit('edit', n)"
            @delete="(n: Note) => emit('delete', n)"
            @refresh="emit('refresh')"
          />
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.note-grid { min-height: 100px; }

.virtual-scroll {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  padding-right: 4px;
}
.virtual-row {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
}
.virtual-item {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}

.grid-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

/* Stagger 渐入（全局 jnclub-cardIn，统一节奏） */
.grid-item-wrap {
  opacity: 0;
  transform: translateY(10px);
  animation: jnclub-cardIn 0.32s var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * 0.045s);
}
.grid-cards.visible .grid-item-wrap { animation-name: jnclub-cardIn; }

/* 拖拽排序时抑制 stagger 渐入动画重播 */
.grid-cards.sorting .grid-item-wrap {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}
</style>
