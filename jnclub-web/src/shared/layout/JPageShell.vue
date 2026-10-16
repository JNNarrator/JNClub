<script setup lang="ts">
/**
 * JPageShell.vue — 统一页面壳
 * 收敛独立页面（概览/回收站等）重复的：
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
      :style="{ maxWidth, '--page-panel-padding': panelPadding }"
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
  /* 只留水平 gutter：上边距交给 .j-page-body 的 margin-top，
     页头才能用等量负 margin 抵消这里的 padding 让分隔线通栏（P0-1）。 */
  padding: var(--layout-page-padding, 0 24px 0);
  min-height: 0;
}

.j-page-body {
  flex: 1;
  min-height: 0;
  width: 100%;
  /* 与主壳 Home.vue 的 .content-area padding-top 对齐，均为 20px（P0-1） */
  margin: 20px auto 0;
  overflow-y: auto;
  /* panelPadding 由模板以 --page-panel-padding 变量注入，而非直接写 inline padding：
     inline style 优先级高于媒体查询，直接写 padding 会让下面 767px 的移动端覆盖静默失效
     （实测 390px 下内容可用宽仅 324px）。走变量后值仍来自组件 API
     （--layout-panel-padding 与紧凑模式的 14px 16px 都能透传），是否覆盖交还给 CSS 层叠。 */
  padding: var(--page-panel-padding, var(--layout-panel-padding));
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
    /* --shell-gutter 是与页头的契约变量：JPageHeader 用它做等量负 margin */
    --shell-gutter: 8px;
    padding: 0 var(--shell-gutter) 0;
  }
  .j-page-body {
    margin: 12px 0 0;
    /* 与主壳 Home.vue 的 .content-area 移动端 padding:12px 对齐。
       此处能生效依赖上面把 panelPadding 改成了变量：原先 inline padding 会压过本规则，
       390px 下实际仍是 20px 24px，内容可用宽只剩 324px。 */
    padding: 12px;
  }
}
</style>
