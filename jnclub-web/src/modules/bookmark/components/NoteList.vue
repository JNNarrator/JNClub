<script setup lang="ts">
/**
 * NoteList.vue — 便签极简列表视图
 * 包装 NoteRow 列表，支持拖拽排序
 */
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVirtualList } from '@vueuse/core'
import { NSpin } from 'naive-ui'
import NoteRow from './NoteRow.vue'
import type { Note } from '../stores/note'
import { useDraggableSort } from '../composables/useDraggableSort'

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

const listRef = ref<HTMLElement | null>(null)
const isVirtual = computed(() => props.notes.length > 80)
const virtual = useVirtualList(computed(() => props.notes), { itemHeight: 64, overscan: 8 })
const virtualItems = computed(() => Array.isArray(virtual.list.value) ? virtual.list.value : [])
const dragDisabled = computed(() => isVirtual.value)
const { init: initSort, destroy: destroySort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
}, dragDisabled)
onMounted(() => {
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
  <div class="note-list">
    <NSpin :show="loading">
      <div v-if="isVirtual" v-bind="virtual.containerProps" class="virtual-scroll">
        <div v-bind="virtual.wrapperProps">
          <NoteRow
            v-for="item in virtualItems"
            :key="item.data.id"
            :data-id="item.data.id"
            :note="item.data"
            :batch-mode="batchMode"
            :selected="selectedIds?.includes(item.data.id)"
            class="virtual-item"
            :style="{ '--i': item.index % 20 }"
            @toggle-select="emit('toggle-select', item.data.id)"
            @preview="(n: Note) => emit('preview', n)"
            @edit="(n: Note) => emit('edit', n)"
            @delete="(n: Note) => emit('delete', n)"
            @refresh="emit('refresh')"
          />
        </div>
      </div>
      <div v-else ref="listRef" class="list-inner">
        <NoteRow
          v-for="note in props.notes"
          :key="note.id"
          :data-id="note.id"
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
    </NSpin>
  </div>
</template>

<style scoped>
.note-list { min-height: 100px; }
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
.list-inner {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>
