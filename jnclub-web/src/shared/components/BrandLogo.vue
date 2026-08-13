<script setup lang="ts">
/**
 * BrandLogo.vue — 统一品牌标识（JNClub）
 * 图形复用 favicon.svg 的视觉语言：粉渐变圆角方块 + 白色 heart（lucide Heart）
 * 供 SideNav / Welcome / ExtensionPage 三处共用，消除图标不一致。
 */
import { NIcon } from 'naive-ui'
import { Heart } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  /** 图形方块边长（px） */
  size?: number
  /** 是否显示文字区（文字/副标题） */
  showText?: boolean
  /** 主文字 */
  text?: string
  /** 副标题（可选） */
  slogan?: string
  /** 折叠模式：仅显示图形，文字隐藏（侧栏折叠） */
  collapsed?: boolean
}>(), {
  size: 36,
  showText: true,
  text: 'JNClub',
  slogan: '',
  collapsed: false,
})

const iconSize = Math.round(props.size * 0.5)
</script>

<template>
  <span :class="['brand-logo', { collapsed }]">
    <span class="brand-mark" :style="{ width: `${size}px`, height: `${size}px` }">
      <NIcon :component="Heart" :size="iconSize" color="#fff" />
    </span>
    <template v-if="showText && !collapsed">
      <span class="brand-text">{{ text }}</span>
      <span v-if="slogan" class="brand-slogan">{{ slogan }}</span>
    </template>
  </span>
</template>

<style scoped>
.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  line-height: 1;
}
.brand-logo.collapsed {
  justify-content: center;
  gap: 0;
}

/* 粉渐变圆角方块（与 favicon.svg 一致：brand → 亮粉） */
.brand-mark {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 22%;
  background: linear-gradient(135deg, #ec5b8e, #ff8fab);
  box-shadow:
    0 6px 16px -4px rgba(236, 91, 142, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

.brand-text {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.5px;
  color: var(--text-1);
  line-height: 1.2;
}

.brand-slogan {
  font-size: 11px;
  color: var(--text-3);
  line-height: 1.2;
}
</style>
