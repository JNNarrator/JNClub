<script setup lang="ts">
/**
 * NoteList.vue — 便签极简列表视图
 * 包装 NoteRow 列表，支持拖拽排序
 */
import { ref, onMounted } from 'vue'
import { NSpin } from 'naive-ui'
import NoteRow from './NoteRow.vue'
import type { Note } from '../stores/note'
import { useDraggableSort } from '../composables/useDraggableSort'

defineProps<{
  notes: Note[]
  loading?: boolean
}>()

const emit = defineEmits<{
  preview: [note: Note]
  edit: [note: Note]
  delete: [note: Note]
  refresh: []
  sort: [orderedIds: number[]]
}>()

const listRef = ref<HTMLElement | null>(null)
const { init: initSort } = useDraggableSort(listRef, (ids) => {
  emit('sort', ids.map(Number))
})
onMounted(() => { initSort() })
</script>

<template>
  <div class="note-list">
    <NSpin :show="loading">
      <div ref="listRef" class="list-inner">
        <NoteRow
          v-for="note in notes"
          :key="note.id"
          :data-id="note.id"
          :note="note"
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
.list-inner {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
/* SortableJS 拖拽视觉 */
.list-inner :deep(.sortable-ghost) {
  opacity: 0.4;
  background: var(--brand-soft) !important;
  border-radius: var(--radius-sm);
  outline: 2px dashed var(--brand);
  outline-offset: -2px;
}
.list-inner :deep(.sortable-chosen) {
  cursor: grabbing;
}
</style>
