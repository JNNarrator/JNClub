<script setup lang="ts">
/**
 * NoteList.vue — 便签极简列表视图
 * 包装 NoteRow 列表
 */
import { NSpin } from 'naive-ui'
import NoteRow from './NoteRow.vue'
import type { Note } from '../stores/note'

defineProps<{
  notes: Note[]
  loading?: boolean
}>()

const emit = defineEmits<{
  preview: [note: Note]
  edit: [note: Note]
  delete: [note: Note]
  refresh: []
}>()
</script>

<template>
  <div class="note-list">
    <NSpin :show="loading">
      <div class="list-inner">
        <NoteRow
          v-for="note in notes"
          :key="note.id"
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
</style>
