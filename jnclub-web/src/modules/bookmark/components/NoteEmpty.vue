<script setup lang="ts">
/**
 * NoteEmpty.vue — 便签空状态（氛围升级版）
 * 飘浮花瓣 + 渐变卡片底 + 品牌色环形图标 + CTA 按钮渐变
 */
defineProps<{
  message?: string
  hint?: string
}>()

const emit = defineEmits<{
  create: []
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
          <rect x="10" y="6" width="28" height="36" rx="3" stroke="var(--brand)" stroke-width="2" />
          <line x1="16" y1="16" x2="32" y2="16" stroke="var(--brand)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="16" y1="22" x2="28" y2="22" stroke="var(--pink-adzuki)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="16" y1="28" x2="24" y2="28" stroke="var(--pink-adzuki)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="16" y1="34" x2="20" y2="34" stroke="var(--pink-adzuki)" stroke-width="1.5" stroke-linecap="round" />
          <circle cx="34" cy="12" r="2" fill="var(--brand)" opacity="0.5" />
        </svg>
      </div>

      <h3 class="empty-title">{{ message || '还没有便签' }}</h3>
      <p class="empty-sub">{{ hint || '点击按钮创建你的第一篇便签，随时记录灵感。' }}</p>

      <button class="empty-cta jnclub-bouncy-slow" @click="emit('create')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        新建便签
      </button>
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
  margin: 0 0 24px;
}

.empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--gradient-btn);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 24px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  box-shadow: var(--shadow-1);
}
.empty-cta:hover {
  box-shadow: var(--shadow-card-hover);
  transform: scale(1.03);
}
.empty-cta:active {
  transform: scale(0.97);
}
</style>
