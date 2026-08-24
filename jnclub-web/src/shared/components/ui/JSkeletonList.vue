<script setup lang="ts">
/**
 * JSkeletonList.vue — 列表骨架屏
 * 用于收藏列表/便签列表/云盘/待办等行式页面的首次加载。
 */
withDefaults(defineProps<{
  count?: number
}>(), {
  count: 5,
})
</script>

<template>
  <div class="j-skeleton-list" aria-hidden="true">
    <div v-for="i in count" :key="i" class="j-skeleton-row">
      <div class="j-skeleton-icon" />
      <div class="j-skeleton-row-main">
        <div class="j-skeleton-line j-skeleton-title" />
        <div class="j-skeleton-line j-skeleton-text" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.j-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.j-skeleton-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--glass-bg-trans);
}
.j-skeleton-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--hover-bg);
  animation: j-skeleton-pulse 1.4s ease-in-out infinite;
}
.j-skeleton-row-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.j-skeleton-line {
  height: 12px;
  border-radius: 6px;
  background: var(--hover-bg);
  animation: j-skeleton-pulse 1.4s ease-in-out infinite;
}
.j-skeleton-title {
  width: 45%;
  height: 14px;
}
.j-skeleton-text {
  width: 80%;
}
@keyframes j-skeleton-pulse {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}
</style>
