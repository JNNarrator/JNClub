/**
 * vue-bits 动画适配层统一出口
 *
 * 使用规范（两项目一致，前缀 J + 语义名）：
 * - 业务侧只 import 这里的 J* 组件，不直接 import vendor/ 下的 vue-bits 源码
 * - 主题色/节奏由 tokens.ts 统一提供，如需新组件按 vendor/JXxx.vue 模式补充
 */
export { default as JCountUp } from './JCountUp.vue'
export { default as JMagnet } from './JMagnet.vue'
export { default as JShinyText } from './JShinyText.vue'
export { default as JSplitText } from './JSplitText.vue'
export { default as JAnimatedContent } from './JAnimatedContent.vue'

export { motion, themeColors } from './tokens'
