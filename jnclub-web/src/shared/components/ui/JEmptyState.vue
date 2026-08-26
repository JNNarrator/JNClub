<script setup lang="ts">
/**
 * JEmptyState.vue — 全局统一空状态
 * 包装原 EmptyState（玻璃卡片 + 飘浮花瓣 + 品牌色图标 + CTA），
 * 作为 shared/ui 出口供各页面复用。
 * variant 区分 空列表 / 搜索无结果 / 错误重试 三场景。
 */
import EmptyState from '../../../modules/bookmark/components/EmptyState.vue'

withDefaults(defineProps<{
  message?: string
  hint?: string
  icon?: 'bookmark' | 'note' | 'file' | 'vault' | 'search' | 'error'
  ctaLabel?: string
  retryLabel?: string
  showCta?: boolean
  showRetry?: boolean
  /** 场景：empty 列表为空 / search 搜索无结果 / error 错误重试 */
  variant?: 'empty' | 'search' | 'error'
}>(), {
  message: '',
  hint: '',
  icon: undefined,
  ctaLabel: '',
  retryLabel: '',
  showCta: true,
  showRetry: true,
  variant: 'empty',
})

const emit = defineEmits<{
  create: []
  retry: []
}>()
</script>

<template>
  <EmptyState
    :message="message"
    :hint="hint"
    :icon="icon"
    :cta-label="ctaLabel"
    :retry-label="retryLabel"
    :show-cta="showCta"
    :show-retry="showRetry"
    :variant="variant"
    @create="emit('create')"
    @retry="emit('retry')"
  />
</template>