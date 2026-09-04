<script setup lang="ts">
/**
 * JPageShell.vue — 统一页面壳
 * 收敛独立页面（概览/待办/日历/回收站）重复的：
 * - 页面外距
 * - 毛玻璃内容面板
 * - 最大宽度与居中
 * - 移动端间距
 * 用法：
 *   <JPageShell>
 *     <template #header><JPageHeader ... /></template>
 *     <DashboardView />
 *   </JPageShell>
 */
withDefaults(defineProps<{
  /** 内容面板最大宽度；默认走 --layout-content-max */
  maxWidth?: string
  /** 内容面板内边距；默认走 --layout-panel-padding */
  panelPadding?: string
}>(), {
  maxWidth: 'var(--layout-content-max)',
  panelPadding: 'var(--layout-panel-padding)',
})
</script>

<template>
  <div class="j-page-shell">
    <slot name="header" />
    <div
      class="j-page-body"
      :style="{ maxWidth, padding: panelPadding }"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.j-page-shell {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 1;
  padding: var(--layout-page-padding, 12px 24px 0);
  min-height: 0;
}

.j-page-body {
  flex: 1;
  min-height: 0;
  width: 100%;
  margin: 0 auto;
  overflow-y: auto;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  box-shadow: var(--glass-shadow);
}

@media (max-width: 767px) {
  .j-page-shell {
    padding: 8px 8px 0;
  }
  .j-page-body {
    margin: 0;
    padding: 12px;
  }
}
</style>
