<script setup lang="ts">
/**
 * JInlineEmpty —— 轻量行内空态/状态提示。
 *
 * 与 JEmptyState 的分工：
 * - JEmptyState：占据整块主内容区的空态，带玻璃卡片、品牌环形图标、花瓣与 CTA 按钮。
 * - JInlineEmpty：面板内、抽屉内、侧栏内的一行提示。这些位置塞不下也无需 80px 图标，
 *   过去是 7 个组件各写一份 padding/字号/颜色略有出入的 CSS，现统一到本组件。
 *
 * 用法：
 *   <JInlineEmpty message="暂无数据" />
 *   <JInlineEmpty bordered message="云盘还没有文件" />
 *   <JInlineEmpty tone="error" :message="error" />
 *   <JInlineEmpty pad="none" message="加载中…"><NSpin size="small" /></JInlineEmpty>
 */
withDefaults(
  defineProps<{
    /** 主提示文字；也可用默认插槽承载富文本（如带 <code> 的说明） */
    message?: string
    /** 次要说明，渲染为更小更淡的一行 */
    hint?: string
    /** 语义色调：muted 用于空态/加载，error 用于失败 */
    tone?: 'muted' | 'error'
    /** 是否套虚线框（用于图表、列表等留白区域，让「空」有边界） */
    bordered?: boolean
    /** 内边距档位：sm 为面板级，md 为主内容级，none 用于已在大容器内的情况 */
    pad?: 'none' | 'sm' | 'md'
    /** 横向排列（图标 + 文字同一行），用于「加载中…」这类单行状态 */
    inline?: boolean
  }>(),
  {
    tone: 'muted',
    pad: 'sm',
  },
)
</script>

<template>
  <div
    class="j-inline-empty"
    :class="[`is-${tone}`, `pad-${pad}`, { 'is-bordered': bordered, 'is-inline': inline }]"
  >
    <span v-if="$slots.icon" class="j-inline-empty-icon"><slot name="icon" /></span>
    <p v-if="message || $slots.default" class="j-inline-empty-msg">
      <slot>{{ message }}</slot>
    </p>
    <p v-if="hint" class="j-inline-empty-hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.j-inline-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  text-align: center;
}
.j-inline-empty.is-inline {
  flex-direction: row;
  gap: 10px;
}
.j-inline-empty.pad-sm { padding: var(--empty-pad-sm) 16px; }
.j-inline-empty.pad-md { padding: var(--empty-pad) 16px; }
.j-inline-empty.pad-none { padding: 0; }
.j-inline-empty.is-bordered {
  /* 内嵌 L2 块：中性虚线框 + --hover-bg 底（原为 --glass-border 白描边 + 粉 chip 底） */
  border: 1px dashed var(--surface-border);
  border-radius: var(--radius-sm);
  /* 虚线框常用于图表/列表的留白区，垫一层中性底色让「空」有体积感 */
  background: var(--hover-bg);
}
.j-inline-empty-msg {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--text-3);
}
.j-inline-empty.is-error .j-inline-empty-msg {
  color: var(--danger);
}
.j-inline-empty-hint {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--text-4);
}
.j-inline-empty-icon {
  display: inline-flex;
  align-items: center;
}
</style>
