<script setup lang="ts">
/**
 * NoteEmpty.vue — 便签空态
 * flex column 居中，线性大图标(品牌浅底圆) + 主标题 + 副文案 + 品牌粉主按钮
 * 与收藏夹 CollectionEmpty 同范式
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
  <div class="note-empty">
    <!-- 空态居中区域 -->
    <div class="empty-visual">
      <!-- 品牌浅底圆大图标 -->
      <div class="empty-icon-circle">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <!-- 文档/便签线性图标 -->
          <rect x="10" y="6" width="28" height="36" rx="3" stroke="var(--brand)" stroke-width="2" />
          <line x1="16" y1="16" x2="32" y2="16" stroke="var(--brand)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="16" y1="22" x2="28" y2="22" stroke="var(--text-4)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="16" y1="28" x2="24" y2="28" stroke="var(--text-4)" stroke-width="1.5" stroke-linecap="round" />
          <line x1="16" y1="34" x2="20" y2="34" stroke="var(--text-4)" stroke-width="1.5" stroke-linecap="round" />
          <!-- 装饰小圆 — 像素级小彩蛋 -->
          <circle cx="34" cy="12" r="2" fill="var(--brand)" opacity="0.5" />
        </svg>
      </div>
    </div>

    <p class="empty-title">{{ message || '这个目录还没有便签' }}</p>
    <p class="empty-hint">{{ hint || '点击右下角 + 创建你的第一篇便签' }}</p>

    <button class="empty-cta" @click="emit('create')">
      新建便签
    </button>
  </div>
</template>

<style scoped>
.note-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
  min-height: 400px;
  /* 空态居中占满主区 */
}

/* 品牌浅底圆 */
.empty-icon-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--brand-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  transition: transform var(--dur) var(--ease);
}
.empty-icon-circle:hover { transform: scale(1.05); }

/* 文案 */
.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-2);
  margin: 0 0 8px;
}
.empty-hint {
  font-size: 13px;
  color: var(--text-3);
  margin: 0 0 24px;
}

/* 品牌粉主按钮 */
.empty-cta {
  border: none;
  background: var(--brand);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 24px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--dur) var(--ease);
}
.empty-cta:hover {
  background: var(--brand-hover);
  box-shadow: 0 4px 12px rgba(236, 91, 142, 0.3);
  transform: scale(1.03);
}
.empty-cta:active {
  background: var(--brand-press);
  transform: scale(0.97);
}
</style>
