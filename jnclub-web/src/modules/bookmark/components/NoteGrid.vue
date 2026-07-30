<script setup lang="ts">
/**
 * NoteGrid.vue — 便签卡片网格
 * grid-template-columns: repeat(auto-fill, minmax(220px,1fr)) + gap
 * stagger 渐入
 */
import { ref, onMounted } from 'vue'
import { NSpin } from 'naive-ui'
import NoteCard from './NoteCard.vue'
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

const visible = ref(false)
onMounted(() => {
  requestAnimationFrame(() => { visible.value = true })
})
</script>

<template>
  <div class="note-grid">
    <NSpin :show="loading">
      <div :class="['grid-cards', { visible }]">
        <div
          v-for="(note, i) in notes"
          :key="note.id"
          class="grid-item-wrap"
          :style="{ '--i': i }"
        >
          <NoteCard
            :note="note"
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

.grid-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

/* Stagger 渐入 */
.grid-item-wrap {
  opacity: 0;
  transform: translateY(12px);
  animation: fadeSlideIn 0.35s var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * 0.05s);
}
.grid-cards.visible .grid-item-wrap { animation-name: fadeSlideIn; }

@keyframes fadeSlideIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
