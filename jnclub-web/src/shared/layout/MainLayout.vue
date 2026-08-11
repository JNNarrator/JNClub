<script setup lang="ts">
/**
 * MainLayout.vue — 应用主布局
 * 嵌入 SideNav（三段式）+ 内容区
 * 移动端（<768px）：侧栏自动折叠为窄条，顶栏隐藏 FAB 标签文字由 Home 处理
 */
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { NLayout, NLayoutContent } from 'naive-ui'
import SideNav from './SideNav.vue'
import MobileTabBar from './MobileTabBar.vue'

const props = defineProps<{
  isDark: boolean
  /** 当前模块（由 AppWrapper 从 URL/偏好同步，用于侧边栏高亮） */
  activeModule: 'bookmarks' | 'notes' | 'files' | 'vault'
}>()

const emit = defineEmits<{
  'toggle-theme': []
  'module-change': [module: 'bookmarks' | 'notes' | 'files' | 'vault']
}>()

const handleModuleChange = (module: 'bookmarks' | 'notes' | 'files' | 'vault') => {
  emit('module-change', module)
}

// ========== 移动端响应式：侧栏自动折叠 ==========
const collapsed = ref(false)
const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) collapsed.value = true
  else collapsed.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onBeforeUnmount(() => window.removeEventListener('resize', checkMobile))

watch(isMobile, (m) => {
  // 移动端强制折叠；切回桌面展开
  collapsed.value = m
})
</script>

<template>
  <NLayout has-sider class="app-layout" :class="{ 'is-mobile': isMobile }">
    <!-- 桌面端（≥768px）：三段式侧栏 -->
    <SideNav
      v-if="!isMobile"
      :is-dark="isDark"
      :active-module="props.activeModule"
      :collapsed="collapsed"
      @toggle-theme="emit('toggle-theme')"
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
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--bg-page);
}

/* 移动端：底部 TabBar 让位（Home 内滚动，防止内容被 TabBar 遮挡） */
.app-layout.is-mobile .app-content {
  padding-bottom: calc(64px + env(safe-area-inset-bottom));
}
</style>
