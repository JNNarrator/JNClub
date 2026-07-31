<script setup lang="ts">
/**
 * CollectionEmpty.vue — 收藏空状态（可爱粉版）
 * 飘浮花瓣 + 渐变卡片底 + 品牌色环形图标 + CTA
 */
defineProps<{
  message?: string
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
        <svg width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="6" width="32" height="36" rx="4" stroke="#fff" stroke-width="2" />
          <line x1="14" y1="16" x2="34" y2="16" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="14" y1="23" x2="28" y2="23" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="14" y1="30" x2="22" y2="30" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <circle cx="38" cy="10" r="3" fill="#fff" opacity="0.7" />
        </svg>
      </div>

      <h3 class="empty-title">{{ message || '这里空空如也～' }}</h3>
      <p class="empty-sub">把喜欢的链接收藏起来，构建你的专属灵感库 🌸</p>

      <button class="empty-cta jnclub-bouncy-slow" @click="emit('create')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        添加收藏
      </button>
    </div>
  </div>
</template>

<style scoped>
.empty-state-card {
  position: relative;
  overflow: hidden;
  background: radial-gradient(circle at 50% 30%, var(--brand) 0%, transparent 65%),
              linear-gradient(180deg, var(--pink-cherry) 0%, var(--bg-card) 40%);
  border: 1px solid var(--pink-rose);
  border-radius: var(--radius-lg);
  padding: 64px 20px 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: 0 4px 24px rgba(251, 129, 119, 0.12);
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
}

.empty-icon-ring {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--gradient-btn);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 24px rgba(251, 129, 119, 0.35), var(--shadow-2);
  margin-bottom: 24px;
  animation: jnclub-breathe 2.8s ease-in-out infinite;
}

.empty-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--brand);
  margin: 0 0 8px;
  letter-spacing: 0.5px;
}

.empty-sub {
  font-size: 13px;
  color: var(--text-3);
  max-width: 280px;
  line-height: 1.7;
  margin: 0 0 28px;
}

.empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--gradient-btn);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 11px 28px;
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(251, 129, 119, 0.35);
}
.empty-cta:hover {
  box-shadow: 0 6px 22px rgba(251, 129, 119, 0.45);
  transform: scale(1.04);
}
.empty-cta:active {
  transform: scale(0.97);
}
</style>
