<script setup lang="ts">
/**
 * 内联 SVG 图标组件，替代 element-plus 的 <el-icon> + 图标组件。
 *
 * 为什么不用图标库：项目此前同时装了 element-plus 与 vant 两套 UI 库，
 * 而 element-plus 实际只用到 ElIcon 容器和 28 个图标 —— 为了这几个图标
 * 引入 element-plus/dist/index.css（526KB，99% 未使用）。改为内联 path 后
 * 样式体积降到 0，图标本身也只有几 KB。
 *
 * 用法对齐原 el-icon：
 *   <JIcon name="back" :size="16" />
 *   <JIcon :name="modeMeta.icon" :size="20" class="spin" />
 * size 省略时跟随父级 font-size（与原 el-icon 的 font-size: inherit 一致）。
 */
import { computed } from 'vue'
import { ICONS } from '../icons'

const props = defineProps<{
  /** icons.ts 中的图标名 */
  name: string
  /** 图标边长（px），省略则继承父级字号 */
  size?: number | string
}>()

const paths = computed(() => ICONS[props.name] ?? [])

const style = computed(() => {
  if (props.size === undefined) return undefined
  const v = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { fontSize: v }
})
</script>

<template>
  <i class="j-icon" :style="style" aria-hidden="true">
    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
      <path v-for="(d, i) in paths" :key="i" :d="d" fill="currentColor" />
    </svg>
  </i>
</template>

<style scoped>
/* 与原 .el-icon 的几何保持一致：1em 方块、inline-flex 居中、跟随 currentColor */
.j-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  line-height: 1em;
  font-size: inherit;
  color: inherit;
  fill: currentColor;
  vertical-align: middle;
  flex-shrink: 0;
}
.j-icon svg {
  width: 1em;
  height: 1em;
}
</style>
