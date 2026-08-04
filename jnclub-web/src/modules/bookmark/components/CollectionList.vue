<script setup lang="ts">
/**
 * CollectionList.vue — 极简列表视图
 * 包裹多行 CollectionRow，stagger 渐入
 */
import { ref, onMounted } from 'vue'
import { NSpin } from 'naive-ui'
import CollectionRow from './CollectionRow.vue'
import type { BookmarkItem } from './CollectionRow.vue'

defineProps<{
  bookmarks: BookmarkItem[]
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  edit: [bookmark: BookmarkItem]
}>()

/* Stagger 渐入：每项延迟递增 */
const visible = ref(false)
onMounted(() => {
  requestAnimationFrame(() => {
    visible.value = true
  })
})
</script>

<template>
  <div class="collection-list">
    <NSpin :show="loading">
      <div :class="['list-items', { visible }]">
        <div
          v-for="(bk, i) in bookmarks"
          :key="bk.id"
          class="list-item-wrap"
          :style="{ '--i': i }"
        >
          <CollectionRow :bookmark="bk" @refresh="emit('refresh')" @edit="emit('edit', $event)" />
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

/* Stagger 渐入 */
.list-item-wrap {
  opacity: 0;
  transform: translateY(8px);
  animation: fadeSlideIn 0.3s var(--ease) forwards;
  animation-delay: calc(var(--i, 0) * 0.04s);
}

.list-items.visible .list-item-wrap {
  animation-name: fadeSlideIn;
}

@keyframes fadeSlideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
