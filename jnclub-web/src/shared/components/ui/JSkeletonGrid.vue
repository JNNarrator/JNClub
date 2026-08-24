<script setup lang="ts">
/**
 * JSkeletonGrid.vue — 卡片网格骨架屏
 * 用于收藏/便签/概览等卡片类页面的首次加载，替代整块 NSpin。
 */
withDefaults(defineProps<{
  count?: number
}>(), {
  count: 6,
})
</script>

<template>
  <div class="j-skeleton-grid" aria-hidden="true">
    <div v-for="i in count" :key="i" class="j-skeleton-card">
      <div class="j-skeleton-line j-skeleton-head" />
      <div class="j-skeleton-line j-skeleton-title" />
      <div class="j-skeleton-line j-skeleton-text" />
      <div class="j-skeleton-line j-skeleton-text short" />
    </div>
  </div>
</template>

<style scoped>
.j-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.j-skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--glass-bg-trans);
}
.j-skeleton-line {
  height: 12px;
  border-radius: 6px;
  background: var(--hover-bg);
  animation: j-skeleton-pulse 1.4s ease-in-out infinite;
}
.j-skeleton-head {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
}
.j-skeleton-title {
  width: 70%;
  height: 16px;
}
.j-skeleton-text {
  width: 100%;
}
.j-skeleton-text.short {
  width: 55%;
}
@keyframes j-skeleton-pulse {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}
</style>
