<script setup lang="ts">
/**
 * CollectionEmpty.vue — 空态组件
 * 居中"书架"图示 + 友好文案 + 品牌粉小元素
 * 不用大片白撑满，轻量居中
 */

defineProps<{
  message?: string
}>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-shelf">
      <div class="shelf-icon">
        <svg width="80" height="64" viewBox="0 0 80 64" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="8" width="64" height="4" rx="2" fill="var(--brand-soft)" />
          <rect x="8" y="30" width="64" height="4" rx="2" fill="var(--brand-soft)" />
          <rect x="8" y="52" width="64" height="4" rx="2" fill="var(--brand-soft)" />
          <rect x="14" y="14" width="10" height="14" rx="1" fill="var(--text-4)" />
          <rect x="26" y="16" width="8" height="12" rx="1" fill="var(--brand)" opacity="0.5" />
          <rect x="36" y="12" width="12" height="16" rx="1" fill="var(--text-4)" />
          <rect x="50" y="14" width="10" height="14" rx="1" fill="var(--text-4)" />
          <rect x="18" y="36" width="12" height="14" rx="1" fill="var(--text-4)" />
          <rect x="32" y="38" width="8" height="12" rx="1" fill="var(--brand)" opacity="0.4" />
          <rect x="42" y="36" width="16" height="14" rx="1" fill="var(--text-4)" />
          <circle cx="62" cy="6" r="3" fill="var(--brand)" opacity="0.3" />
        </svg>
      </div>
    </div>
    <p class="empty-text">{{ message || '这个目录还没有收藏' }}</p>
    <p class="empty-hint">点击上方按钮添加第一个收藏</p>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.empty-shelf {
  margin-bottom: 20px;
  opacity: 0.6;
  transition: opacity var(--dur) var(--ease);
}

.empty-shelf:hover {
  opacity: 1;
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-2);
  margin-bottom: 6px;
}

.empty-hint {
  font-size: 13px;
  color: var(--text-3);
}
</style>
