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
import { useDraggableSort } from '../../../shared/composables/useDraggableSort'
import { useItemDragContext } from '../composables/useItemDragContext'
import { useBreakpoint } from '../../../shared/composables/useBreakpoint'

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
const { isCoarse } = useBreakpoint()

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
/**
 * 便签卡是定高卡片 —— 实测两种密度下均为 185.58px（NoteCard 内部行数固定，
 * 不像收藏卡那样随标签换行而变）。行高 = 卡片 185.58 + `.virtual-row`
 * 的 margin-bottom 16 = 201.58，取 202 留 0.4px 余量。
 * 原先写死 220 虚高 9%：100 条便签在 1440px 下排 34 行，
 * wrapper 占位 7480px 而真实内容约 6854px，滚到底会多出约 600px 空白。
 */
const virtual = useVirtualList(rows, { itemHeight: 202, overscan: 2 })
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
        <!-- wrapperProps 是 ComputedRef，必须显式取 .value（原因见 CollectionList.vue） -->
        <div v-bind="virtual.wrapperProps.value">
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
              :draggable="!isCoarse"
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
          :draggable="!isCoarse"
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
  /* 高度不再自己按视口猜（原 calc(100vh - 240px) 与实际 chrome 失配，会同时激活
     外层滚动条形成双滚动条），改为填满父级 flex 容器给的高度。
     父级链路见 Home.vue 的 .spin-area 注释；本组件的根元素也必须是 flex 容器
     （Home.vue 已用 :deep() 设好），否则 flex:1 不生效、列表会撑开而失去虚拟滚动。 */
  flex: 1 1 auto;
  min-height: 0;
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
  animation: jnclub-cardIn var(--dur-slow) var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * var(--dur-stagger));
}
.grid-cards.visible .grid-item-wrap { animation-name: jnclub-cardIn; }

/* 拖拽排序时抑制 stagger 渐入动画重播 */
.grid-cards.sorting .grid-item-wrap {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}
</style>
