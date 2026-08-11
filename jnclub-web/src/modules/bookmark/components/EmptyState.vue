<script setup lang="ts">
/**
 * EmptyState.vue — 统一空状态（玻璃卡片版）
 * 飘浮花瓣 + 玻璃渐变卡底 + 品牌色环形图标 + CTA
 * 通过 props 区分收藏 / 便签等场景
 */
defineProps<{
  message?: string
  hint?: string
  /** 图标类型：bookmark / note / file / vault */
  icon?: 'bookmark' | 'note' | 'file' | 'vault'
  ctaLabel?: string
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
        <svg v-if="icon === 'bookmark'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="6" width="32" height="36" rx="4" stroke="#fff" stroke-width="2" />
          <line x1="14" y1="16" x2="34" y2="16" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="14" y1="23" x2="28" y2="23" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="14" y1="30" x2="22" y2="30" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <circle cx="38" cy="10" r="3" fill="#fff" opacity="0.7" />
        </svg>
        <svg v-else-if="icon === 'note'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="10" y="6" width="28" height="36" rx="3" stroke="#fff" stroke-width="2" />
          <line x1="16" y1="16" x2="32" y2="16" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="16" y1="22" x2="28" y2="22" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="16" y1="28" x2="24" y2="28" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <line x1="16" y1="34" x2="20" y2="34" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".3" />
          <circle cx="34" cy="12" r="2.5" fill="#fff" opacity="0.7" />
        </svg>
        <svg v-else-if="icon === 'file'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M10 8h17l9 9v22a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z" stroke="#fff" stroke-width="2" />
          <path d="M26 8v10h10" stroke="#fff" stroke-width="2" stroke-linejoin="round" />
          <rect x="18" y="23" width="12" height="8" rx="1.5" stroke="#fff" stroke-width="1.5" opacity=".7" />
          <circle cx="38" cy="8" r="3" fill="#fff" opacity="0.7" />
        </svg>
        <svg v-else-if="icon === 'vault'" width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M24 6l14 6v10c0 8-5.5 14-14 17-8.5-3-14-9-14-17V12l14-6z" stroke="#fff" stroke-width="2" stroke-linejoin="round" />
          <circle cx="24" cy="21" r="4.5" stroke="#fff" stroke-width="1.8" />
          <path d="M24 25.5V31" stroke="#fff" stroke-width="1.8" stroke-linecap="round" />
          <circle cx="39" cy="8" r="3" fill="#fff" opacity="0.7" />
        </svg>
        <svg v-else width="44" height="44" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="6" width="32" height="36" rx="4" stroke="#fff" stroke-width="2" />
          <line x1="14" y1="16" x2="34" y2="16" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
          <line x1="14" y1="23" x2="28" y2="23" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".6" />
          <line x1="14" y1="30" x2="22" y2="30" stroke="#fff" stroke-width="1.5" stroke-linecap="round" opacity=".4" />
          <circle cx="38" cy="10" r="3" fill="#fff" opacity="0.7" />
        </svg>
      </div>

      <h3 class="empty-title">{{ message || '这里空空如也～' }}</h3>
      <p class="empty-sub">{{ hint || '里面还没有内容，去添加一些吧 ✨' }}</p>

      <button class="empty-cta glass-pill-btn jnclub-bouncy-slow" @click="emit('create')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {{ ctaLabel || '添加' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.empty-state-card {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 30%, var(--glass-glow-top), transparent 65%),
    linear-gradient(180deg, var(--glass-chip-bg) 0%, var(--glass-bg-trans) 40%);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 64px 20px 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: var(--glass-shadow);
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
  border: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 24px var(--focus-ring), var(--shadow-2);
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
  font-size: var(--fs-md);
  color: var(--text-3);
  max-width: 280px;
  line-height: 1.7;
  margin: 0 0 28px;
}

.empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-base);
  padding: 11px 28px;
  cursor: pointer;
}
</style>