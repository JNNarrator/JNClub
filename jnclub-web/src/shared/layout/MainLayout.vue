<script setup lang="ts">
/**
 * MainLayout.vue — 应用主布局
 * 嵌入 SideNav（三段式）+ 内容区
 * 移动端（<768px）：侧栏自动折叠为窄条，顶栏隐藏 FAB 标签文字由 Home 处理
 */
import { onMounted, ref, watch } from 'vue'
import { NLayout, NLayoutContent } from 'naive-ui'
import SideNav from './SideNav.vue'
import MobileTabBar from './MobileTabBar.vue'
import { useBreakpoint } from '../composables/useBreakpoint'
import { useUiDensity } from '../composables/useUiDensity'
import { useUserPreferences } from '../composables/useUserPreferences'
import type { ModuleKey } from '../types/nav'

const props = defineProps<{
  isDark: boolean
  /** 当前模块（由 AppWrapper 从 URL/偏好同步，用于侧边栏高亮） */
  activeModule: ModuleKey
}>()

const emit = defineEmits<{
  'toggle-theme': []
  'module-change': [module: ModuleKey]
}>()

const handleModuleChange = (module: ModuleKey) => {
  emit('module-change', module)
}

// ========== 移动端响应式：侧栏自动折叠（统一断点） ==========
const collapsed = ref(false)
const { isMobile } = useBreakpoint()

// 移动端强制折叠；切回桌面展开
watch(isMobile, (m) => {
  collapsed.value = m
}, { immediate: true })

/**
 * 密度类在此内聚绑定，而不是由各页壳各自传入。
 * `app-density-compact` 覆盖的是 --layout-page-gutter / --header-height /
 * --layout-panel-padding 等**布局令牌**，而 MainLayout 正是这三个消费点
 * （AppWrapper、OverviewLayout、RecycleLayout）共用的唯一布局根；
 * 由它统一挂类，三处才能拿到同一份紧凑值。
 * 此前只有 AppWrapper 传了这个类，导致概览/回收站在紧凑模式下仍是
 * 60px 页头 + 20px 24px 面板内边距（实测），与主壳 52px/14px 16px 不一致。
 */
const { density } = useUiDensity()

/**
 * 偏好加载也在此内聚：useUiDensity 只在 prefs.ready 变 true 时才把后端权威值
 * 同步进 density，而 load() 原先只有 AppWrapper 调 —— 概览/回收站不经过
 * AppWrapper，ready 永远是 false，这两页的紧凑模式因此完全不生效
 * （实测 compact 下仍是 60px 页头 + 20px 24px 面板内边距）。
 * load() 已做成幂等，路由切换导致 MainLayout 重挂不会重复请求。
 */
const prefs = useUserPreferences()
onMounted(() => { prefs.load() })
</script>

<template>
  <NLayout
    has-sider
    class="app-layout"
    :class="{ 'is-mobile': isMobile, 'app-density-compact': density === 'compact' }"
  >
    <!-- 桌面端（≥768px）：三段式侧栏 -->
    <SideNav
      v-if="!isMobile"
      :active-module="props.activeModule"
      :collapsed="collapsed"
      @module-change="handleModuleChange"
      @update:collapsed="(v: boolean) => collapsed = v"
    />

    <NLayout>
      <NLayoutContent class="app-content">
        <slot />
      </NLayoutContent>
    </NLayout>

    <!-- 移动端（<768px）：底部 Tab 导航 -->
    <MobileTabBar
      v-if="isMobile"
      :active-module="props.activeModule"
      @module-change="handleModuleChange"
    />
  </NLayout>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

.app-content {
  height: 100vh;
  overflow: auto;
  /* 玻璃升级版：L0 页面底铺三层极光渐变 + 品牌光晕 + 实色底。
     粉紫氛围光从边缘柔和渗入，中间保持干净可读。 */
  background:
    radial-gradient(1200px 600px at 15% -5%, var(--aurora-top), transparent 55%),
    radial-gradient(1000px 500px at 105% 105%, var(--aurora-bottom), transparent 50%),
    radial-gradient(800px 400px at 50% 50%, var(--aurora-center), transparent 60%),
    radial-gradient(1200px 500px at 10% -10%, var(--surface-glow), transparent 60%),
    var(--bg-page);
}

/* 移动端：底部 TabBar 让位（Home 内滚动，防止内容被 TabBar 遮挡） */
.app-layout.is-mobile .app-content {
  padding-bottom: calc(var(--tabbar-height, 56px) + env(safe-area-inset-bottom));
}
</style>
