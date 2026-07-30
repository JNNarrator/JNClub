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

defineProps<{
  bookmarks: BookmarkItem[]
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref(false)
onMounted(() => {
  requestAnimationFrame(() => {
    visible.value = true
  })
})
</script>

<template>
  <div class="collection-grid">
    <NSpin :show="loading">
      <div :class="['grid-cards', { visible }]">
        <div
          v-for="(bk, i) in bookmarks"
          :key="bk.id"
          class="grid-item-wrap"
          :style="{ '--i': i }"
        >
          <CollectionCard :bookmark="bk" @refresh="emit('refresh')" />
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
