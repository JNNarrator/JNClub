<script setup lang="ts">
/**
 * JEmptyState.vue — 全局统一空状态
 * 包装原 EmptyState（玻璃卡片 + 飘浮花瓣 + 品牌色图标 + CTA），
 * 作为 shared/ui 出口供各页面复用。
 */
import EmptyState from '../../../modules/bookmark/components/EmptyState.vue'

withDefaults(defineProps<{
  message?: string
  hint?: string
  icon?: 'bookmark' | 'note' | 'file' | 'vault'
  ctaLabel?: string
  showCta?: boolean
}>(), {
  message: '',
  hint: '',
  icon: undefined,
  ctaLabel: '',
  showCta: true,
})

const emit = defineEmits<{
  create: []
}>()
</script>

<template>
  <EmptyState
    :message="message"
    :hint="hint"
    :icon="icon"
    :cta-label="ctaLabel"
    :show-cta="showCta"
    @create="emit('create')"
  />
</template>
