<script setup lang="ts">
/**
 * CollectionGrid.vue — 卡片网格视图
 * grid-template-columns: repeat(auto-fill, minmax(220px,1fr)) + gap
 * 单卡不孤悬，stagger 渐入
 */
import { ref, onMounted } from 'vue'
import { NSpin } from 'naive-ui'
import CollectionCard from './CollectionCard.vue'
import type { BookmarkItem } from './CollectionRow.vue'
import { useDraggableSort } from '../composables/useDraggableSort'

defineProps<{
  bookmarks: BookmarkItem[]
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
  sort: [orderedIds: number[]]
}>()

const visible = ref(false)
const gridRef = ref<HTMLElement | null>(null)
onMounted(() => {
  requestAnimationFrame(() => {
    visible.value = true
  })
})

const { init: initSort } = useDraggableSort(gridRef, (ids) => {
  emit('sort', ids.map(Number))
})
onMounted(() => { initSort() })
</script>

<template>
  <div class="collection-grid">
    <NSpin :show="loading">
      <div ref="gridRef" :class="['grid-cards', { visible }]">
        <div
          v-for="(bk, i) in bookmarks"
          :key="bk.id"
          :data-id="bk.id"
          class="grid-item-wrap"
          :style="{ '--i': i }"
        >
          <CollectionCard :bookmark="bk" @refresh="emit('refresh')" @edit="emit('edit', $event)" />
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.collection-grid {
  min-height: 100px;
}

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

.grid-cards.visible .grid-item-wrap {
  animation-name: fadeSlideIn;
}

/* 拖拽排序时抑制 stagger 渐入动画重播 */
.grid-cards.sorting .grid-item-wrap {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}

@keyframes fadeSlideIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
