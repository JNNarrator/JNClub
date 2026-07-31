<script setup lang="ts">
/**
 * CollectionEmpty.vue — 收藏空状态（氛围升级版）
 * 飘浮花瓣 + 渐变卡片底 + 品牌色环形图标
 */
defineProps<{
  message?: string
}>()
</script>

<template>
  <div class="empty-state-card">
    <!-- 飘浮花瓣 -->
    <span class="petal petal-1"></span>
    <span class="petal petal-2"></span>
    <span class="petal petal-3"></span>
    <span class="petal petal-4"></span>

    <div class="empty-content">
      <!-- 品牌色环形图标 -->
      <div class="empty-icon-ring">
        <svg width="42" height="42" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="6" width="32" height="36" rx="4" stroke="var(--brand)" stroke-width="2" />
          <line x1="14" y1="16" x2="34" y2="16" stroke="var(--brand)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="14" y1="23" x2="28" y2="23" stroke="var(--pink-adzuki)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="14" y1="30" x2="22" y2="30" stroke="var(--pink-adzuki)" stroke-width="1.5" stroke-linecap="round" />
          <circle cx="38" cy="10" r="2.5" fill="var(--brand)" opacity="0.5" />
        </svg>
      </div>

      <h3 class="empty-title">{{ message || '还没有收藏' }}</h3>
      <p class="empty-sub">把你喜欢的链接保存到这里，随时随地快速访问，构建专属灵感库。</p>
    </div>
  </div>
</template>

<style scoped>
.empty-state-card {
  position: relative;
  overflow: hidden;
  background: radial-gradient(circle at 50% 34%, var(--pink-cherry) 0%, transparent 70%), var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 60px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: var(--shadow-1);
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
}

.empty-icon-ring {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--pink-cherry);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--glow-icon);
  margin-bottom: 20px;
}

.empty-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--brand);
  margin: 0 0 6px;
}

.empty-sub {
  font-size: 13px;
  color: var(--text-3);
  max-width: 260px;
  line-height: 1.6;
  margin: 0;
}
</style>
