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
  border-radius: var(--radius-xs);
  /* M2-4：接入 --skeleton-base / --skeleton-highlight 做扫光，替代原先的整块 opacity 脉冲 */
  background: linear-gradient(
    90deg,
    var(--skeleton-base) 0%,
    var(--skeleton-highlight) 50%,
    var(--skeleton-base) 100%
  );
  background-size: 200% 100%;
  animation: j-skeleton-shimmer var(--dur-loop) var(--ease-linear) infinite;
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
@keyframes j-skeleton-shimmer {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

/* 减少动态效果：停在静态底色，不扫光 */
@media (prefers-reduced-motion: reduce) {
  .j-skeleton-line,
  .j-skeleton-head {
    background: var(--skeleton-base);
    animation: none;
  }
}
</style>
